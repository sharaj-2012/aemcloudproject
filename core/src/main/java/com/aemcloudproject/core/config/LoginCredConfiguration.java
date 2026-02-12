package com.aemcloudproject.core.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition
public @interface LoginCredConfiguration {

    @AttributeDefinition(name = "First Name" , description = "First Name" , type = AttributeType.STRING)
    String firstName();

    @AttributeDefinition(name = "Age" , description = "Age" , type = AttributeType.INTEGER)
    int age();

    @AttributeDefinition(name = "isAdult" , description = "Is Adult" , type = AttributeType.BOOLEAN)
    boolean isAdult();

    @AttributeDefinition(name = "Hobbies" , description = "Hobbies" , type = AttributeType.STRING)
    String[] hobbies();
}
