package com.aemcloudproject.core.services.impl;

import com.aemcloudproject.core.config.ScbOfferListingModel;
import com.aemcloudproject.core.services.ScbContentFragmentReportService;
import com.aemcloudproject.core.testcontext.AppAemContext;
import com.aemcloudproject.core.testcontext.ScbOfferListingTestContent;
import com.day.cq.dam.api.Asset;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFDataValidation;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static com.aemcloudproject.core.testcontext.ScbOfferListingTestContent.ROOT;
import static com.aemcloudproject.core.testcontext.ScbOfferListingTestContent.scbFragment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link ScbContentFragmentReportServiceImpl}.
 */
@ExtendWith(AemContextExtension.class)
class ScbContentFragmentReportServiceImplTest {

    private static final String REPORTS = "/content/dam/test/reports/offer-listing";
    private static final String RUNS = "/content/dam/test/reports/upload-runs";
    private static final int MAX_ROWS = 1234;

    private final AemContext context = AppAemContext.newAemContext();
    private final DataFormatter formatter = new DataFormatter();

    private ScbContentFragmentReportService reportService;

    @BeforeEach
    void setUp() throws Exception {
        ScbOfferListingTestContent.scbSetUp(context);
        Map<String, Object> config = new HashMap<>();
        config.put("fragmentReportFolder", REPORTS + "/");
        config.put("runResultsFolder", RUNS);
        config.put("fragmentReportFileName", "test-report");
        config.put("runResultsFileName", "test-run");
        config.put("maxFileSizeMb", 7);
        config.put("maxRows", MAX_ROWS);
        reportService = context.registerInjectActivateService(new ScbContentFragmentReportServiceImpl(), config);
    }

    @Test
    void sampleHasTheGuideAndOneSheetPerModelInOrder() throws Exception {
        try (XSSFWorkbook workbook = scbSample()) {
            List<String> names = new ArrayList<>();
            workbook.forEach(sheet -> names.add(sheet.getSheetName()));
            assertIterableEquals(Arrays.asList("_Guide", "merchant-venue", "merchant-details", "category",
                    "offer-card", "offer-cta", "offer-detail"), names);
            assertEquals(0, workbook.getActiveSheetIndex());
        }
    }

    @Test
    void sampleHeadersShowRequiredFieldsInRed() throws Exception {
        try (XSSFWorkbook workbook = scbSample()) {
            Sheet sheet = workbook.getSheet("offer-detail");
            assertIterableEquals(Arrays.asList("action", "offerSlug", "offerTitle", "offerID", "offerEndDate",
                    "featured", "priority", "score", "offerCards", "offerCta", "offerImage", "alternateText"),
                    scbRowText(sheet.getRow(0)));
            assertFalse(scbIsRed(workbook, sheet.getRow(0).getCell(0)));
            assertTrue(scbIsRed(workbook, sheet.getRow(0).getCell(1)));
            assertTrue(scbIsRed(workbook, sheet.getRow(0).getCell(2)));
            assertTrue(scbIsRed(workbook, sheet.getRow(0).getCell(3)));
            assertFalse(scbIsRed(workbook, sheet.getRow(0).getCell(4)));
            assertTrue(scbIsRed(workbook, sheet.getRow(0).getCell(10)));
            assertTrue(scbIsRed(workbook, sheet.getRow(0).getCell(11)));
        }
    }

    @Test
    void sampleRowHasFixedExampleValuesGreyAndNotInTheDropdown() throws Exception {
        scbFragment(context, ScbOfferListingModel.OFFER_DETAIL, "o-high", "offerID", "100009", "offerTitle", "High");
        scbFragment(context, ScbOfferListingModel.MERCHANT_VENUE, "v-first", "name", "First");

        try (XSSFWorkbook workbook = scbSample()) {
            Sheet offers = workbook.getSheet("offer-detail");
            assertEquals(1, offers.getLastRowNum());
            Row sample = offers.getRow(1);
            assertIterableEquals(Arrays.asList("SAMPLE", "weekend-dinner-deal", "Weekend dinner deal: 25% off",
                    "100001", "2026-12-31T23:59:59.000+05:30", "", "", "",
                    ROOT + "/cards/platinum\n" + ROOT + "/cards/gold", ROOT + "/offer-cta/apply-now",
                    "/content/dam/aemcloudproject/offers/dinner-deal.jpg", "Couple dining at Spice Garden"),
                    scbRowText(sample));
            Font font = workbook.getFontAt(sample.getCell(0).getCellStyle().getFontIndex());
            assertTrue(font.getItalic());
            assertEquals(IndexedColors.GREY_50_PERCENT.getIndex(), font.getColor());
            assertEquals(2, scbDropdownFirstRow(workbook.getSheet("offer-detail")));

            assertEquals("spice-garden-orchard", workbook.getSheet("merchant-venue").getRow(1).getCell(1)
                    .getStringCellValue());
            assertEquals(1, workbook.getSheet("category").getLastRowNum());
        }
    }

