package com.aemcloudproject.core.bulkimport;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbookParserTest {

    private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

    private ParsedWorkbook parsed;

    @BeforeEach
    void setUp() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy hh:mm"));

            Sheet offers = workbook.createSheet("OfferDetail");
            row(offers, 0, "offerID", "offerSlug", "offerTitle", "hotPromo", "offerStartDate", "offerEndDate",
                    "offerCategories (fragment reference)\nBasePath - /content/dam/sc/categories",
                    "offerCards", "offerCta", "notAProperty");
            Row first = row(offers, 1, null, "mount-faber", "Mount Faber", null, "01/04/26 0:00", null,
                    null, "mastercard", "find-out-more");
            first.createCell(0).setCellValue(1067987d);
            first.createCell(3).setCellValue(0d);
            Cell excelDate = first.createCell(5);
            excelDate.setCellValue(LocalDateTime.of(2027, 3, 31, 23, 59));
            excelDate.setCellStyle(dateStyle);
            Row second = row(offers, 2, "811806", "duty-free-shopping", "Double cashback", "1",
                    "2023-11-01T00:00:00", null, "travel", "visa, mastercard", "register-now\nfind-out-more");
            second.createCell(0).setCellValue(811806d);
            row(offers, 3, "5", "bad-date", "Bad date", "maybe", "31/02/26 0:00", null, "[]");

            Sheet categories = workbook.createSheet("OfferCategory");
            row(categories, 0, "Name", "offerCategoryslug", "categoryIconPath",
                    "parent(content fragment)\n/content/dam/sc/categories");
            row(categories, 1, "Health & Fitness", "health-fitness-new", "restrelax");
            row(categories, 2, "Travel", "travel");

            Sheet merchants = workbook.createSheet("MerchantDetails");
            row(merchants, 0, "merchantName", "merchantSlug", "merchantLogo",
                    "merchantVenues (fragment reference)\nbasePath -> /content/dam/sc/venues/");
            row(merchants, 1, "Shilla Duty Free", "shilla-duty-free", null, "shilla-duty-free");
            row(merchants, 2, null, null, null, "shilla-duty-free-macau");
            row(merchants, 3, "ignored name", null, null, "shilla-duty-free-hongkong");
            row(merchants, 4, "Shinsegae Duty Free", "shinsegae-duty-free", null, "shinsegae-duty-free");

            Sheet venues = workbook.createSheet("merchantVenues");
            row(venues, 0, "name", "merchantvenueSlug", "address", "latitude", "longitude", "telephone");
            Row venue = row(venues, 1, "Shilla Duty Free", "shilla-duty-free", "542 Balestier Rd", null, null, "6255 2873");
            venue.createCell(3).setCellValue(1.3266038d);
            venue.createCell(4).setCellValue(103.8432065d);
            Row numericPhone = row(venues, 2, "Shinsegae", "shinsegae-duty-free");
            numericPhone.createCell(5).setCellValue(913123121d);

            workbook.createSheet("Notes");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            parsed = new WorkbookParser(SINGAPORE).parse(new ByteArrayInputStream(out.toByteArray()));
        }
    }

    @Test
    void readsNumericIdsBooleansAndPlainText() {
        ParsedRow offer = parsed.getRows(ModelSpec.OFFER_DETAIL).get(0);
        assertEquals(1067987L, offer.getId());
        assertEquals("mount-faber", offer.getSlug());
        assertEquals(Boolean.FALSE, offer.getValues().get("hotPromo"));
        assertEquals(Boolean.TRUE, parsed.getRows(ModelSpec.OFFER_DETAIL).get(1).getValues().get("hotPromo"));
        assertEquals(2, offer.getRowNumber());
    }

    @Test
    void readsTextDatesExcelDatesAndIsoDatesInTheConfiguredZone() {
        ParsedRow offer = parsed.getRows(ModelSpec.OFFER_DETAIL).get(0);
        assertEquals(ZonedDateTime.of(2026, 4, 1, 0, 0, 0, 0, SINGAPORE).toInstant(),
                ((Calendar) offer.getValues().get("offerStartDate")).toInstant());
        assertEquals(ZonedDateTime.of(2027, 3, 31, 23, 59, 0, 0, SINGAPORE).toInstant(),
                ((Calendar) offer.getValues().get("offerEndDate")).toInstant());
        assertEquals(ZonedDateTime.of(2023, 11, 1, 0, 0, 0, 0, SINGAPORE).toInstant(),
                ((Calendar) parsed.getRows(ModelSpec.OFFER_DETAIL).get(1).getValues().get("offerStartDate")).toInstant());
    }

    @Test
    void stripsHeaderNotesAndMatchesPropertiesIgnoringCase() {
        ParsedRow offer = parsed.getRows(ModelSpec.OFFER_DETAIL).get(1);
        assertEquals(Collections.singletonList("travel"), offer.getValues().get("offerCategories"));
        assertEquals("health-fitness-new", parsed.getRows(ModelSpec.CATEGORY).get(0).getSlug());
        assertEquals("restrelax", parsed.getRows(ModelSpec.CATEGORY).get(0).getValues().get("categoryIconPath"));
    }

    @Test
    void splitsReferenceCellsOnCommasAndLineBreaks() {
        ParsedRow offer = parsed.getRows(ModelSpec.OFFER_DETAIL).get(1);
        assertEquals(Arrays.asList("visa", "mastercard"), offer.getValues().get("offerCards"));
        assertEquals(Arrays.asList("register-now", "find-out-more"), offer.getValues().get("offerCta"));
    }

    @Test
    void foldsContinuationRowsIntoTheRowAbove() {
        List<ParsedRow> merchants = parsed.getRows(ModelSpec.MERCHANT_DETAILS);
        assertEquals(2, merchants.size());
        assertEquals(Arrays.asList("shilla-duty-free", "shilla-duty-free-macau", "shilla-duty-free-hongkong"),
                merchants.get(0).getValues().get("merchantVenues"));
        assertEquals(1, merchants.get(0).getWarnings().size(), "single-value cell in a continuation row is reported");
        assertEquals("shinsegae-duty-free", merchants.get(1).getSlug());
    }

    @Test
    void keepsDecimalsAndWritesLongNumbersOutInFull() {
        ParsedRow venue = parsed.getRows(ModelSpec.MERCHANT_VENUE).get(0);
        assertEquals(1.3266038d, venue.getValues().get("latitude"));
        assertEquals("6255 2873", venue.getValues().get("telephone"));
        assertEquals("913123121", parsed.getRows(ModelSpec.MERCHANT_VENUE).get(1).getValues().get("telephone"));
    }

    @Test
    void recordsBadCellsAgainstTheRowInsteadOfFailing() {
        ParsedRow bad = parsed.getRows(ModelSpec.OFFER_DETAIL).get(2);
        assertEquals(2, bad.getErrors().size());
        assertTrue(bad.getErrors().stream().anyMatch(e -> e.startsWith("'hotPromo'")));
        assertTrue(bad.getErrors().stream().anyMatch(e -> e.contains("31/02/26")), "31 February is rejected, not rolled over");
        assertEquals(Collections.emptyList(), bad.getValues().get("offerCategories"), "[] means clear");
    }

    @Test
    void reportsUnknownSheetsAndColumnsAsWarnings() {
        assertTrue(parsed.getIssues().stream().anyMatch(i -> i.getSheet().equals("Notes") && i.getOutcome() == Outcome.WARNING));
        assertTrue(parsed.getIssues().stream().anyMatch(i -> i.getMessage().contains("'notAProperty'")));
        assertFalse(parsed.hasSheet(ModelSpec.OFFER_CARD));
    }

    @Test
    void parsesSupportedDateFormatsStrictly() {
        assertEquals(ZonedDateTime.of(2026, 9, 30, 23, 55, 0, 0, SINGAPORE).toInstant(),
                WorkbookParser.parseDate("30/09/2026 23:55", SINGAPORE).toInstant());
        assertEquals(ZonedDateTime.of(2026, 9, 30, 0, 0, 0, 0, SINGAPORE).toInstant(),
                WorkbookParser.parseDate("2026-09-30", SINGAPORE).toInstant());
        assertEquals(ZonedDateTime.of(2026, 9, 30, 10, 0, 0, 0, ZoneId.of("Z")).toInstant(),
                WorkbookParser.parseDate("2026-09-30T10:00:00Z", SINGAPORE).toInstant());
        assertThrows(IllegalArgumentException.class, () -> WorkbookParser.parseDate("09/30/26 10:00", SINGAPORE));
    }

    @Test
    void headerNameDropsEverythingAfterABracketOrLineBreak() {
        assertEquals("parent", WorkbookParser.headerName("parent(Fragment reference)"));
        assertEquals("offerCta", WorkbookParser.headerName("offerCta\nBasePath -> /content/dam"));
        assertNull(parsed.getRows(ModelSpec.OFFER_DETAIL).get(0).getValues().get("offerCategories"));
    }

    private static Row row(Sheet sheet, int index, String... values) {
        Row row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                row.createCell(i).setCellValue(values[i]);
            }
        }
        return row;
    }
}
