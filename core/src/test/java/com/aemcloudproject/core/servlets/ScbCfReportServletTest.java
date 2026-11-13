package com.aemcloudproject.core.servlets;

import com.aemcloudproject.core.config.ScbOfferListingModel;
import com.aemcloudproject.core.services.ScbContentFragmentReportService;
import com.aemcloudproject.core.services.impl.ScbContentFragmentReportServiceImpl;
import com.aemcloudproject.core.testcontext.AppAemContext;
import com.aemcloudproject.core.testcontext.ScbOfferListingTestContent;
import com.day.cq.dam.api.Asset;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static com.aemcloudproject.core.testcontext.ScbOfferListingTestContent.ROOT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link ScbCfReportServlet}.
 */
@ExtendWith(AemContextExtension.class)
class ScbCfReportServletTest {

    private final AemContext context = AppAemContext.newAemContext();

    @Test
    void downloadsTheSampleSheetOfTheFolder() throws Exception {
        ScbCfReportServlet servlet = scbServlet();
        ScbOfferListingTestContent.scbFragment(context, ScbOfferListingModel.OFFER_CTA, "apply", "label", "Apply");
        context.request().setParameterMap(Collections.singletonMap("rootPath", ROOT));

        servlet.doGet(context.request(), context.response());

        assertEquals(200, context.response().getStatus());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                context.response().getContentType());
        assertEquals("attachment; filename=\"scb-tgl-cf-bulk-upload-sample.xlsx\"",
                context.response().getHeader("Content-Disposition"));
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(context.response().getOutput()))) {
            assertEquals("SAMPLE", workbook.getSheet("offer-cta").getRow(1).getCell(0).getStringCellValue());
        }
    }

    @Test
    void sampleIsRefusedForAFolderThatCannotBeUsed() throws Exception {
        ScbCfReportServlet servlet = scbServlet();

        servlet.doGet(context.request(), context.response());

        assertEquals(400, context.response().getStatus());
        Map<String, Object> body = scbBody();
        assertEquals("error", body.get("status"));
        assertEquals("Select the fragment folder first.", body.get("message"));
    }

    @Test
    void savesTheReportAndReturnsItsPath() throws Exception {
        ScbCfReportServlet servlet = scbServlet();
        context.request().setMethod("POST");
        context.request().setParameterMap(Collections.singletonMap("rootPath", ROOT + "/"));

        servlet.doPost(context.request(), context.response());

        assertEquals(200, context.response().getStatus());
        Map<String, Object> body = scbBody();
        assertEquals("ok", body.get("status"));
        String path = (String) body.get("path");
        assertTrue(path.startsWith("/content/dam/aemcloudproject/reports/offer-listing-report-"), path);
        assertNotNull(context.resourceResolver().getResource(path).adaptTo(Asset.class));
    }

    @Test
    void saveIsRefusedForAFolderThatCannotBeUsed() throws Exception {
        ScbCfReportServlet servlet = scbServlet();
        context.request().setMethod("POST");
        context.request().setParameterMap(Collections.singletonMap("rootPath", "/content/dam/missing/offer-listing"));

        servlet.doPost(context.request(), context.response());

        assertEquals(400, context.response().getStatus());
        assertEquals("No folder found at /content/dam/missing/offer-listing.", scbBody().get("message"));
    }

    @Test
    void saveAnswers500WhenTheReportCannotBeSaved() throws Exception {
        ScbOfferListingTestContent.scbSetUp(context);
        ScbContentFragmentReportService failing = mock(ScbContentFragmentReportService.class);
        when(failing.scbSaveReport(any(), anyString())).thenThrow(new IOException("disk full"));
        context.registerService(ScbContentFragmentReportService.class, failing);
        ScbCfReportServlet servlet = context.registerInjectActivateService(new ScbCfReportServlet());
        context.request().setMethod("POST");
        context.request().setParameterMap(Collections.singletonMap("rootPath", ROOT));

        servlet.doPost(context.request(), context.response());

        assertEquals(500, context.response().getStatus());
        assertEquals("Could not save the report: disk full", scbBody().get("message"));
    }

    private ScbCfReportServlet scbServlet() throws Exception {
        ScbOfferListingTestContent.scbSetUp(context);
        context.registerInjectActivateService(new ScbContentFragmentReportServiceImpl());
        return context.registerInjectActivateService(new ScbCfReportServlet());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> scbBody() throws IOException {
        return new ObjectMapper().readValue(context.response().getOutputAsString(), Map.class);
    }
}
