package com.aemcloudproject.core.servlets;

import com.aemcloudproject.core.services.ScbContentFragmentReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Backend of the "Save report to DAM" button: builds the report of every Content Fragment under
 * offer-listing and saves it as an .xlsx in DAM, keeping the previous file as a version.
 */
@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = ScbCfReportServlet.RESOURCE_TYPE,
        methods = HttpConstants.METHOD_POST,
        extensions = "json")
public class ScbCfReportServlet extends SlingAllMethodsServlet {

    /** Resource type of the "report" node under the bulk upload page. */
    static final String RESOURCE_TYPE = "aemcloudproject/cfbulkupload/report";
    private static final Logger log = LoggerFactory.getLogger(ScbCfReportServlet.class);

    @Reference
    private transient ScbContentFragmentReportService reportService;

    /**
     * Saves the report to DAM and responds with HTTP 200 and the keys status, path and versionLabel,
     * or with HTTP 500 and a message if it cannot be built or saved.
     *
     * @param request  the request
     * @param response the response the JSON is written to
     * @throws IOException if writing the response fails
     */
    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        log.info("CF report save requested by {}", request.getResourceResolver().getUserID());
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            Map<String, Object> saved = reportService.scbSaveReport(request.getResourceResolver());
            body.put("status", "ok");
            body.putAll(saved);
            response.setStatus(HttpServletResponse.SC_OK);
        } catch (IOException | RuntimeException e) {
            log.error("Could not save the CF report", e);
            body.put("status", "error");
            body.put("message", "Could not save the report: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        new ObjectMapper().writeValue(response.getWriter(), body);
    }
}
