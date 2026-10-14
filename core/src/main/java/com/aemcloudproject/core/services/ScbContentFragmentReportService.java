package com.aemcloudproject.core.services;

import org.apache.sling.api.resource.ResourceResolver;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

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
     * @throws IOException if writing to {@code out} fails
     */
    void scbWriteReport(ResourceResolver resolver, OutputStream out) throws IOException;

    /**
     * Builds the report and saves it in the configured DAM folder under the configured file name.
     * If the file already exists, its current content is first kept as a version labelled with
     * the date and time.
     *
     * @param resolver the author's resource resolver
     * @return the keys path and versionLabel ({@code null} on the first save)
     * @throws IOException if the report cannot be built or saved
     */
    Map<String, Object> scbSaveReport(ResourceResolver resolver) throws IOException;
}
