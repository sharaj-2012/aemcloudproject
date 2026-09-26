package com.aemcloudproject.core.bulkimport;

/**
 * One content fragment element that the workbook can populate.
 */
public final class FieldSpec {

    private final String property;
    private final FieldType type;
    private final String targetModel;

    private FieldSpec(String property, FieldType type, String targetModel) {
        this.property = property;
        this.type = type;
        this.targetModel = targetModel;
    }

    static FieldSpec text(String property) {
        return new FieldSpec(property, FieldType.TEXT, null);
    }

    static FieldSpec multiline(String property) {
        return new FieldSpec(property, FieldType.MULTILINE, null);
    }

    static FieldSpec number(String property) {
        return new FieldSpec(property, FieldType.LONG, null);
    }

    static FieldSpec decimal(String property) {
        return new FieldSpec(property, FieldType.DOUBLE, null);
    }

    static FieldSpec bool(String property) {
        return new FieldSpec(property, FieldType.BOOLEAN, null);
    }

    static FieldSpec datetime(String property) {
        return new FieldSpec(property, FieldType.DATETIME, null);
    }

    static FieldSpec asset(String property) {
        return new FieldSpec(property, FieldType.ASSET, null);
    }

    static FieldSpec fragment(String property, String targetModel) {
        return new FieldSpec(property, FieldType.FRAGMENT, targetModel);
    }

    static FieldSpec fragments(String property, String targetModel) {
        return new FieldSpec(property, FieldType.FRAGMENTS, targetModel);
    }

    public String getProperty() {
        return property;
    }

    public FieldType getType() {
        return type;
    }

    /**
     * @return the model this reference points at, or null for non-reference fields
     */
    public ModelSpec getTargetModel() {
        return targetModel == null ? null : ModelSpec.forModelName(targetModel);
    }
}
