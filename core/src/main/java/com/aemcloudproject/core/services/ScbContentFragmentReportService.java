package com.aemcloudproject.core.services;

import org.apache.sling.api.resource.ResourceResolver;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Exports the Content Fragments under offer-listing to an Excel workbook.
 */
public interface ScbContentFragmentReportService {

    /**
     * Writes all fragments under offer-listing as an .xlsx in the import workbook layout,
     * one sheet per model, with an empty action column.
     *
     * @param resolver the author's resource resolver
     * @param out      the stream the .xlsx is written to
     * @return the number of fragments in the report
     * @throws IOException if writing to {@code out} fails
     */
    int scbWriteReport(ResourceResolver resolver, OutputStream out) throws IOException;

    /**
     * Returns the file name for a report built today: the configured report file name, the date and {@code .xlsx}.
     *
     * @return the file name, e.g. {@code offer-listing-report-2026-10-13.xlsx}
     */
    String scbReportFileName();
}
