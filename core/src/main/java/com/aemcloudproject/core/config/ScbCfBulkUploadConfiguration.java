package com.aemcloudproject.core.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi configuration with the repository paths used by the CF bulk upload and the report.
 * Shared by the writer and report services through {@link #PID}.
 */
@ObjectClassDefinition(name = "SCB CF Bulk Upload", description = "Repository paths for the CF bulk upload and report")
public @interface ScbCfBulkUploadConfiguration {

    /** Configuration PID shared by both services; also the name of the cfg.json file. */
    String PID = "com.aemcloudproject.core.config.ScbCfBulkUploadConfiguration";

    /**
     * Root folder that the model folders (e.g. {@code offer-listing/ctas}) are created under.
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
}
