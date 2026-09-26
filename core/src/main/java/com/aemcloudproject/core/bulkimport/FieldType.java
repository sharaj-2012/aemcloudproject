package com.aemcloudproject.core.bulkimport;

/**
 * How a workbook cell is read and how the value is written to a content fragment element.
 */
public enum FieldType {
    TEXT,
    MULTILINE,
    LONG,
    DOUBLE,
    BOOLEAN,
    DATETIME,
    /** A path or URL stored in a content reference element. */
    ASSET,
    /** A single fragment reference, given in the workbook as a slug. */
    FRAGMENT,
    /** A multi-value fragment reference, given in the workbook as a list of slugs. */
    FRAGMENTS;

    public boolean isReference() {
        return this == FRAGMENT || this == FRAGMENTS;
    }
}
