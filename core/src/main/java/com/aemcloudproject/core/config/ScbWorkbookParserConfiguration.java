package com.aemcloudproject.core.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi configuration with the limits for uploaded CF bulk upload workbooks.
 */
@ObjectClassDefinition(name = "SCB CF Bulk Upload - Workbook Parser", description = "Limits for workbooks uploaded to the CF Bulk Upload page")
public @interface ScbWorkbookParserConfiguration {

    /**
     * Maximum number of data rows across all sheets.
     *
     * @return the row limit
     */
    @AttributeDefinition(name = "Max rows", description = "Maximum number of data rows across all sheets", type = AttributeType.INTEGER)
    int maxRows() default 5000;

    /**
     * Maximum size of the uploaded workbook.
     *
     * @return the size limit in megabytes
     */
    @AttributeDefinition(name = "Max file size (MB)", description = "Maximum size of the uploaded .xlsx", type = AttributeType.INTEGER)
    int maxFileSizeMb() default 10;
}
