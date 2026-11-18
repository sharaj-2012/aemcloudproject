package com.aemcloudproject.core.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi configuration with the report folder, report file names and upload limits of the CF bulk upload.
 * Shared by the upload servlet and the report service through {@link #PID}.
 */
@ObjectClassDefinition(name = "SCB TGL Content Fragment Bulk Upload", description = "Report folders, report file names and limits for the CF bulk upload")
public @interface ScbCfBulkUploadConfiguration {

    /** Configuration PID shared by both components; also the name of the cfg.json file. */
    String PID = "com.aemcloudproject.core.config.ScbCfBulkUploadConfiguration";

    /**
     * DAM folder the fragment report saved with Save report to DAM goes to; created if missing.
     *
     * @return the fragment report folder
     */
    @AttributeDefinition(name = "Fragment report folder", description = "DAM folder Save report to DAM saves the fragment report in")
    String fragmentReportFolder() default "/content/dam/aemcloudproject/reports/offer-listing";

    /**
     * DAM folder the results of each final run go to; created if missing.
     *
     * @return the run results folder
     */
    @AttributeDefinition(name = "Run results folder", description = "DAM folder the results of each final run are saved in")
    String runResultsFolder() default "/content/dam/aemcloudproject/reports/upload-runs";

    /**
     * File name prefix of the fragment report saved with Save report to DAM; the date, time and {@code .xlsx} are added.
     *
     * @return the prefix, e.g. {@code offer-listing-report} for {@code offer-listing-report-2026-10-20-14-05-33.xlsx}
     */
    @AttributeDefinition(name = "Fragment report file name", description = "File name prefix of Save report to DAM; date, time and .xlsx are added")
    String fragmentReportFileName() default "offer-listing-report";

    /**
     * File name prefix of the results saved after each final run; the date, time and {@code .xlsx} are added.
     *
     * @return the prefix, e.g. {@code cf-upload-run} for {@code cf-upload-run-2026-10-20-14-05-33.xlsx}
     */
    @AttributeDefinition(name = "Run results file name", description = "File name prefix of the results saved after each final run; date, time and .xlsx are added")
    String runResultsFileName() default "cf-upload-run";

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
