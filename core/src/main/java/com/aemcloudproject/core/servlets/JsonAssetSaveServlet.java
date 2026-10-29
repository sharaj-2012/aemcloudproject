package com.aemcloudproject.core.servlets;

import com.aemcloudproject.core.services.JsonAssetSaveService;
import com.aemcloudproject.core.services.SaveResult;
import com.aemcloudproject.core.services.SaveStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static com.aemcloudproject.core.constants.MyProjectConstants.JSON;

/**
 * Accepts the JSON editor's save: POST {@code <asset>.jsoneditor.json} with the raw JSON text as the body.
 */
@Component(service = Servlet.class)
@SlingServletResourceTypes(resourceTypes = "dam:Asset", selectors = JsonAssetSaveServlet.SELECTOR, extensions = JSON, methods = HttpConstants.METHOD_POST)
public class JsonAssetSaveServlet extends SlingAllMethodsServlet {

    static final String SELECTOR = "jsoneditor";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Reference
    private transient JsonAssetSaveService saveService;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        SaveResult result = isJsonRequest(request)
                ? saveService.save(request.getResource(), request.getInputStream().readAllBytes())
                : SaveResult.of(SaveStatus.WRONG_CONTENT_TYPE);
        write(response, result);
    }

    /**
     * Requiring application/json means a cross-site page cannot send the request without a CORS preflight.
     */
    private static boolean isJsonRequest(SlingHttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("application/json");
    }

    private static void write(SlingHttpServletResponse response, SaveResult result) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", result.getStatus().code());
        body.put("message", result.getMessage());
        putIfPresent(body, "line", result.getLine());
        putIfPresent(body, "column", result.getColumn());
        putIfPresent(body, "versionLabel", result.getVersionLabel());
        response.setStatus(result.getStatus().httpStatus());
        response.setContentType("application/json;charset=UTF-8");
        MAPPER.writeValue(response.getWriter(), body);
    }

    private static void putIfPresent(Map<String, Object> body, String key, Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }
}
