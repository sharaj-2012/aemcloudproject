package com.aemcloudproject.core.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Move Asset Job Configuration" , description = "Move Asset Job Configuration")
public @interface MoveAssetJobConfiguration {

    @AttributeDefinition(name = "Enabled", description = "True, if job should be enabled", type = AttributeType.BOOLEAN)
    public boolean enabled() default false;

    @AttributeDefinition(name = "Cron Expression", description = "Cron expression used by the job scheduler", type = AttributeType.STRING)
    public String cronExpression() default "0 0 0/12 ? * * *";

    @AttributeDefinition(name = "Hobbies" , description = "Hobbies" , type = AttributeType.STRING)
    String[] hobbies();
}
