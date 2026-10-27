package com.aemcloudproject.core.servlets;

import com.aemcloudproject.core.services.ScbContentFragmentReportService;
import com.aemcloudproject.core.utils.ScbCfBulkUploadUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
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
 * Backend of the report card: GET downloads the empty sample sheet, POST ("Save report to DAM") saves
 * the report of every Content Fragment under the picked folder as a new, timestamped .xlsx in DAM.
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

    private static final String PARAM_ROOT_PATH = "rootPath";
    private static final String SAMPLE_FILE_NAME = "cf-bulk-upload-sample.xlsx";
    private static final String XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Reference
    private transient ScbContentFragmentReportService reportService;

    /**
     * Sends the empty sample workbook as a download named {@code cf-bulk-upload-sample.xlsx}.
     *
     * @param request  the request
     * @param response the response the .xlsx is written to
     * @throws IOException if writing the response fails
     */
    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        response.setContentType(XLSX_MIME_TYPE);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + SAMPLE_FILE_NAME + "\"");
        reportService.scbWriteSample(request.getResourceResolver(), response.getOutputStream());
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
        String rootPath = StringUtils.removeEnd(StringUtils.trimToEmpty(request.getParameter(PARAM_ROOT_PATH)), "/");
        log.info("CF report save of {} requested by {}", rootPath, request.getResourceResolver().getUserID());
        Map<String, Object> body = new LinkedHashMap<>();
        String problem = ScbCfBulkUploadUtil.scbCheckFolder(request.getResourceResolver(), rootPath);
        if (problem != null) {
            body.put("status", "error");
            body.put("message", problem);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        } else {
            try {
                body.put("path", reportService.scbSaveReport(request.getResourceResolver(), rootPath));
                body.put("status", "ok");
                response.setStatus(HttpServletResponse.SC_OK);
            } catch (IOException | RuntimeException e) {
                log.error("Could not save the CF report", e);
                body.put("status", "error");
                body.put("message", "Could not save the report: " + e.getMessage());
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        new ObjectMapper().writeValue(response.getWriter(), body);
    }
}
