package com.aemcloudproject.core.models;

import org.apache.sling.api.resource.Resource;

/**
 * Sling Model for the Content Image component.
 */
public interface ContentImageModel {

    String getTitle();

    String getDescription();

    String getFileReference();

    String getAltText();

    String getImageUrl();

    /**
     * Resource presented as the configured Core Image delegate.
     */
    Resource getImageResource();

    /**
     * Whether the configured DAM reference currently resolves to an asset.
     */
    boolean hasImage();
}
