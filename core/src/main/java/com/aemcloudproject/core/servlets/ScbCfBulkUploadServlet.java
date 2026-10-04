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
 * WHAT: Backend of the CF Bulk Upload page (/apps/aemcloudproject/cfbulkupload). The author picks
 * a workbook in DAM and clicks Submit; this servlet checks the file, reads it, creates / updates
 * the Content Fragments, and returns one result per row as JSON.
 *
 * WHO CALLS IT: cf-bulk-upload.js, with
 *   POST /apps/aemcloudproject/cfbulkupload/content/cfbulkupload/upload.json
 *   filePath=/content/dam/aemcloudproject/imports/cf-import-combined.xlsx
 *
 * HOW SLING FINDS IT: the "upload" node under the page has
 * sling:resourceType="aemcloudproject/cfbulkupload/upload". A POST with extension .json on that
 * node matches the @SlingServletResourceTypes below, so Sling calls doPost().
 */
@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = ScbCfBulkUploadServlet.RESOURCE_TYPE,
        methods = HttpConstants.METHOD_POST,
        extensions = "json")
public class ScbCfBulkUploadServlet extends SlingAllMethodsServlet {

    // Must match sling:resourceType of the "upload" node in ui.apps .../cfbulkupload/content/cfbulkupload/.content.xml
    static final String RESOURCE_TYPE = "aemcloudproject/cfbulkupload/upload";
    private static final Logger log = LoggerFactory.getLogger(ScbCfBulkUploadServlet.class);

    // Name of the request parameter sent by cf-bulk-upload.js.
    private static final String PARAM_FILE_PATH = "filePath";
    private static final String XLSX_EXTENSION = ".xlsx";

    // Injected by OSGi: ScbWorkbookParserServiceImpl. "transient" because servlets are
    // Serializable and a service reference must not be serialized.
    @Reference
    private transient ScbWorkbookParserService workbookParserService;

    // Injected by OSGi: ScbContentFragmentWriterServiceImpl — creates / updates the fragments.
    @Reference
    private transient ScbContentFragmentWriterService fragmentWriterService;

    /**
     * WHAT: Checks the picked file step by step, reads the workbook, and answers with JSON.
     *
     * INPUT: request parameter "filePath",
     *        e.g. /content/dam/aemcloudproject/imports/cf-import-combined.xlsx
     *
     * OUTPUT (written to the response; the method itself returns nothing):
     *   HTTP 200 — workbook read without problems, fragments written; one result per row
     *   ("status" is "error" if any row FAILED, "ok" otherwise):
     *     {"status":"ok",
     *      "path":"/content/dam/aemcloudproject/imports/cf-import-combined.xlsx",
     *      "results":[{"sheet":"venues","result":"SKIPPED","message":"Not one of the CF models this uploader knows."},
     *                 {"sheet":"offer-cta","name":"book-now","action":"CREATE","result":"CREATED",
     *                  "path":"/content/dam/aemcloudproject/cfs/offer-listing/ctas/book-now"},
     *                 {"sheet":"offer-cta","name":"shop-online","action":"CREATE","result":"FAILED",
     *                  "path":"/content/dam/aemcloudproject/cfs/offer-listing/ctas/shop-online","message":"already exists"},
     *                 ...]}
     *   HTTP 400 — workbook read, but headers or actions are wrong (all problems listed together):
     *     {"status":"error","errors":["Sheet \"offer-detail\", column C: header \"Offer Title\" must be a single word.",
     *                                 "Sheet \"offer-cta\", row 4: action \"DELET\" must be CREATE or UPDATE."]}
     *   HTTP 400 — the file itself can't be used:
     *     {"status":"error","message":"No file found at /content/dam/aemcloudproject/imports/abc.xlsx."}
     */
    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        // STEP 1 — read the parameter.
        // trimToEmpty: null -> "", "  /content/dam/x.xlsx " -> "/content/dam/x.xlsx", so one isEmpty() check covers both.
        String filePath = StringUtils.trimToEmpty(request.getParameter(PARAM_FILE_PATH));
        if (filePath.isEmpty()) {
            // Nothing picked -> 400 {"status":"error","message":"Select a workbook first."}
            scbWriteError(response, "Select a workbook first.");
            return;
        }

        // STEP 2 — does the path exist?
        // Uses the logged-in author's resolver, so their permissions apply.
        // Returns null if the path doesn't exist OR the author may not read it.
        // e.g. ".../imports/does-not-exist.xlsx" -> null
        Resource resource = request.getResourceResolver().getResource(filePath);
        if (resource == null) {
            scbWriteError(response, "No file found at " + filePath + ".");
            return;
        }

