package com.aemcloudproject.core.cfimport;

/**
 * The data type of a content fragment model field, from the {@code metaType} and
 * {@code valueType} the model editor stores on the field.
 */
public enum FieldType {
    TEXT("Single line text"),
    MULTILINE_TEXT("Multi line text"),
    LONG("Number (whole)"),
    DOUBLE("Number (decimal)"),
    BOOLEAN("Boolean"),
    DATE("Date and time"),
    CONTENT_REFERENCE("Content reference"),
    FRAGMENT_REFERENCE("Fragment reference"),
    /** Enumerations, tags, JSON and anything newer: the importer doesn't write these yet. */
    UNSUPPORTED("Not supported");

    private final String label;

    FieldType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** @param valueType without the trailing "[]" of multi-value fields */
    static FieldType of(String metaType, String valueType) {
        switch (metaType == null ? "" : metaType) {
            case "text-single":
                return TEXT;
            case "text-multi":
                return MULTILINE_TEXT;
            case "number":
                return "double".equals(valueType) ? DOUBLE : LONG;
            case "boolean":
                return BOOLEAN;
            case "date":
                return DATE;
            case "reference":
                return CONTENT_REFERENCE;
            case "fragment-reference":
                return FRAGMENT_REFERENCE;
            default:
                return UNSUPPORTED;
        }
    }
}
