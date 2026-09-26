package com.aemcloudproject.core.bulkimport;

import com.aemcloudproject.core.testcontext.AppAemContext;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.jcr.RepositoryException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(AemContextExtension.class)
class OfferBulkImportTest {

    private final AemContext context = AppAemContext.newAemContext();

    @Test
    void createsMissingDamFoldersWithTitles() throws PersistenceException {
        context.create().resource("/content/dam");

        OfferBulkImport.ensureFolder(context.resourceResolver(), "/content/dam/test/cfs/offer-listing/cards");

        Resource cards = context.resourceResolver().getResource("/content/dam/test/cfs/offer-listing/cards");
        assertEquals("sling:Folder", cards.getValueMap().get("jcr:primaryType", String.class));
        assertEquals("cards", cards.getChild("jcr:content").getValueMap().get("jcr:title", String.class));
        assertEquals("sling:Folder", cards.getParent().getParent().getValueMap().get("jcr:primaryType", String.class));
    }

    @Test
    void refusesToCreateFoldersOutsideTheDam() {
        assertThrows(PersistenceException.class,
                () -> OfferBulkImport.ensureFolder(context.resourceResolver(), "/content/site/folder"));
    }

    @Test
    void mapsReportRowsToColumnsLeavingWorkbookLevelRowNumbersBlank() {
        ReportRow row = new ReportRow("OfferDetail", 4, "offer-detail", "duty-free-shopping",
                "/content/dam/x/duty-free-shopping", Outcome.UPDATED, java.util.Arrays.asList("offerTitle", "offerCards"), "");
        EnumMap<OfferBulkImport.ReportColumn, Object> columns = OfferBulkImport.toColumns(row);

        assertEquals("4", columns.get(OfferBulkImport.ReportColumn.ROW));
        assertEquals("UPDATED", columns.get(OfferBulkImport.ReportColumn.OUTCOME));
        assertEquals("offerTitle, offerCards", columns.get(OfferBulkImport.ReportColumn.FIELDS_CHANGED));
        assertEquals("", OfferBulkImport.toColumns(ReportRow.issue(Outcome.WARNING, "Notes", 0, "x"))
                .get(OfferBulkImport.ReportColumn.ROW));
    }

    @Test
    void rejectsAFileThatIsNotAWorkbook() throws IOException {
        OfferBulkImport process = process(upload("not a spreadsheet".getBytes()));

        RepositoryException e = assertThrows(RepositoryException.class, process::init);
        assertTrue(e.getMessage().startsWith("Could not read the workbook"));
    }

    @Test
    void rejectsAWorkbookWithNoRecognisedSheets() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Sheet1");
            workbook.write(out);
        }
        OfferBulkImport process = process(upload(out.toByteArray()));

        RepositoryException e = assertThrows(RepositoryException.class, process::init);
        assertTrue(e.getMessage().contains("OfferDetail"));
    }

    @Test
    void rejectsAMissingUpload() {
        assertThrows(RepositoryException.class, process(null)::init);
    }

    private static OfferBulkImport process(RequestParameter upload) {
        OfferBulkImport process = new OfferBulkImport(null, ZoneId.of("Asia/Singapore"));
        process.workbook = upload;
        return process;
    }

    private static RequestParameter upload(byte[] bytes) throws IOException {
        RequestParameter parameter = mock(RequestParameter.class);
        when(parameter.getInputStream()).thenReturn(new ByteArrayInputStream(bytes));
        return parameter;
    }
}
