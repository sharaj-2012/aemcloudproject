package com.aemcloudproject.core.servlets;

import com.aemcloudproject.core.services.JsonAssetChangesService;
import com.aemcloudproject.core.services.JsonChanges;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.aemcloudproject.core.constants.MyProjectConstants.JSON;

/**
 * Returns a JSON asset's current content and its most recent version: GET {@code <asset>.jsonchanges.json}.
 */
@Component(service = Servlet.class)
@SlingServletResourceTypes(resourceTypes = "dam:Asset", selectors = JsonAssetChangesServlet.SELECTOR, extensions = JSON, methods = HttpConstants.METHOD_GET)
public class JsonAssetChangesServlet extends SlingSafeMethodsServlet {

    static final String SELECTOR = "jsonchanges";

    private static final ObjectMapper MAPPER = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Reference
    private transient JsonAssetChangesService changesService;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        JsonChanges changes = changesService.getChanges(request.getResource());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", changes.getStatus().code());
        body.put("path", changes.getPath());
        body.put("current", changes.getCurrent());
        body.put("previous", changes.getPrevious());
        response.setStatus(changes.getStatus().httpStatus());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        MAPPER.writeValue(response.getWriter(), body);
    }
}
