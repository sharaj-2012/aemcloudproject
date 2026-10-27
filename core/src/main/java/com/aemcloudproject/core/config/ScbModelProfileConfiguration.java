package com.aemcloudproject.core.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Factory configuration describing one Content Fragment model for the CF bulk upload and report:
 * where its fragments go, which fields give their title and ID, and how the report sorts them. One configuration per model.
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
     * Folder for the model's fragments, relative to the folder picked on the page.
     *
     * @return the folder, e.g. {@code offers}
     */
    @AttributeDefinition(name = "Folder", description = "Folder relative to the picked folder, e.g. offers")
    String folder() default "";

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
     * Field the report sorts the model's sheet by, latest or highest first; empty sorts by the ID field, smallest first.
     *
     * @return the sort field, e.g. {@code offerEndDate}
     */
    @AttributeDefinition(name = "Sort field", description = "Field the report sorts by, latest or highest first (optional; default: ID field, smallest first)")
    String sortField() default "";

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
