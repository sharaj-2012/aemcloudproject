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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

/**
 * Backend of the "Generate report" button: returns an .xlsx of every Content Fragment
 * under offer-listing as a file download.
 */
@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = ScbCfReportServlet.RESOURCE_TYPE,
        methods = HttpConstants.METHOD_GET,
        extensions = "xlsx")
public class ScbCfReportServlet extends SlingSafeMethodsServlet {

    /** Resource type of the "report" node under the bulk upload page. */
    static final String RESOURCE_TYPE = "aemcloudproject/cfbulkupload/report";
    private static final Logger log = LoggerFactory.getLogger(ScbCfReportServlet.class);

    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    /** Response header carrying the number of fragments in the report. */
    static final String FRAGMENT_COUNT_HEADER = "X-Fragment-Count";

    @Reference
    private transient ScbContentFragmentReportService reportService;

    /**
     * Builds the report in memory and sends it as an attachment named
     * {@code offer-listing-report-<date>.xlsx}, with the fragment count in {@link #FRAGMENT_COUNT_HEADER}.
     * Responds with HTTP 500 and a plain-text message if the report cannot be built.
     *
     * @param request  the request
     * @param response the response the workbook is written to
     * @throws IOException if writing the response fails
     */
    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        String fileName = "offer-listing-report-" + LocalDate.now() + ".xlsx";
        log.info("CF report requested by {}", request.getResourceResolver().getUserID());

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int fragmentCount;
        try {
            fragmentCount = reportService.scbWriteReport(request.getResourceResolver(), buffer);
        } catch (IOException | RuntimeException e) {
            log.error("Could not build the CF report", e);
            response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/plain");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("Could not build the report: " + e.getMessage());
            return;
        }

        response.setContentType(XLSX_CONTENT_TYPE);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setHeader(FRAGMENT_COUNT_HEADER, String.valueOf(fragmentCount));
        response.setContentLength(buffer.size());

        buffer.writeTo(response.getOutputStream());
    }
}
