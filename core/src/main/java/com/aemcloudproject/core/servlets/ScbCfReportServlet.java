package com.aemcloudproject.core.servlets;

import com.aemcloudproject.core.services.ScbContentFragmentReportService;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.IOException;
import java.time.LocalDate;

/**
 * WHAT: Backend of the "Generate report" button on the CF Bulk Upload page. Returns an .xlsx of
 * every Content Fragment under /content/dam/aemcloudproject/cfs/offer-listing as a download.
 *
 * WHO CALLS IT: the browser, when the author clicks the button (it's a plain link):
 *   GET /apps/aemcloudproject/cfbulkupload/content/cfbulkupload/report.xlsx
 *
 * HOW SLING FINDS IT: the "report" node under the page has
 * sling:resourceType="aemcloudproject/cfbulkupload/report"; a GET with extension .xlsx on it
 * matches the annotation below, so Sling calls doGet().
 */
@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = ScbCfReportServlet.RESOURCE_TYPE,
        methods = HttpConstants.METHOD_GET,
        extensions = "xlsx")
public class ScbCfReportServlet extends SlingSafeMethodsServlet {

    // Must match sling:resourceType of the "report" node in ui.apps .../cfbulkupload/content/cfbulkupload/.content.xml
    static final String RESOURCE_TYPE = "aemcloudproject/cfbulkupload/report";
    private static final Logger log = LoggerFactory.getLogger(ScbCfReportServlet.class);

    // The MIME type browsers and Excel recognise as .xlsx.
    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    // Injected by OSGi: ScbContentFragmentReportServiceImpl — builds the workbook.
    @Reference
    private transient ScbContentFragmentReportService reportService;

    /**
     * WHAT: Streams the report to the browser as a file download.
     *
     * INPUT:  nothing besides the logged-in author's session.
     * OUTPUT: HTTP 200 with
     *           Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
     *           Content-Disposition: attachment; filename="offer-listing-report-2026-10-05.xlsx"
     *         and the .xlsx bytes — the browser saves it as a file instead of showing it.
     *         HTTP 500 with a short text message if building the workbook fails.
     */
    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        // e.g. offer-listing-report-2026-10-05.xlsx (date of the server, yyyy-MM-dd)
        String fileName = "offer-listing-report-" + LocalDate.now() + ".xlsx";
        log.info("CF report requested by {}", request.getResourceResolver().getUserID());

        // Headers must be set before the first byte of the body is written.
        response.setContentType(XLSX_CONTENT_TYPE);
        // "attachment" makes the browser download the file under this name.
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        try {
            // The service writes the workbook straight into the response body.
            reportService.scbWriteReport(request.getResourceResolver(), response.getOutputStream());
        } catch (IOException | RuntimeException e) {
            log.error("Could not build the CF report", e);
            // Only possible if nothing was sent yet; otherwise the download is simply cut short.
            if (!response.isCommitted()) {
                response.reset();
                response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("text/plain");
                response.getWriter().write("Could not build the report: " + e.getMessage());
            }
        }
    }
}
