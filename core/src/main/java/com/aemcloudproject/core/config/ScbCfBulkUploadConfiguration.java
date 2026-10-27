package com.aemcloudproject.core.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi configuration with the repository paths and upload limits of the CF bulk upload and the report.
 * Shared by the upload servlet and the writer and report services through {@link #PID}.
 */
@ObjectClassDefinition(name = "SCB CF Bulk Upload", description = "Repository paths and limits for the CF bulk upload and report")
public @interface ScbCfBulkUploadConfiguration {

    /** Configuration PID shared by both services; also the name of the cfg.json file. */
    String PID = "com.aemcloudproject.core.config.ScbCfBulkUploadConfiguration";

    /**
     * Folder pre-filled in the page's folder picker; the model folders (e.g. {@code offer-cta}) are created under
     * the folder picked there.
     *
     * @return the default fragment folder
     */
    @AttributeDefinition(name = "Default fragment folder", description = "Folder pre-filled in the folder picker; model folders are created under the picked folder")
    String fragmentRootPath() default "/content/dam/aemcloudproject/cfs/offer-listing";

    /**
     * Folder holding the Content Fragment models; a sheet name is resolved to {@code <modelsPath>/<sheet>}.
     *
     * @return the models path
     */
    @AttributeDefinition(name = "Models path", description = "Folder holding the content fragment models")
    String modelsPath() default "/conf/aemcloudproject/settings/dam/cfm/models";

    /**
     * DAM folder the saved report and the final-run reports go to; created if missing.
     *
     * @return the report folder
     */
    @AttributeDefinition(name = "Report folder", description = "DAM folder the report is saved in")
    String reportFolder() default "/content/dam/aemcloudproject/reports";

    /**
     * File name prefix of the report saved with Save report to DAM; the date, time and {@code .xlsx} are added.
     *
     * @return the prefix, e.g. {@code offer-listing-report} for {@code offer-listing-report-2026-10-20-14-05-33.xlsx}
     */
    @AttributeDefinition(name = "Report file name", description = "File name prefix of the saved report; date, time and .xlsx are added")
    String reportFileName() default "offer-listing-report";

    /**
     * File name prefix of the results report saved after each final run; the date, time and {@code .xlsx} are added.
     *
     * @return the prefix, e.g. {@code cf-upload-run} for {@code cf-upload-run-2026-10-20-14-05-33.xlsx}
     */
    @AttributeDefinition(name = "Run report file name", description = "File name prefix of the report saved after each final run; date, time and .xlsx are added")
    String runReportFileName() default "cf-upload-run";

    /**
     * Largest workbook accepted; larger files are rejected before they are opened.
     *
     * @return the size limit in megabytes
     */
    @AttributeDefinition(name = "Max file size (MB)", description = "Largest workbook accepted, in MB")
    int maxFileSizeMb() default 10;

    /**
     * Most rows with an action (CREATE, UPDATE or DELETE) in one upload, across all sheets.
     *
     * @return the row limit
     */
    @AttributeDefinition(name = "Max rows", description = "Most rows with an action in one upload, across all sheets")
    int maxRows() default 5000;
}
