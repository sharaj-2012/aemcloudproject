package com.aemcloudproject.core.bulkimport;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The six content fragment models the offer workbook populates, one per sheet.
 * Declared in dependency order: a model only references models declared before it, or itself.
 */
public enum ModelSpec {

    MERCHANT_VENUE("merchantVenues", "offer-listing/merchants/venues", "merchant-venue",
            "merchantVenueSlug", "name", null,
            FieldSpec.text("name"),
            FieldSpec.text("merchantVenueSlug"),
            FieldSpec.multiline("address"),
            FieldSpec.decimal("latitude"),
            FieldSpec.decimal("longitude"),
            FieldSpec.text("telephone")),

    MERCHANT_DETAILS("MerchantDetails", "offer-listing/merchants", "merchant-details",
            "merchantSlug", "merchantName", "merchantID",
            FieldSpec.text("merchantName"),
            FieldSpec.text("merchantSlug"),
            FieldSpec.asset("merchantLogo"),
            FieldSpec.fragments("merchantVenues", "merchant-venue")),

    CATEGORY("OfferCategory", "offer-listing/categories", "category",
            "offerCategorySlug", "name", "ID",
            FieldSpec.text("name"),
            FieldSpec.text("offerCategorySlug"),
            FieldSpec.text("categoryIconPath"),
            FieldSpec.fragment("parent", "category")),

    OFFER_CARD("OfferCard", "offer-listing/cards", "offer-card",
            "offerCardSlug", "name", "id",
            FieldSpec.text("name"),
            FieldSpec.text("offerCardSlug"),
            FieldSpec.asset("logo"),
            FieldSpec.fragment("parent", "offer-card")),

    OFFER_CTA("OfferCta", "offer-listing/offer-cta", "offer-cta",
            "offerCtaSlug", "label", null,
            FieldSpec.text("label"),
            FieldSpec.text("offerCtaSlug"),
            FieldSpec.asset("url"),
            FieldSpec.text("deeplink")),

    OFFER_DETAIL("OfferDetail", "offer-listing", "offer-detail",
            "offerSlug", "offerTitle", "offerID",
            FieldSpec.number("offerID"),
            FieldSpec.text("offerSlug"),
            FieldSpec.text("offerTitle"),
            FieldSpec.multiline("offerSummary"),
            FieldSpec.multiline("offerDescription"),
            FieldSpec.asset("offerImage"),
            FieldSpec.text("alternateText"),
            FieldSpec.bool("hotPromo"),
            FieldSpec.datetime("offerStartDate"),
            FieldSpec.datetime("offerEndDate"),
            FieldSpec.text("offerURL"),
            FieldSpec.text("offerEmail"),
            FieldSpec.multiline("offerTnc"),
            FieldSpec.fragments("offerCategories", "category"),
            FieldSpec.fragments("offerMerchants", "merchant-details"),
            FieldSpec.fragments("offerCards", "offer-card"),
            FieldSpec.fragments("offerCta", "offer-cta"),
            FieldSpec.text("offerOrigin"),
            FieldSpec.text("brCode"),
            FieldSpec.text("qrCode"));

    public static final String MODELS_ROOT = "/conf/aemcloudproject/settings/dam/cfm/models";

    private final String sheetName;
    private final String folder;
    private final String modelName;
    private final String slugProperty;
    private final String titleProperty;
    private final String idProperty;
    private final List<FieldSpec> fields;

    ModelSpec(String sheetName, String folder, String modelName, String slugProperty,
              String titleProperty, String idProperty, FieldSpec... fields) {
        this.sheetName = sheetName;
        this.folder = folder;
        this.modelName = modelName;
        this.slugProperty = slugProperty;
        this.titleProperty = titleProperty;
        this.idProperty = idProperty;
        this.fields = Collections.unmodifiableList(Arrays.asList(fields));
    }

    public String getSheetName() {
        return sheetName;
    }

    /**
     * @return folder holding this model's fragments, relative to the import root
     */
    public String getFolder() {
        return folder;
    }

    public String getModelName() {
        return modelName;
    }

    public String getModelPath() {
        return MODELS_ROOT + "/" + modelName;
    }

    public String getSlugProperty() {
        return slugProperty;
    }

    public String getTitleProperty() {
        return titleProperty;
    }

    /**
     * @return the generated numeric ID property, or null when the model has none
     */
    public String getIdProperty() {
        return idProperty;
    }

    public List<FieldSpec> getFields() {
        return fields;
    }

    /**
     * Finds a field by property name, ignoring case, so workbook headers such as
     * {@code offerCategoryslug} match the model's {@code offerCategorySlug}.
     */
    public FieldSpec getField(String property) {
        for (FieldSpec field : fields) {
            if (field.getProperty().equalsIgnoreCase(property)) {
                return field;
            }
        }
        return null;
    }

    public static ModelSpec forSheet(String sheetName) {
        String wanted = sheetName.trim().toLowerCase(Locale.ROOT);
        for (ModelSpec model : values()) {
            if (model.sheetName.toLowerCase(Locale.ROOT).equals(wanted)) {
                return model;
            }
        }
        return null;
    }

    public static ModelSpec forModelName(String modelName) {
        for (ModelSpec model : values()) {
            if (model.modelName.equals(modelName)) {
                return model;
            }
        }
        throw new IllegalArgumentException("Unknown model " + modelName);
    }
}
