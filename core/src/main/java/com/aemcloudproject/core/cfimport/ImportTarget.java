package com.aemcloudproject.core.cfimport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where fragments of each importable model go, and which fields name and title them. A model
 * that isn't listed here can't be imported, whatever else sits in the models folder.
 */
final class ImportTarget {

    static final String ROOT = "/content/dam/aemcloudproject/cfs/offer-listing";

    private static final Map<String, ImportTarget> BY_MODEL = new LinkedHashMap<>();

    static {
        add("merchant-venue", "venues", "merchantVenueSlug", "name", null);
        add("category", "categories", "offerCategorySlug", "name", "ID");
        add("offer-card", "cards", "offerCardSlug", "name", "id");
        add("offer-cta", "ctas", "offerCtaSlug", "label", null);
        add("merchant-details", "merchants", "merchantSlug", "merchantName", "merchantID");
        add("offer-detail", "offers", "offerSlug", "offerTitle", "offerID");
    }

    private final String modelId;
    private final String folder;
    private final String slugField;
    private final String titleField;
    private final String idField;

    private ImportTarget(String modelId, String folder, String slugField, String titleField, String idField) {
        this.modelId = modelId;
        this.folder = folder;
        this.slugField = slugField;
        this.titleField = titleField;
        this.idField = idField;
    }

    private static void add(String modelId, String folderName, String slugField, String titleField, String idField) {
        BY_MODEL.put(modelId, new ImportTarget(modelId, ROOT + "/" + folderName, slugField, titleField, idField));
    }

    static ImportTarget forModel(String modelId) throws ImportException {
        ImportTarget target = BY_MODEL.get(modelId);
        if (target == null) {
            throw new ImportException("Model " + modelId + " can't be imported with this tool. It imports: "
                    + String.join(", ", new ArrayList<>(BY_MODEL.keySet())) + ".");
        }
        return target;
    }

    String getModelId() {
        return modelId;
    }

    /** The folder the model's fragments are created in. */
    String getFolder() {
        return folder;
    }

    /** The field whose value is the fragment's node name, and so its identity. */
    String getSlugField() {
        return slugField;
    }

    /** The field used as the fragment's title when it's created. */
    String getTitleField() {
        return titleField;
    }

    /** The business ID field, which must be unique within a file, or null if the model has none. */
    String getIdField() {
        return idField;
    }
}
