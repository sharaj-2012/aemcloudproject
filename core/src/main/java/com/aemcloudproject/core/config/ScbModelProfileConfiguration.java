package com.aemcloudproject.core.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Factory configuration describing one Content Fragment model for the CF bulk upload and report:
 * where its fragments go and which fields give their name, title and ID. One configuration per model.
 */
@ObjectClassDefinition(name = "SCB CF Model Profile", description = "One Content Fragment model handled by the CF bulk upload and report")
public @interface ScbModelProfileConfiguration {

    /**
     * Model node name under the models path; also the workbook sheet name.
     *
     * @return the model name, e.g. {@code offer-detail}
     */
    @AttributeDefinition(name = "Model", description = "Model node name, also the sheet name, e.g. offer-detail")
    String model() default "";

    /**
     * Folder for the model's fragments, relative to the fragment root path.
     *
     * @return the folder, e.g. {@code offer-listing/offers}
     */
    @AttributeDefinition(name = "Folder", description = "Folder relative to the fragment root path, e.g. offer-listing/offers")
    String folder() default "";

    /**
     * Field whose value becomes the fragment's node name.
     *
     * @return the name field, e.g. {@code offerSlug}
     */
    @AttributeDefinition(name = "Name field", description = "Field whose value becomes the fragment name")
    String nameField() default "";

    /**
     * Field whose value becomes the fragment's title; the name is used when empty.
     *
     * @return the title field, e.g. {@code offerTitle}
     */
    @AttributeDefinition(name = "Title field", description = "Field whose value becomes the fragment title (optional)")
    String titleField() default "";

    /**
     * Numeric ID field filled automatically on CREATE and used to sort the report; empty for none.
     *
     * @return the ID field, e.g. {@code offerID}
     */
    @AttributeDefinition(name = "ID field", description = "Numeric ID field generated on CREATE (optional)")
    String idField() default "";

    /**
     * First ID used when the sheet has no IDs yet.
     *
     * @return the start ID
     */
    @AttributeDefinition(name = "Start ID", description = "First ID when the sheet has no IDs yet")
    long startId() default 1;

    /**
     * Position of the model's sheet in the report; lower comes first.
     *
     * @return the sheet order
     */
    @AttributeDefinition(name = "Order", description = "Position of the sheet in the report, lower first")
    int order() default 100;

    /**
     * Label of each configuration in the configuration manager.
     *
     * @return the name hint
     */
    String webconsole_configurationFactory_nameHint() default "{model}";
}
