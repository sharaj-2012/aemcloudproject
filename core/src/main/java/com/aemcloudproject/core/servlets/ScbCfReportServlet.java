package com.aemcloudproject.core.servlets;

import com.aemcloudproject.core.services.ScbContentFragmentReportService;
import com.aemcloudproject.core.utils.ScbCfBulkUploadUtil;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backend of the report card for the picked offer-listing folder: GET downloads the sample sheet,
 * POST ("Save report to DAM") saves the report of every Content Fragment as a new, timestamped .xlsx in DAM.
 */
@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = ScbCfReportServlet.RESOURCE_TYPE,
        methods = {HttpConstants.METHOD_GET, HttpConstants.METHOD_POST},
        extensions = {"json", "xlsx"})
public class ScbCfReportServlet extends SlingAllMethodsServlet {

    /** Resource type of the "report" node under the bulk upload page. */
    static final String RESOURCE_TYPE = "aemcloudproject/cfbulkupload/report";
    private static final Logger log = LoggerFactory.getLogger(ScbCfReportServlet.class);

    private static final String SAMPLE_FILE_NAME = "scb-tgl-cf-bulk-upload-sample.xlsx";
    private static final String XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Reference
    private transient ScbContentFragmentReportService reportService;

    /**
     * Sends the sample workbook of the folder in the {@code rootPath} parameter as a download named
     * {@code scb-tgl-cf-bulk-upload-sample.xlsx}, or HTTP 400 and a message when the folder is not usable.
     *
     * @param request  the request with the {@code rootPath} parameter
     * @param response the response the .xlsx is written to
     * @throws IOException if writing the response fails
     */
    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        String rootPath = ScbCfBulkUploadUtil.scbRootPath(request);
        String problem = ScbCfBulkUploadUtil.scbCheckFolder(request.getResourceResolver(), rootPath);
        if (problem != null) {
            ScbCfBulkUploadUtil.scbWriteJson(response, HttpServletResponse.SC_BAD_REQUEST, scbError(problem));
            return;
        }
        response.setContentType(XLSX_MIME_TYPE);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + SAMPLE_FILE_NAME + "\"");
        reportService.scbWriteSample(request.getResourceResolver(), rootPath, response.getOutputStream());
    }

    /**
     * Saves the report of the folder in the {@code rootPath} parameter and responds with HTTP 200 and the
     * keys status and path, with HTTP 400 and a message when the folder is not usable, or with HTTP 500
     * and a message if the report cannot be built or saved.
     *
     * @param request  the request with the {@code rootPath} parameter
     * @param response the response the JSON is written to
     * @throws IOException if writing the response fails
     */
    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        String rootPath = ScbCfBulkUploadUtil.scbRootPath(request);
        log.info("CF report save of {} requested by {}", rootPath, request.getResourceResolver().getUserID());
        String problem = ScbCfBulkUploadUtil.scbCheckFolder(request.getResourceResolver(), rootPath);
        if (problem != null) {
            ScbCfBulkUploadUtil.scbWriteJson(response, HttpServletResponse.SC_BAD_REQUEST, scbError(problem));
            return;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("path", reportService.scbSaveReport(request.getResourceResolver(), rootPath));
            body.put("status", "ok");
            ScbCfBulkUploadUtil.scbWriteJson(response, HttpServletResponse.SC_OK, body);
        } catch (IOException | RuntimeException e) {
            log.error("Could not save the CF report", e);
            ScbCfBulkUploadUtil.scbWriteJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    scbError("Could not save the report: " + e.getMessage()));
        }
    }

    /**
     * Builds an error reply.
     *
     * @param message the message shown to the author
     * @return the keys status ({@code error}) and message
     */
    private Map<String, Object> scbError(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("message", message);
        return body;
    }
}
