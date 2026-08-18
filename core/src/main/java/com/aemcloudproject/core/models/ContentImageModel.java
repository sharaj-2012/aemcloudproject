package com.aemcloudproject.core.models;

import org.apache.sling.api.resource.Resource;

/**
 * Sling Model for the Content Image component.
 *
 * <p>This component is a <em>consumer</em>. It owns business properties only - title,
 * description and an optional link - and knows nothing about image delivery. It exposes
 * a delegated resource that renders as the Custom Image component, which in turn owns
 * the desktop/mobile Core Image v3 resources.</p>
 */
public interface ContentImageModel {

    String getTitle();

    String getDescription();

    /**
     * @return an optional link the image is wrapped in, or {@code null}
     */
    String getLink();

    /**
     * Returns this component's own resource, re-typed as the Custom Image component so
     * that it can be rendered with {@code data-sly-resource}. The wrapper keeps the same
     * path, so the real {@code desktop} and {@code mobile} child resources - and
     * therefore the Core Image v3 delivery URLs - are preserved.
     *
     * @return the delegated Custom Image resource, or {@code null} when no image is authored
     */
    Resource getImageResource();
}
