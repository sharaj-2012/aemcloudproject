package com.aemcloudproject.core.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helpers shared by the CF bulk upload and its report: checking and creating DAM folders, finding
 * fragments, reading IDs and models, and the servlets' request and JSON handling.
 */
public final class ScbCfBulkUploadUtil {

    private static final String FOLDER_TYPE = "sling:OrderedFolder";
    private static final String FOLDER = "sling:Folder";
    private static final String ASSET = "dam:Asset";
    private static final String DAM_PREFIX = "/content/dam/";
    private static final String OFFER_LISTING = "offer-listing";
    private static final String PARAM_ROOT_PATH = "rootPath";

    private ScbCfBulkUploadUtil() {
    }

    /**
     * Returns the folder, creating it and any missing parents as {@code sling:OrderedFolder}.
     * Does not commit.
     *
     * @param resolver   the resource resolver
     * @param folderPath the folder path
     * @return the existing or new folder
     * @throws PersistenceException if a folder cannot be created
     */
    public static Resource scbGetOrCreateFolder(ResourceResolver resolver, String folderPath) throws PersistenceException {
        Resource folder = resolver.getResource(folderPath);
        if (folder != null) {
            return folder;
        }
        int lastSlash = folderPath.lastIndexOf('/');
        Resource parent = scbGetOrCreateFolder(resolver, folderPath.substring(0, lastSlash));
        Map<String, Object> props = new HashMap<>();
        props.put("jcr:primaryType", FOLDER_TYPE);
        return resolver.create(parent, folderPath.substring(lastSlash + 1), props);
    }

    /**
     * Walks a folder and its sub-folders and returns every Content Fragment in it.
     *
     * @param folder the folder to walk
     * @return the fragment assets (assets with {@code jcr:content/data}), in folder order
     */
    public static List<Resource> scbFindFragments(Resource folder) {
        List<Resource> fragments = new ArrayList<>();
        for (Resource child : folder.getChildren()) {
            String type = child.getValueMap().get("jcr:primaryType", String.class);
            if (FOLDER.equals(type) || FOLDER_TYPE.equals(type)) {
                fragments.addAll(scbFindFragments(child));
            } else if (ASSET.equals(type) && child.getChild("jcr:content/data") != null) {
                fragments.add(child);
            }
        }
        return fragments;
    }

    /**
     * Returns the model a fragment uses.
     *
     * @param fragment the fragment asset
     * @return the model path from {@code jcr:content/data/cq:model}, or {@code null} when it has none
     */
    public static String scbModelOf(Resource fragment) {
        Resource data = fragment.getChild("jcr:content/data");
        return data == null ? null : data.getValueMap().get("cq:model", String.class);
    }

    /**
     * Reads a stored or typed ID.
     *
     * @param value a number, or text holding only digits
     * @return the ID, or {@code null} when the value is empty or not a whole number
     */
    public static Long scbParseId(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && ((String) value).trim().matches("\\d{1,18}")) {
            return Long.parseLong(((String) value).trim());
        }
        return null;
    }

    /**
     * Reads the fragment folder picked on the page.
     *
     * @param request the request with the {@code rootPath} parameter
     * @return the trimmed folder path without a trailing slash; empty when none is given
     */
    public static String scbRootPath(SlingHttpServletRequest request) {
        return StringUtils.removeEnd(StringUtils.trimToEmpty(request.getParameter(PARAM_ROOT_PATH)), "/");
    }

    /**
     * Checks the fragment folder picked on the page.
     *
     * @param resolver the resource resolver
     * @param path     the picked folder, without a trailing slash; may be empty
     * @return the problem shown to the author, or {@code null} when it is an existing folder named
     *         {@code offer-listing} under {@code /content/dam}
     */
    public static String scbCheckFolder(ResourceResolver resolver, String path) {
        if (StringUtils.isEmpty(path)) {
            return "Select the fragment folder first.";
        }
        if (!path.startsWith(DAM_PREFIX)) {
            return path + " is not a DAM folder.";
        }
        if (!path.endsWith("/" + OFFER_LISTING)) {
            return "Pick an offer-listing folder, e.g. /content/dam/aemcloudproject/cfs/offer-listing.";
        }
        Resource folder = resolver.getResource(path);
        if (folder == null) {
            return "No folder found at " + path + ".";
        }
        String type = folder.getValueMap().get("jcr:primaryType", String.class);
        if (!FOLDER.equals(type) && !FOLDER_TYPE.equals(type)) {
            return path + " is not a folder.";
        }
        return null;
    }

    /**
     * Writes a map as a UTF-8 JSON response with the given HTTP status.
     *
     * @param response the response to write to
     * @param status   the HTTP status code
     * @param body     the content to serialise
     * @throws IOException if writing the response fails
     */
    public static void scbWriteJson(SlingHttpServletResponse response, int status, Map<String, Object> body)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        new ObjectMapper().writeValue(response.getWriter(), body);
    }
}
