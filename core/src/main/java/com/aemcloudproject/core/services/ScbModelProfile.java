package com.aemcloudproject.core.services;

/**
 * Settings of one Content Fragment model for the CF bulk upload and report.
 * One instance is registered per model profile configuration.
 */
public interface ScbModelProfile {

    /**
     * Returns the model node name, which is also the sheet name.
     *
     * @return the model name, e.g. {@code offer-detail}
     */
    String getModel();

    /**
     * Returns the folder for the model's fragments, relative to the fragment root path.
     *
     * @return the folder, e.g. {@code offer-listing/offers}
     */
    String getFolder();

    /**
     * Returns the field whose value becomes the fragment's node name.
     *
     * @return the name field
     */
    String getNameField();

    /**
     * Returns the field whose value becomes the fragment's title.
     *
     * @return the title field, or {@code null} when not set
     */
    String getTitleField();

    /**
     * Returns the numeric ID field.
     *
     * @return the ID field, or {@code null} when the model has none
     */
    String getIdField();

    /**
     * Returns the first ID used when the sheet has no IDs yet.
     *
     * @return the start ID
     */
    long getStartId();

    /**
     * Returns the position of the model's sheet in the report.
     *
     * @return the sheet order, lower first
     */
    int getOrder();
}
