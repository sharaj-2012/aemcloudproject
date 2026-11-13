package com.aemcloudproject.core.services.impl;

import com.aemcloudproject.core.dto.ScbSheetData;
import com.aemcloudproject.core.services.ScbWorkbookParserService;
import com.aemcloudproject.core.testcontext.AppAemContext;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link ScbWorkbookParserServiceImpl}.
 */
@ExtendWith(AemContextExtension.class)
class ScbWorkbookParserServiceImplTest {

    private final AemContext context = AppAemContext.newAemContext();

    private ScbWorkbookParserService parser;
    private final List<String> errors = new ArrayList<>();

    @BeforeEach
    void setUp() {
        parser = context.registerInjectActivateService(new ScbWorkbookParserServiceImpl());
    }

    @Test
    void readsHeadersRowsAndRowNumbersAndSkipsGuideSheets() throws Exception {
        Map<String, ScbSheetData> sheets = parser.scbReadWorkbook(scbWorkbook(workbook -> {
            scbRows(workbook.createSheet("_Guide"), new String[] {"anything"});
            scbRows(workbook.createSheet("offer-detail"),
                    new String[] {"action", "offerSlug", "offerTitle"},
                    new String[] {"create", "summer-sale", "Summer sale"},
                    null,
                    new String[] {"", "", ""},
                    new String[] {"Update", "winter-sale", "Winter"});
        }), errors);

        assertTrue(errors.isEmpty(), errors.toString());
        assertIterableEquals(Arrays.asList("offer-detail"), sheets.keySet());
        ScbSheetData data = sheets.get("offer-detail");
        assertIterableEquals(Arrays.asList("action", "offerSlug", "offerTitle"), data.getHeaders());
        assertIterableEquals(Arrays.asList(2, 5), data.getRowNumbers());
        assertEquals("CREATE", data.getRows().get(0).get("action"));
        assertEquals("summer-sale", data.getRows().get(0).get("offerSlug"));
        assertEquals("Summer sale", data.getRows().get(0).get("offerTitle"));
        assertEquals("UPDATE", data.getRows().get(1).get("action"));
    }

    @Test
    void skipsSampleRowsInAnyCase() throws Exception {
        Map<String, ScbSheetData> sheets = parser.scbReadWorkbook(scbWorkbook(workbook ->
                scbRows(workbook.createSheet("category"),
                        new String[] {"action", "offerCategorySlug"},
                        new String[] {"SAMPLE", "dining"},
                        new String[] {"sample", "travel"},
                        new String[] {"DELETE", "shopping"})), errors);

        assertTrue(errors.isEmpty(), errors.toString());
        ScbSheetData data = sheets.get("category");
        assertEquals(1, data.getRows().size());
        assertEquals("shopping", data.getRows().get(0).get("offerCategorySlug"));
        assertIterableEquals(Arrays.asList(4), data.getRowNumbers());
    }

    @Test
    void keepsRowsWithoutAnActionSoTheWriterCanSkipThem() throws Exception {
        Map<String, ScbSheetData> sheets = parser.scbReadWorkbook(scbWorkbook(workbook ->
                scbRows(workbook.createSheet("offer-cta"),
                        new String[] {"action", "offerCtaSlug", "label"},
                        new String[] {"", "apply", "Apply now"})), errors);

        assertTrue(errors.isEmpty());
        Map<String, Object> row = sheets.get("offer-cta").getRows().get(0);
        assertFalse(row.containsKey("action"));
        assertEquals("Apply now", row.get("label"));
    }

    @Test
    void splitsMultiLinePathsIntoAListAndTrimsThem() throws Exception {
        Map<String, ScbSheetData> sheets = parser.scbReadWorkbook(scbWorkbook(workbook ->
                scbRows(workbook.createSheet("offer-detail"),
                        new String[] {"action", "offerSlug", "offerCards", "offerCta", "offerTitle"},
                        new String[] {"CREATE", "o1", "/content/dam/a\n\n  /content/dam/b  ", "/content/dam/c\n",
                            "Line one\nLine two"})), errors);

        Map<String, Object> row = sheets.get("offer-detail").getRows().get(0);
        assertEquals(Arrays.asList("/content/dam/a", "/content/dam/b"), row.get("offerCards"));
        assertEquals("/content/dam/c", row.get("offerCta"));
        assertEquals("Line one\nLine two", row.get("offerTitle"));
    }

    @Test
    void readsNumericCellsAsFormattedText() throws Exception {
        Map<String, ScbSheetData> sheets = parser.scbReadWorkbook(scbWorkbook(workbook -> {
            Sheet sheet = workbook.createSheet("offer-card");
            scbRows(sheet, new String[] {"action", "offerCardSlug", "ID"});
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("UPDATE");
            row.createCell(1).setCellValue("gold");
            row.createCell(2).setCellValue(1005d);
        }), errors);

        assertEquals("1005", sheets.get("offer-card").getRows().get(0).get("ID"));
    }

    @Test
    void reportsInvalidActionsHeadersWithSpacesAndMissingActionColumns() throws Exception {
        parser.scbReadWorkbook(scbWorkbook(workbook -> {
            scbRows(workbook.createSheet("offer-card"),
                    new String[] {"action", "offerCardSlug", "card name"},
                    new String[] {"MOVE", "gold", "x"});
            scbRows(workbook.createSheet("category"),
                    new String[] {"offerCategorySlug", "name"},
                    new String[] {"dining", "Dining"});
            workbook.createSheet("offer-cta");
        }), errors);

        assertIterableEquals(Arrays.asList(
                "Sheet \"offer-card\", column C: header \"card name\" must be a single word.",
                "Sheet \"offer-card\", row 2: action \"MOVE\" must be CREATE, UPDATE or DELETE.",
                "Sheet \"category\": has no action column."), errors);
    }

    @Test
    void returnsAnEmptySheetForASheetWithoutHeaders() throws Exception {
        Map<String, ScbSheetData> sheets = parser.scbReadWorkbook(scbWorkbook(workbook ->
                workbook.createSheet("offer-cta")), errors);

        assertTrue(errors.isEmpty());
        assertTrue(sheets.get("offer-cta").getHeaders().isEmpty());
        assertTrue(sheets.get("offer-cta").getRows().isEmpty());
    }

    @Test
    void rejectsAFileThatIsNotAWorkbook() {
        InputStream notExcel = new ByteArrayInputStream("not a workbook".getBytes());
        assertThrows(IOException.class, () -> parser.scbReadWorkbook(notExcel, errors));
    }

    private interface ScbWorkbookBuilder {
        void build(XSSFWorkbook workbook);
    }

    private static InputStream scbWorkbook(ScbWorkbookBuilder builder) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            builder.build(workbook);
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private static void scbRows(Sheet sheet, String[]... rows) {
        for (int r = 0; r < rows.length; r++) {
            if (rows[r] == null) {
                continue;
            }
            Row row = sheet.createRow(r);
            for (int c = 0; c < rows[r].length; c++) {
                row.createCell(c).setCellValue(rows[r][c]);
            }
        }
    }
}
