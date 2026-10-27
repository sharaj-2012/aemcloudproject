package com.aemcloudproject.core.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helpers shared by the CF bulk upload and its report: reading model settings the Content
 * Fragment API does not expose, checking and creating DAM folders, and finding fragments.
 */
public final class ScbCfBulkUploadUtil {

    private static final String FOLDER_TYPE = "sling:OrderedFolder";
    private static final String FOLDER = "sling:Folder";
    private static final String ASSET = "dam:Asset";
    private static final String DAM_PREFIX = "/content/dam/";

    private ScbCfBulkUploadUtil() {
    }

    /**
     * Returns the fields marked Required in the model, read from the model's dialog definition.
     *
     * @param resolver  the resource resolver
     * @param modelPath the model path, e.g. {@code /conf/aemcloudproject/settings/dam/cfm/models/offer-detail}
     * @return the mandatory field names in model order; empty if the model cannot be read
     */
    public static Set<String> scbRequiredFields(ResourceResolver resolver, String modelPath) {
        Set<String> required = new LinkedHashSet<>();
        Resource items = resolver.getResource(modelPath + "/jcr:content/model/cq:dialog/content/items");
        if (items == null) {
            return required;
        }
        for (Resource item : items.getChildren()) {
            String name = item.getValueMap().get("name", String.class);
            String flag = item.getValueMap().get("required", String.class);
            if (name != null && ("on".equals(flag) || "true".equals(flag))) {
                required.add(name);
            }
        }
        return required;
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
     * Checks the fragment folder picked on the page.
     *
     * @param resolver the resource resolver
     * @param path     the picked folder, without a trailing slash; may be empty
     * @return the problem shown to the author, or {@code null} when it is an existing folder under {@code /content/dam}
     */
    public static String scbCheckFolder(ResourceResolver resolver, String path) {
        if (StringUtils.isEmpty(path)) {
            return "Select the fragment folder first.";
        }
        if (!path.startsWith(DAM_PREFIX)) {
            return path + " is not a DAM folder.";
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
}
