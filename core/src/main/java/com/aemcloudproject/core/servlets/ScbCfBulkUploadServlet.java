package com.aemcloudproject.core.servlets;

import com.aemcloudproject.core.config.ScbCfBulkUploadConfiguration;
import com.aemcloudproject.core.dto.ScbSheetData;
import com.aemcloudproject.core.services.ScbContentFragmentReportService;
import com.aemcloudproject.core.services.ScbContentFragmentWriterService;
import com.aemcloudproject.core.services.ScbWorkbookParserService;
import com.aemcloudproject.core.utils.ScbCfBulkUploadUtil;
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
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
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
import java.util.Locale;
import java.util.Map;

/**
 * Backend of the CF bulk upload page: reads a workbook from DAM, creates, updates or deletes the
 * Content Fragments under the picked folder (or only checks them in a dry run) and returns one result
 * per row as JSON. A final run is refused when the workbook changed after its dry run, and saves a
 * results report in DAM.
 */
@Component(service = Servlet.class, configurationPid = ScbCfBulkUploadConfiguration.PID)
@SlingServletResourceTypes(
        resourceTypes = ScbCfBulkUploadServlet.RESOURCE_TYPE,
        methods = {HttpConstants.METHOD_GET, HttpConstants.METHOD_POST},
        extensions = "json")
public class ScbCfBulkUploadServlet extends SlingAllMethodsServlet {

    /** Resource type of the "upload" node under the bulk upload page. */
    static final String RESOURCE_TYPE = "aemcloudproject/cfbulkupload/upload";
    private static final Logger log = LoggerFactory.getLogger(ScbCfBulkUploadServlet.class);

    private static final String PARAM_FILE_PATH = "filePath";
    private static final String PARAM_ROOT_PATH = "rootPath";
    private static final String PARAM_DRY_RUN = "dryRun";
    private static final String PARAM_FILE_VERSION = "fileVersion";
    private static final String XLSX_EXTENSION = ".xlsx";
    private static final String ACTION = "action";
    private static final long BYTES_PER_MB = 1024L * 1024L;

    @Reference
    private transient ScbWorkbookParserService workbookParserService;

    @Reference
    private transient ScbContentFragmentWriterService fragmentWriterService;

    @Reference
    private transient ScbContentFragmentReportService reportService;

    private String defaultRootPath;
    private int maxFileSizeMb;
    private int maxRows;

    /**
     * Reads the default fragment folder and the upload limits from the configuration.
     *
     * @param config the CF bulk upload configuration
     */
    @Activate
    @Modified
    protected void activate(ScbCfBulkUploadConfiguration config) {
        defaultRootPath = StringUtils.removeEnd(config.fragmentRootPath(), "/");
        maxFileSizeMb = config.maxFileSizeMb();
        maxRows = config.maxRows();
    }

    /**
     * Sends the folder the page pre-fills in its folder picker, as {@code {"rootPath": "..."}}.
     *
     * @param request  the request
     * @param response the response the JSON is written to
     * @throws IOException if writing the response fails
     */
    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rootPath", defaultRootPath);
        scbWriteJson(response, HttpServletResponse.SC_OK, body);
    }

    /**
     * Checks the selected file and folder, parses the workbook and writes or checks the fragments.
     * Responds with HTTP 400 and an error message or error list when the file, folder or workbook
     * cannot be used, exceeds the configured size or row limit, or (final run) changed after the dry
     * run, and with HTTP 200 and the per-row results otherwise. A dry run's reply carries the workbook's
     * {@code fileVersion}, which the final run sends back; a final run's reply carries the path of the
     * saved results report, or the reason it could not be saved.
     * Any {@code dryRun} value other than {@code "false"} is treated as a dry run.
     *
     * @param request  the request with the {@code filePath}, {@code rootPath}, {@code dryRun} and,
     *                 for a final run, {@code fileVersion} parameters
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
        String rootPath = StringUtils.removeEnd(StringUtils.trimToEmpty(request.getParameter(PARAM_ROOT_PATH)), "/");
        String folderProblem = ScbCfBulkUploadUtil.scbCheckFolder(request.getResourceResolver(), rootPath);
        if (folderProblem != null) {
            scbWriteError(response, folderProblem);
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

        boolean dryRun = !"false".equalsIgnoreCase(request.getParameter(PARAM_DRY_RUN));
        String fileVersion = String.valueOf(asset.getLastModified());
        if (!dryRun && !fileVersion.equals(request.getParameter(PARAM_FILE_VERSION))) {
            scbWriteError(response, "The workbook changed after the dry run. Run the dry run again.");
            return;
        }

        if (original.getSize() > maxFileSizeMb * BYTES_PER_MB) {
            scbWriteError(response, String.format(Locale.ROOT, "The workbook is %.1f MB; the limit is %d MB.",
                    (double) original.getSize() / BYTES_PER_MB, maxFileSizeMb));
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

        long actionRows = sheets.values().stream()
                .flatMap(sheet -> sheet.getRows().stream())
                .filter(row -> row.containsKey(ACTION))
                .count();
        if (actionRows > maxRows) {
            scbWriteError(response, String.format(Locale.ROOT,
                    "The workbook has %,d rows to process; the limit is %,d. Split it into smaller files.",
                    actionRows, maxRows));
            return;
        }
        List<Map<String, Object>> results = fragmentWriterService.scbWriteFragments(
                request.getResourceResolver(), sheets, rootPath, dryRun);
        log.info("CF bulk upload of {} into {} by {}: dryRun={}, {} results", filePath, rootPath,
                request.getResourceResolver().getUserID(), dryRun, results.size());

        boolean anyFailed = results.stream().anyMatch(result -> "FAIL".equals(result.get("result")));
        body.put("status", anyFailed ? "error" : "ok");
        body.put("dryRun", dryRun);
        body.put("path", filePath);
        body.put("rootPath", rootPath);
        if (dryRun) {
            body.put("fileVersion", fileVersion);
        } else {
            try {
                body.put("reportPath", reportService.scbSaveRunReport(request.getResourceResolver(), filePath,
                        rootPath, results));
            } catch (IOException | RuntimeException e) {
                log.error("Could not save the run report of {}", filePath, e);
                body.put("reportError", "The changes were written, but the run report could not be saved: "
                        + e.getMessage());
            }
        }
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
