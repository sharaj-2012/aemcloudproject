package com.aemcloudproject.core.bulkimport;

/**
 * What happened, or in a dry run would happen, to one fragment or workbook entry.
 */
public enum Outcome {
    CREATED,
    UPDATED,
    UNCHANGED,
    /** Created from a slug that was referenced but not defined in its own sheet. */
    STUB_CREATED,
    ERROR,
    WARNING
}
