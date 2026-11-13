package com.aemcloudproject.core.servlets;

import com.aemcloudproject.core.services.ScbContentFragmentReportService;
import com.aemcloudproject.core.services.ScbContentFragmentWriterService;
import com.aemcloudproject.core.services.impl.ScbContentFragmentReportServiceImpl;
import com.aemcloudproject.core.services.impl.ScbContentFragmentWriterServiceImpl;
import com.aemcloudproject.core.services.impl.ScbWorkbookParserServiceImpl;
import com.aemcloudproject.core.testcontext.AppAemContext;
import com.aemcloudproject.core.testcontext.ScbOfferListingTestContent;
import com.day.cq.dam.api.Asset;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.aemcloudproject.core.testcontext.ScbOfferListingTestContent.ROOT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link ScbCfBulkUploadServlet} with the real parser, writer and report services.
 */
@ExtendWith(AemContextExtension.class)
class ScbCfBulkUploadServletTest {

    private static final String IMPORTS = "/content/dam/test/imports";
    private static final String BOOK = IMPORTS + "/book.xlsx";

    private final AemContext context = AppAemContext.newAemContext();

    @BeforeEach
    void setUp() throws Exception {
        ScbOfferListingTestContent.scbSetUp(context);
        context.registerInjectActivateService(new ScbWorkbookParserServiceImpl());
        context.registerService(ScbContentFragmentWriterService.class, new ScbContentFragmentWriterServiceImpl());
    }

    @Test
    void asksForAWorkbookFirst() throws Exception {
        Map<String, Object> body = scbPost(scbServlet(10, 5000), " ", ROOT, null, null);

        assertEquals(400, context.response().getStatus());
        assertEquals("error", body.get("status"));
        assertEquals("Select a workbook first.", body.get("message"));
    }

    @Test
    void rejectsAFolderThatIsNotAnOfferListingFolder() throws Exception {
        Map<String, Object> body = scbPost(scbServlet(10, 5000), BOOK, ROOT + "/offers", null, null);

        assertEquals(400, context.response().getStatus());
        assertEquals("Pick an offer-listing folder, e.g. /content/dam/aemcloudproject/cfs/offer-listing.",
                body.get("message"));
    }

    @Test
    void rejectsMissingNonAssetAndNonXlsxFiles() throws Exception {
        ScbCfBulkUploadServlet servlet = scbServlet(10, 5000);
        context.create().asset(IMPORTS + "/book.csv", new ByteArrayInputStream(new byte[] {1}), "text/csv");

        assertEquals("No file found at " + BOOK + ".", scbPost(servlet, BOOK, ROOT, null, null).get("message"));
        scbReset();
        assertEquals(ROOT + " is not a DAM asset.", scbPost(servlet, ROOT, ROOT, null, null).get("message"));
        scbReset();
        assertEquals(IMPORTS + "/book.csv is not an .xlsx file.",
                scbPost(servlet, IMPORTS + "/book.csv", ROOT, null, null).get("message"));
    }

    @Test
    void rejectsAnAssetWithoutAnOriginalYet() throws Exception {
        context.create().resource(BOOK, "jcr:primaryType", "dam:Asset");
        context.create().resource(BOOK + "/jcr:content", "jcr:primaryType", "dam:AssetContent");
        context.create().resource(BOOK + "/jcr:content/metadata", "jcr:primaryType", "nt:unstructured");

        Map<String, Object> body = scbPost(scbServlet(10, 5000), BOOK, ROOT, null, null);

        assertEquals(BOOK + " has no original file yet. Wait for DAM processing to finish.", body.get("message"));
    }

    @Test
    void rejectsAFileThatIsNotAValidWorkbook() throws Exception {
        context.create().asset(BOOK, new ByteArrayInputStream("plain text".getBytes()), "text/plain");

        assertEquals("The file is not a valid .xlsx workbook.",
                scbPost(scbServlet(10, 5000), BOOK, ROOT, null, null).get("message"));
    }

    @Test
    void rejectsAWorkbookOverTheSizeLimit() throws Exception {
        scbUpload(new String[] {"action", "offerCtaSlug", "label"}, new String[] {"CREATE", "apply", "Apply"});

        assertTrue(((String) scbPost(scbServlet(0, 5000), BOOK, ROOT, null, null).get("message"))
                .matches("The workbook is 0\\.0 MB; the limit is 0 MB\\."));
    }

    @Test
    void rejectsMoreActionRowsThanTheLimit() throws Exception {
        scbUpload(new String[] {"action", "offerCtaSlug", "label"},
                new String[] {"CREATE", "apply", "Apply"}, new String[] {"CREATE", "learn", "Learn"},
                new String[] {"", "note", "Not counted"});

        assertEquals("The workbook has 2 rows to process; the limit is 1. Split it into smaller files.",
                scbPost(scbServlet(10, 1), BOOK, ROOT, null, null).get("message"));
    }

    @Test
    void returnsTheWorkbookProblemsAsAList() throws Exception {
        scbUpload(new String[] {"action", "offerCtaSlug"}, new String[] {"MOVE", "apply"});

        Map<String, Object> body = scbPost(scbServlet(10, 5000), BOOK, ROOT, null, null);

        assertEquals(400, context.response().getStatus());
        assertEquals(Collections.singletonList(
                "Sheet \"offer-cta\", row 2: action \"MOVE\" must be CREATE, UPDATE or DELETE."), body.get("errors"));
    }

