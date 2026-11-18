package com.aemcloudproject.core.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The offer-listing Content Fragment models handled by the CF bulk upload, in sheet order; deletes run
 * in reverse order. The sheet name is the model name.
 */
public enum ScbOfferListingModel {

    MERCHANT_VENUE("merchant-venue", "merchants/venues", "merchantVenueSlug", "name", null, null),
    MERCHANT_DETAILS("merchant-details", "merchants", "merchantSlug", "merchantName", id("merchantID", 100001), null,
            "merchantID"),
    CATEGORY("category", "categories", "offerCategorySlug", "name", id("ID", 1001), null, "ID"),
    OFFER_CARD("offer-card", "cards", "offerCardSlug", "name", id("ID", 1001), null, "ID"),
    OFFER_CTA("offer-cta", "offer-cta", "offerCtaSlug", "label", null, null),
    OFFER_DETAIL("offer-detail", "offers", "offerSlug", "offerTitle", id("offerID", 100001), "offerEndDate",
            "offerID", "offerTitle", "offerImage", "alternateText");

    /** Folder holding the Content Fragment models. */
    public static final String MODELS_PATH = "/conf/aemcloudproject/settings/dam/cfm/models";

    private final String model;
    private final String folder;
    private final String slugColumn;
    private final String titleField;
    private final String idField;
    private final long startId;
    private final String sortField;
    private final Set<String> requiredFields;

    ScbOfferListingModel(String model, String folder, String slugColumn, String titleField, ScbIdField id,
                         String sortField, String... requiredFields) {
        this.model = model;
        this.folder = folder;
        this.slugColumn = slugColumn;
        this.titleField = titleField;
        this.idField = id == null ? null : id.field;
        this.startId = id == null ? 0 : id.startId;
        this.sortField = sortField;
        this.requiredFields = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(requiredFields)));
    }

    /**
     * Finds the model a sheet stands for.
     *
     * @param sheet the sheet name
     * @return the model, or {@code null} when the sheet name is not a model
     */
    public static ScbOfferListingModel scbFromSheet(String sheet) {
        return Arrays.stream(values()).filter(value -> value.model.equals(sheet)).findFirst().orElse(null);
    }

    /**
     * Returns the model node name, which is also the sheet name.
     *
     * @return the model name, e.g. {@code offer-detail}
     */
    public String getModel() {
        return model;
    }

    /**
     * Returns the model path.
     *
     * @return the path, e.g. {@code /conf/aemcloudproject/settings/dam/cfm/models/offer-detail}
     */
    public String getModelPath() {
        return MODELS_PATH + "/" + model;
    }

    /**
     * Returns the folder for the model's fragments, relative to the picked offer-listing folder.
     *
     * @return the folder, e.g. {@code offers}
     */
    public String getFolder() {
        return folder;
    }

    /**
     * Returns the sheet column holding the fragment's node name. It exists only in the workbook and is never
     * stored in the fragment.
     *
     * @return the column, e.g. {@code offerSlug}
     */
    public String getSlugColumn() {
        return slugColumn;
    }

    /**
     * Returns the field whose value becomes the fragment's title.
     *
     * @return the title field
     */
    public String getTitleField() {
        return titleField;
    }

    /**
     * Returns the numeric ID field filled automatically on CREATE.
     *
     * @return the ID field, or {@code null} when the model has none
     */
    public String getIdField() {
        return idField;
    }

    /**
     * Returns the first ID used when neither the sheet nor DAM has an ID yet.
     *
     * @return the start ID
     */
    public long getStartId() {
        return startId;
    }

    /**
     * Returns the field the report sorts by, latest or highest first.
     *
     * @return the sort field, or {@code null} to sort by the ID field
     */
    public String getSortField() {
        return sortField;
    }

    /**
     * Returns the fields that must be filled on CREATE and can't be emptied on UPDATE.
     *
     * @return the required fields, in model order
     */
    public Set<String> getRequiredFields() {
        return requiredFields;
    }

    /**
     * Describes a model's numeric ID field.
     *
     * @param field   the ID field, e.g. {@code offerID}
     * @param startId the first ID used when neither the sheet nor DAM has an ID yet
     * @return the ID field and its start ID
     */
    private static ScbIdField id(String field, long startId) {
        return new ScbIdField(field, startId);
    }

    /**
     * A numeric ID field and the first ID it gets.
     */
    private static final class ScbIdField {

        private final String field;
        private final long startId;

        private ScbIdField(String field, long startId) {
            this.field = field;
            this.startId = startId;
        }
    }
}
