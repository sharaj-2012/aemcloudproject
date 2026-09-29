package com.aemcloudproject.core.cfimport;

import java.util.Collections;
import java.util.List;

/**
 * One field of a content fragment model, as the model editor saved it on the instance.
 */
public final class ModelField {

    private final String name;
    private final String label;
    private final FieldType type;
    private final String valueType;
    private final boolean required;
    private final boolean multiple;
    private final List<String> allowedModels;
    private final String rootPath;
    private final Integer maxLength;
    private final String mimeType;

    ModelField(String name, String label, FieldType type, String valueType, boolean required, boolean multiple,
               List<String> allowedModels, String rootPath, Integer maxLength, String mimeType) {
        this.name = name;
        this.label = label;
        this.type = type;
        this.valueType = valueType;
        this.required = required;
        this.multiple = multiple;
        this.allowedModels = Collections.unmodifiableList(allowedModels);
        this.rootPath = rootPath;
        this.maxLength = maxLength;
        this.mimeType = mimeType;
    }

    /** The property name fragments store the value under, e.g. {@code offerCategories}. */
    public String getName() {
        return name;
    }

    /** The label authors see in the fragment editor, e.g. "Offer Categories". */
    public String getLabel() {
        return label;
    }

    public FieldType getType() {
        return type;
    }

    /** The raw valueType, e.g. {@code string/content-fragment[]}. */
    public String getValueType() {
        return valueType;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isMultiple() {
        return multiple;
    }

    /** For fragment references: the model paths a referenced fragment may use. Empty means any. */
    public List<String> getAllowedModels() {
        return allowedModels;
    }

    /** For references: the path referenced items must sit under, or null. */
    public String getRootPath() {
        return rootPath;
    }

    /** For single line text: the maximum number of characters, or null. */
    public Integer getMaxLength() {
        return maxLength;
    }

    /** For multi line text: the default format, e.g. {@code text/html}, or null. */
    public String getMimeType() {
        return mimeType;
    }
}
