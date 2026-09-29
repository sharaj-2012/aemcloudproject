package com.aemcloudproject.core.cfimport;

/**
 * Columns of the Offer CF Import report. MCP's GenericReport shows one column per constant,
 * in declaration order, in both the HTML view and the Excel download.
 */
public enum ReportColumn {
    FILE,
    ROW,
    MODEL,
    PATH,
    /** e.g. CREATED, WILL UPDATE, UNCHANGED, ERROR; SUMMARY or NOTE on run-level lines. */
    OUTCOME,
    FIELDS_CHANGED,
    MESSAGE
}