    @Test
    void reportListsEveryFragmentWithoutActionsSortedByEndDateThenId() throws Exception {
        scbFragment(context, ScbOfferListingModel.OFFER_DETAIL, "o-dec", "offerID", "100003", "offerTitle", "Dec",
                "offerEndDate", scbDate(2026, Calendar.DECEMBER, 1));
        scbFragment(context, ScbOfferListingModel.OFFER_DETAIL, "o-jan", "offerID", "100001", "offerTitle", "Jan",
                "offerEndDate", scbDate(2027, Calendar.JANUARY, 1));
        scbFragment(context, ScbOfferListingModel.OFFER_DETAIL, "o-none-2", "offerID", "100002", "offerTitle", "N2");
        scbFragment(context, ScbOfferListingModel.OFFER_DETAIL, "o-none-0", "offerID", "100000", "offerTitle", "N0");
        scbFragment(context, ScbOfferListingModel.CATEGORY, "travel", "ID", "1002", "name", "Travel");
        scbFragment(context, ScbOfferListingModel.CATEGORY, "dining", "ID", "1001", "name", "Dining");

        try (XSSFWorkbook workbook = scbReport(reportService.scbSaveReport(context.resourceResolver(), ROOT))) {
            Sheet offers = workbook.getSheet("offer-detail");
            assertIterableEquals(Arrays.asList("o-jan", "o-dec", "o-none-0", "o-none-2"), scbColumn(offers, 1));
            assertNull(offers.getRow(1).getCell(0));
            assertEquals("2027-01-01T00:00:00.000+05:30", offers.getRow(1).getCell(4).getStringCellValue());
            assertEquals(1, scbDropdownFirstRow((XSSFSheet) offers));
            assertIterableEquals(Arrays.asList("dining", "travel"), scbColumn(workbook.getSheet("category"), 1));
        }
    }

    @Test
    void reportIgnoresFragmentsOfOtherModels() throws Exception {
        String other = scbFragment(context, ScbOfferListingModel.OFFER_CTA, "odd", "label", "Odd");
        context.resourceResolver().getResource(other + "/jcr:content/data")
                .adaptTo(ModifiableValueMap.class)
                .put("cq:model", "/conf/other/models/odd");
        context.resourceResolver().commit();

        try (XSSFWorkbook workbook = scbReport(reportService.scbSaveReport(context.resourceResolver(), ROOT))) {
            assertEquals(0, workbook.getSheet("offer-cta").getLastRowNum());
        }
    }

    @Test
    void guideShowsTheLimitsAndEveryModelsSlugColumn() throws Exception {
        try (XSSFWorkbook workbook = scbSample()) {
            Sheet guide = workbook.getSheet("_Guide");
            assertEquals("SCB TGL Content Fragment Bulk Upload – how to fill this workbook", guide.getRow(0).getCell(0).getStringCellValue());
            assertTrue(scbColumn(guide, 0).contains("- Files up to 7 MB, and up to 1,234 rows with an action per upload."));
            int header = scbRowIndex(guide, "Sheet");
            assertIterableEquals(Arrays.asList("Sheet", "Folder (under offer-listing)", "Slug column", "Title column",
                    "ID column", "Required fields"), scbRowText(guide.getRow(header)));
            assertIterableEquals(Arrays.asList("merchant-venue", "merchants/venues", "merchantVenueSlug", "name", "–",
                    "merchantVenueSlug"), scbRowText(guide.getRow(header + 1)));
            assertIterableEquals(Arrays.asList("offer-detail", "offers", "offerSlug", "offerTitle", "offerID",
                    "offerSlug, offerID, offerTitle, offerImage, alternateText"), scbRowText(guide.getRow(header + 6)));
        }
    }

    @Test
    void saveReportCreatesTheReportFolderAndADatedAsset() throws Exception {
        String path = reportService.scbSaveReport(context.resourceResolver(), ROOT);

        assertTrue(path.matches(REPORTS + "/test-report-\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}\\.xlsx"), path);
        assertEquals("sling:OrderedFolder", context.resourceResolver().getResource(REPORTS).getValueMap()
                .get("jcr:primaryType"));
        assertNotNull(context.resourceResolver().getResource(path).adaptTo(Asset.class));
    }

