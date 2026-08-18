package com.aemcloudproject.core.models.impl;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceWrapper;

/**
 * Re-types a resource so that it renders as another component, without changing its path
 * or hiding its children.
 *
 * <p>This is the Teaser-style delegation used by consuming components (Content Image,
 * Hero, Banner, Card, Promo, ...): the consumer wraps <em>its own</em> resource as the
 * Custom Image resource type and renders it with {@code data-sly-resource}. Because the
 * path is unchanged, the real {@code desktop} and {@code mobile} child resources are
 * still reachable and keep their own Core Image v3 identity and delivery URLs.</p>
 *
 * <pre>
 *   /content/.../test  (resourceType = ...customcomponents/contentimage)
 *        |
 *        +-- CustomImageResourceWrapper  -&gt; same path, resourceType = ...custom-image
 *                 |
 *                 +-- /content/.../test/desktop   (Core Image v3)
 *                 +-- /content/.../test/mobile    (Core Image v3)
 * </pre>
 *
 * <p>Only public Sling API is used - {@link ResourceWrapper}. Adobe's internal
 * {@code CoreResourceWrapper} is intentionally not used. Note that a single wrapper is
 * used purely to switch the <em>parent</em> component's rendering; the desktop and mobile
 * identities come from real child resources, never from wrappers.</p>
 */
public class CustomImageResourceWrapper extends ResourceWrapper {

    private final String resourceType;

    /**
     * @param resource     the resource to wrap
     * @param resourceType the resource type to render the resource as
     */
    public CustomImageResourceWrapper(Resource resource, String resourceType) {
        super(resource);
        this.resourceType = resourceType;
    }

    @Override
    public String getResourceType() {
        return resourceType;
    }

    @Override
    public String getResourceSuperType() {
        // Let Sling resolve the super type from the overridden resource type in /apps
        // instead of inheriting the wrapped resource's super type.
        return null;
    }

    @Override
    public boolean isResourceType(String resourceType) {
        return this.getResourceResolver().isResourceType(this, resourceType);
    }
}
