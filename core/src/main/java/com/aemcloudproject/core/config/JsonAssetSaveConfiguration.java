package com.aemcloudproject.core.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "AEM Cloud Project - JSON Asset Save")
public @interface JsonAssetSaveConfiguration {

    @AttributeDefinition(name = "Max size (bytes)", description = "Largest JSON file the editor may save", type = AttributeType.LONG)
    long maxSizeBytes() default 2097152;
}
