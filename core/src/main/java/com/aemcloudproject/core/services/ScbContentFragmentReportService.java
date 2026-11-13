package com.aemcloudproject.core.services;

import org.apache.sling.api.resource.ResourceResolver;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Builds the CF bulk upload workbooks for a picked offer-listing folder: the sample sheet, the report
 * of the existing fragments and the results of a final run.
 */
public interface ScbContentFragmentReportService {

    /**
     * Writes the sample workbook: the {@code _Guide} sheet and one sheet per model with the header row
     * (action, the model's slug column and every model field), one grey {@code SAMPLE} example row copied from an existing
     * fragment of the folder (none when the model has no fragments) and the action dropdown below it.
     *
     * @param resolver the author's resource resolver
     * @param rootPath the picked offer-listing folder
     * @param out      the stream the .xlsx is written to
     * @throws IOException if writing to {@code out} fails
     */
    void scbWriteSample(ResourceResolver resolver, String rootPath, OutputStream out) throws IOException;

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
