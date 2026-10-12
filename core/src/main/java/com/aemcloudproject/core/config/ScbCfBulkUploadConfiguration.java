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
     * Root folder that the model folders (e.g. {@code offer-listing/offer-cta}) are created under.
     *
     * @return the fragment root path
     */
    @AttributeDefinition(name = "Fragment root path", description = "Folder the content fragments are created under")
    String fragmentRootPath() default "/content/dam/aemcloudproject/cfs";

    /**
     * Folder holding the Content Fragment models; a sheet name is resolved to {@code <modelsPath>/<sheet>}.
     *
     * @return the models path
     */
    @AttributeDefinition(name = "Models path", description = "Folder holding the content fragment models")
    String modelsPath() default "/conf/aemcloudproject/settings/dam/cfm/models";

    /**
     * Folder exported by the report, including its sub-folders.
     *
     * @return the report path
     */
    @AttributeDefinition(name = "Report path", description = "Folder exported by Generate report")
    String reportPath() default "/content/dam/aemcloudproject/cfs/offer-listing";

    /**
     * Start of the report's file name; the date and {@code .xlsx} are added to it.
     *
     * @return the file name prefix, e.g. {@code offer-listing-report} for {@code offer-listing-report-2026-10-13.xlsx}
     */
    @AttributeDefinition(name = "Report file name", description = "Start of the report file name; -<date>.xlsx is added")
    String reportFileName() default "offer-listing-report";

    /**
     * Largest workbook accepted; larger files are rejected before they are opened.
     *
     * @return the size limit in megabytes
     */
    @AttributeDefinition(name = "Max file size (MB)", description = "Largest workbook accepted, in MB")
    int maxFileSizeMb() default 10;

    /**
     * Most rows with an action (CREATE or UPDATE) in one upload, across all sheets.
     *
     * @return the row limit
     */
    @AttributeDefinition(name = "Max rows", description = "Most rows with an action in one upload, across all sheets")
    int maxRows() default 5000;
}
