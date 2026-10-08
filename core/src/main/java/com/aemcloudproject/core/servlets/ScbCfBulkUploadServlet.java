package com.aemcloudproject.core.servlets;

import com.aemcloudproject.core.dto.ScbSheetData;
import com.aemcloudproject.core.services.ScbContentFragmentWriterService;
import com.aemcloudproject.core.services.ScbWorkbookParserService;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.Rendition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend of the CF bulk upload page: reads a workbook from DAM, creates or updates
 * the Content Fragments (or only checks them in a dry run) and returns one result per row as JSON.
 */
@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = ScbCfBulkUploadServlet.RESOURCE_TYPE,
        methods = HttpConstants.METHOD_POST,
        extensions = "json")
public class ScbCfBulkUploadServlet extends SlingAllMethodsServlet {

    /** Resource type of the "upload" node under the bulk upload page. */
    static final String RESOURCE_TYPE = "aemcloudproject/cfbulkupload/upload";
    private static final Logger log = LoggerFactory.getLogger(ScbCfBulkUploadServlet.class);

    private static final String PARAM_FILE_PATH = "filePath";
    private static final String PARAM_DRY_RUN = "dryRun";
    private static final String XLSX_EXTENSION = ".xlsx";

    @Reference
    private transient ScbWorkbookParserService workbookParserService;

    @Reference
    private transient ScbContentFragmentWriterService fragmentWriterService;

    /**
     * Checks the selected file, parses the workbook and writes or checks the fragments.
     * Responds with HTTP 400 and an error message or error list when the file or workbook
     * cannot be used, and with HTTP 200 and the per-row results otherwise.
     * Any {@code dryRun} value other than {@code "false"} is treated as a dry run.
     *
     * @param request  the request with the {@code filePath} and {@code dryRun} parameters
     * @param response the response the JSON is written to
     * @throws IOException if writing the response fails
     */
    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        String filePath = StringUtils.trimToEmpty(request.getParameter(PARAM_FILE_PATH));
        if (filePath.isEmpty()) {
            scbWriteError(response, "Select a workbook first.");
            return;
        }

        Resource resource = request.getResourceResolver().getResource(filePath);
        if (resource == null) {
            scbWriteError(response, "No file found at " + filePath + ".");
            return;
        }

        Asset asset = resource.adaptTo(Asset.class);
        if (asset == null) {
            scbWriteError(response, filePath + " is not a DAM asset.");
            return;
        }

        if (!StringUtils.endsWithIgnoreCase(asset.getName(), XLSX_EXTENSION)) {
            scbWriteError(response, filePath + " is not an .xlsx file.");
            return;
        }

        Rendition original = asset.getOriginal();
        if (original == null) {
            scbWriteError(response, filePath + " has no original file yet. Wait for DAM processing to finish.");
            return;
        }

        List<String> errors = new ArrayList<>();
        Map<String, ScbSheetData> sheets;
        try (InputStream file = original.getStream()) {
            sheets = workbookParserService.scbReadWorkbook(file, errors);
        } catch (IOException e) {
            log.info("Could not read {} as .xlsx", filePath, e);
            scbWriteError(response, "The file is not a valid .xlsx workbook.");
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        if (!errors.isEmpty()) {
            body.put("status", "error");
            body.put("errors", errors);
            scbWriteJson(response, HttpServletResponse.SC_BAD_REQUEST, body);
            return;
        }
        boolean dryRun = !"false".equalsIgnoreCase(request.getParameter(PARAM_DRY_RUN));

        List<Map<String, Object>> results = fragmentWriterService.scbWriteFragments(
                request.getResourceResolver(), sheets, dryRun);
        log.info("CF bulk upload of {} by {}: dryRun={}, {} results", filePath,
                request.getResourceResolver().getUserID(), dryRun, results.size());

        boolean anyFailed = results.stream().anyMatch(result -> "FAIL".equals(result.get("result")));
        body.put("status", anyFailed ? "error" : "ok");
        body.put("dryRun", dryRun);
        body.put("path", filePath);
        body.put("results", results);
        scbWriteJson(response, HttpServletResponse.SC_OK, body);
    }

    /**
     * Sends a single error message with HTTP 400.
     *
     * @param response the response to write to
     * @param message  the message shown to the author
     * @throws IOException if writing the response fails
     */
    private void scbWriteError(SlingHttpServletResponse response, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("message", message);
        scbWriteJson(response, HttpServletResponse.SC_BAD_REQUEST, body);
    }

    /**
     * Writes a map as a UTF-8 JSON response with the given HTTP status.
     *
     * @param response the response to write to
     * @param status   the HTTP status code
     * @param body     the content to serialise
     * @throws IOException if writing the response fails
     */
    private void scbWriteJson(SlingHttpServletResponse response, int status, Map<String, Object> body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        new ObjectMapper().writeValue(response.getWriter(), body);
    }
}