        // STEP 3 — is it a DAM asset?
        // Only dam:Asset nodes adapt to Asset; a folder such as ".../imports" gives null.
        Asset asset = resource.adaptTo(Asset.class);
        if (asset == null) {
            scbWriteError(response, filePath + " is not a DAM asset.");
            return;
        }

        // STEP 4 — is it an .xlsx?
        // asset.getName() is the node name, e.g. "cf-import-combined.xlsx" (ok) or "asset.jpg" (rejected).
        if (!StringUtils.endsWithIgnoreCase(asset.getName(), XLSX_EXTENSION)) {
            scbWriteError(response, filePath + " is not an .xlsx file.");
            return;
        }

        // STEP 5 — get the uploaded file itself.
        // The "original" rendition is the file exactly as uploaded (<asset>/jcr:content/renditions/original).
        // Null only while DAM is still processing a fresh upload.
        Rendition original = asset.getOriginal();
        if (original == null) {
            scbWriteError(response, filePath + " has no original file yet. Wait for DAM processing to finish.");
            return;
        }

        // STEP 6 — read the workbook.
        // errors starts empty; the parser adds a message for every bad header / action it finds.
        List<String> errors = new ArrayList<>();
        // Filled by the parser: sheet name -> its headers and rows, e.g. "offer-cta" -> ScbSheetData.
        Map<String, ScbSheetData> sheets;
        // try-with-resources: the file stream is closed even if reading fails.
        try (InputStream file = original.getStream()) {
            sheets = workbookParserService.scbReadWorkbook(file, errors);
        } catch (IOException e) {
            // The parser throws IOException when the file isn't a real .xlsx (e.g. a .txt renamed to .xlsx).
            log.info("Could not read {} as .xlsx", filePath, e);
            scbWriteError(response, "The file is not a valid .xlsx workbook.");
            return;
        }

        // STEP 7 — answer.
        // LinkedHashMap keeps insertion order, so the JSON keys come out as status, path, sheets.
        Map<String, Object> body = new LinkedHashMap<>();
        if (!errors.isEmpty()) {
            // e.g. {"status":"error","errors":["Sheet \"offer-cta\": has no action column."]}
            body.put("status", "error");
            body.put("errors", errors);
            scbWriteJson(response, HttpServletResponse.SC_BAD_REQUEST, body);
            return;
        }
        // STEP 8 — create / update the fragments (only reached when the workbook has no errors).
        // Uses the author's own resolver, so AEM's normal permissions decide what they may write.
        // e.g. [{sheet=offer-cta, name=book-now, action=CREATE, result=CREATED, path=.../ctas/book-now}, ...]
        List<Map<String, Object>> results = fragmentWriterService.scbWriteFragments(request.getResourceResolver(), sheets);

        // "error" if at least one row FAILED, so the author notices; HTTP stays 200 because the
        // other rows were written.
        boolean anyFailed = results.stream().anyMatch(result -> "FAILED".equals(result.get("result")));
        // e.g. {"status":"ok","path":".../cf-import-combined.xlsx","results":[{...},...]}
        body.put("status", anyFailed ? "error" : "ok");
        body.put("path", filePath);
        body.put("results", results);
        scbWriteJson(response, HttpServletResponse.SC_OK, body);
    }

    /**
     * WHAT: Sends a single error message with HTTP 400.
     * INPUT:  message, e.g. "Select a workbook first."
     * OUTPUT: response 400 with {"status":"error","message":"Select a workbook first."}
     */
    private void scbWriteError(SlingHttpServletResponse response, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("message", message);
        scbWriteJson(response, HttpServletResponse.SC_BAD_REQUEST, body);
    }

    /**
     * WHAT: Writes any map as JSON with the given HTTP status.
     * INPUT:  status, e.g. 200; body, e.g. {status=ok, path=..., sheets={...}}
     * OUTPUT: response with that status, Content-Type application/json; charset UTF-8,
     *         and the map as JSON text, e.g. {"status":"ok","path":"...","sheets":{...}}
     */
    private void scbWriteJson(SlingHttpServletResponse response, int status, Map<String, Object> body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");   // so jQuery parses the reply as JSON
        response.setCharacterEncoding("UTF-8");         // so non-English text survives
        new ObjectMapper().writeValue(response.getWriter(), body);  // Map -> JSON
    }
}
