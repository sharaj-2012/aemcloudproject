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
}