    @Test
    void saveRunReportWritesTheRunDetailsAndOneRowPerResult() throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        results.add(scbResult("offer-detail", 2, "o1", "CREATE", "PASS", "/content/dam/x/offers/o1"));
        results.add(scbResult("offer-cta", null, null, null, "SKIP", "Sheet: no row has an action"));

        String path = reportService.scbSaveRunReport(context.resourceResolver(),
                "/content/dam/imports/book.xlsx", ROOT, results);

        assertTrue(path.matches(RUNS + "/test-run-.*\\.xlsx"), path);
        try (XSSFWorkbook workbook = scbReport(path)) {
            XSSFSheet sheet = workbook.getSheet("Results");
            assertIterableEquals(Arrays.asList("Workbook", "/content/dam/imports/book.xlsx"), scbRowText(sheet.getRow(0)));
            assertIterableEquals(Arrays.asList("Folder", ROOT), scbRowText(sheet.getRow(1)));
            assertEquals("Run by", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("Run at", sheet.getRow(3).getCell(0).getStringCellValue());
            assertIterableEquals(Arrays.asList("Sheet", "Row", "Slug", "Action", "Result", "Details"),
                    scbRowText(sheet.getRow(5)));
            assertEquals(CellType.NUMERIC, sheet.getRow(6).getCell(1).getCellType());
            assertIterableEquals(Arrays.asList("offer-detail", "2", "o1", "CREATE", "PASS", "/content/dam/x/offers/o1"),
                    scbRowText(sheet.getRow(6)));
            assertNull(sheet.getRow(7).getCell(1));
            assertEquals("SKIP", sheet.getRow(7).getCell(4).getStringCellValue());
            assertNotNull(sheet.getCTWorksheet().getAutoFilter());
        }
    }

    private XSSFWorkbook scbSample() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        reportService.scbWriteSample(context.resourceResolver(), ROOT, out);
        return new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()));
    }

    private XSSFWorkbook scbReport(String path) throws Exception {
        try (InputStream in = context.resourceResolver().getResource(path).adaptTo(Asset.class)
                .getOriginal().getStream()) {
            return new XSSFWorkbook(in);
        }
    }

    private List<String> scbRowText(Row row) {
        List<String> values = new ArrayList<>();
        row.forEach(cell -> values.add(formatter.formatCellValue(cell)));
        return values;
    }

    private List<String> scbColumn(Sheet sheet, int column) {
        List<String> values = new ArrayList<>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            Cell cell = row == null ? null : row.getCell(column);
            if (cell != null) {
                values.add(formatter.formatCellValue(cell));
            }
        }
        return values;
    }

    private int scbRowIndex(Sheet sheet, String firstCell) {
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row != null && row.getCell(0) != null && firstCell.equals(formatter.formatCellValue(row.getCell(0)))) {
                return r;
            }
        }
        return -1;
    }

    private boolean scbIsRed(XSSFWorkbook workbook, Cell cell) {
        return workbook.getFontAt(cell.getCellStyle().getFontIndex()).getColor() == IndexedColors.RED.getIndex();
    }

    private int scbDropdownFirstRow(XSSFSheet sheet) {
        List<XSSFDataValidation> validations = sheet.getDataValidations();
        assertEquals(1, validations.size());
        CellRangeAddress range = validations.get(0).getRegions().getCellRangeAddress(0);
        assertEquals(0, range.getFirstColumn());
        assertEquals(0, range.getLastColumn());
        assertEquals(range.getFirstRow() + MAX_ROWS - 1, range.getLastRow());
        assertIterableEquals(Arrays.asList("CREATE", "UPDATE", "DELETE"),
                Arrays.asList(validations.get(0).getValidationConstraint().getExplicitListValues()));
        return range.getFirstRow();
    }

    private static Calendar scbDate(int year, int month, int day) {
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("GMT+05:30"));
        calendar.clear();
        calendar.set(year, month, day);
        return calendar;
    }

    private static Map<String, Object> scbResult(String sheet, Integer row, String slug, String action,
                                                 String result, String details) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("sheet", sheet);
        entry.put("row", row);
        entry.put("slug", slug);
        entry.put("action", action);
        entry.put("result", result);
        entry.put("details", details);
        return entry;
    }
}
