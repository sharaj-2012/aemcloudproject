package com.aemcloudproject.core.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi configuration read by ScbWorkbookParserServiceImpl.activate(). Shows up in
 * /system/console/configMgr as "SCB CF Bulk Upload - Workbook Parser". Without a cfg.json in
 * ui.config, the defaults below apply.
 *
 * <p>Example: to allow 8,000 rows, deploy
 * ui.config/.../config/com.aemcloudproject.core.services.impl.ScbWorkbookParserServiceImpl.cfg.json
 * containing {@code { "maxRows": 8000 }}.</p>
 */
@ObjectClassDefinition(name = "SCB CF Bulk Upload - Workbook Parser", description = "Limits for workbooks uploaded to the CF Bulk Upload page")
public @interface ScbWorkbookParserConfiguration {

    // Total data rows across all sheets. cf-import-combined.xlsx has 22, far below 5000.
    @AttributeDefinition(name = "Max rows", description = "Maximum number of data rows across all sheets", type = AttributeType.INTEGER)
    int maxRows() default 5000;

    // 10 means 10 MB = 10 * 1024 * 1024 = 10,485,760 bytes. cf-import-combined.xlsx is 8,904 bytes.
    @AttributeDefinition(name = "Max file size (MB)", description = "Maximum size of the uploaded .xlsx", type = AttributeType.INTEGER)
    int maxFileSizeMb() default 10;
}