    @Test
    void dryRunReturnsTheResultsAndFileVersionAndWritesNothing() throws Exception {
        scbUpload(new String[] {"action", "offerCtaSlug", "label"}, new String[] {"CREATE", "apply", "Apply"});

        Map<String, Object> body = scbPost(scbServlet(10, 5000), BOOK, ROOT + "/", null, null);

        assertEquals(200, context.response().getStatus());
        assertEquals("ok", body.get("status"));
        assertEquals(Boolean.TRUE, body.get("dryRun"));
        assertEquals(BOOK, body.get("path"));
        assertEquals(ROOT, body.get("rootPath"));
        assertEquals(scbFileVersion(), body.get("fileVersion"));
        assertNull(body.get("reportPath"));
        assertEquals("PASS", scbResults(body).get(0).get("result"));
        assertNull(context.resourceResolver().getResource(ROOT + "/offer-cta/apply"));
    }

    @Test
    void dryRunWithFailuresStillAnswers200WithStatusError() throws Exception {
        scbUpload(new String[] {"action", "offerCtaSlug", "label"}, new String[] {"UPDATE", "missing", "M"});

        Map<String, Object> body = scbPost(scbServlet(10, 5000), BOOK, ROOT, null, null);

        assertEquals(200, context.response().getStatus());
        assertEquals("error", body.get("status"));
        assertEquals("FAIL", scbResults(body).get(0).get("result"));
    }

    @Test
    void finalRunIsRefusedWhenTheWorkbookChangedAfterTheDryRun() throws Exception {
        scbUpload(new String[] {"action", "offerCtaSlug", "label"}, new String[] {"CREATE", "apply", "Apply"});

        Map<String, Object> body = scbPost(scbServlet(10, 5000), BOOK, ROOT, "false", "older-version");

        assertEquals(400, context.response().getStatus());
        assertEquals("The workbook changed after the dry run. Run the dry run again.", body.get("message"));
        assertNull(context.resourceResolver().getResource(ROOT + "/offer-cta/apply"));
    }

    @Test
    void finalRunWritesTheFragmentsAndSavesARunReport() throws Exception {
        scbUpload(new String[] {"action", "offerCtaSlug", "label"}, new String[] {"CREATE", "apply", "Apply"});

        Map<String, Object> body = scbPost(scbServlet(10, 5000), BOOK, ROOT, "false", scbFileVersion());

        assertEquals(200, context.response().getStatus());
        assertEquals(Boolean.FALSE, body.get("dryRun"));
        assertNull(body.get("fileVersion"));
        assertEquals("Apply", ScbOfferListingTestContent.scbMaster(context, ROOT + "/offer-cta/apply").get("label"));
        String reportPath = (String) body.get("reportPath");
        assertTrue(reportPath.startsWith("/content/dam/aemcloudproject/reports/cf-upload-run-"), reportPath);
        assertNotNull(context.resourceResolver().getResource(reportPath).adaptTo(Asset.class));
    }

    @Test
    void finalRunReportsARunReportThatCouldNotBeSaved() throws Exception {
        ScbContentFragmentReportService failing = mock(ScbContentFragmentReportService.class);
        when(failing.scbSaveRunReport(any(), anyString(), anyString(), anyList())).thenThrow(new IOException("disk full"));
        scbUpload(new String[] {"action", "offerCtaSlug", "label"}, new String[] {"CREATE", "apply", "Apply"});
        context.registerService(ScbContentFragmentReportService.class, failing);
        ScbCfBulkUploadServlet servlet = context.registerInjectActivateService(new ScbCfBulkUploadServlet());

        Map<String, Object> body = scbPost(servlet, BOOK, ROOT, "false", scbFileVersion());

        assertEquals(200, context.response().getStatus());
        assertFalse(body.containsKey("reportPath"));
        assertEquals("The changes were written, but the run report could not be saved: disk full",
                body.get("reportError"));
        assertNotNull(context.resourceResolver().getResource(ROOT + "/offer-cta/apply"));
    }

    private ScbCfBulkUploadServlet scbServlet(int maxFileSizeMb, int maxRows) {
        Map<String, Object> config = new HashMap<>();
        config.put("maxFileSizeMb", maxFileSizeMb);
        config.put("maxRows", maxRows);
        context.registerInjectActivateService(new ScbContentFragmentReportServiceImpl());
        return context.registerInjectActivateService(new ScbCfBulkUploadServlet(), config);
    }

    private Map<String, Object> scbPost(ScbCfBulkUploadServlet servlet, String filePath, String rootPath,
                                        String dryRun, String fileVersion) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("filePath", filePath);
        params.put("rootPath", rootPath);
        if (dryRun != null) {
            params.put("dryRun", dryRun);
        }
        if (fileVersion != null) {
            params.put("fileVersion", fileVersion);
        }
        context.request().setMethod("POST");
        context.request().setParameterMap(params);
        servlet.doPost(context.request(), context.response());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = new ObjectMapper().readValue(context.response().getOutputAsString(), Map.class);
        return body;
    }

    private void scbReset() {
        context.response().resetBuffer();
        context.response().setStatus(200);
    }

    private void scbUpload(String[]... rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("offer-cta");
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            workbook.write(out);
            context.create().asset(BOOK, new ByteArrayInputStream(out.toByteArray()),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }
    }

    private String scbFileVersion() {
        return String.valueOf(context.resourceResolver().getResource(BOOK).adaptTo(Asset.class).getLastModified());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> scbResults(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("results");
    }
}
