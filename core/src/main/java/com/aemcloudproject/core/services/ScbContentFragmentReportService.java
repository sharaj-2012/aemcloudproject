package com.aemcloudproject.core.services;

import org.apache.sling.api.resource.ResourceResolver;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Builds the CF bulk upload workbooks: the empty sample sheet, the report of the existing fragments
 * and the results report of a final run.
 */
public interface ScbContentFragmentReportService {

    /**
     * Writes the empty sample workbook: the {@code _Guide} sheet and one sheet per model with only the
     * header row (action, slug and every model field) and the action dropdown.
     *
     * @param resolver the author's resource resolver
     * @param out      the stream the .xlsx is written to
     * @throws IOException if writing to {@code out} fails
     */
    void scbWriteSample(ResourceResolver resolver, OutputStream out) throws IOException;

    /**
     * Writes every fragment under the folder, one sheet per model in the upload layout, and saves the
     * workbook in the configured report folder under a new name with the date and time.
     *
     * @param resolver the author's resource resolver
     * @param rootPath the folder whose fragments are exported, including its sub-folders
     * @return the path of the saved file
     * @throws IOException if the report cannot be built or saved
     */
    String scbSaveReport(ResourceResolver resolver, String rootPath) throws IOException;

    /**
     * Saves the results of a final run in the configured report folder under a new name with the date and time.
     *
     * @param resolver the author's resource resolver
     * @param filePath the uploaded workbook
     * @param rootPath the folder picked on the page
     * @param results  the run's results, as returned by {@link ScbContentFragmentWriterService}
     * @return the path of the saved file
     * @throws IOException if the report cannot be built or saved
     */
    String scbSaveRunReport(ResourceResolver resolver, String filePath, String rootPath,
                            List<Map<String, Object>> results) throws IOException;
}
