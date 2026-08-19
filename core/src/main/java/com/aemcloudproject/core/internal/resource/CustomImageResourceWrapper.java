package com.aemcloudproject.core.internal.resource;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceWrapper;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;

import java.util.HashMap;
import java.util.Map;

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
    private final ValueMap valueMap;

    /**
     * @param resource     the resource to wrap
     * @param resourceType the resource type to render the resource as
     */

    public CustomImageResourceWrapper(Resource resource, String resourceType) {
        this(resource, resourceType, null);
    }

    /**
     * @param resource             the resource to wrap
     * @param resourceType         the resource type to render the resource as
     * @param overriddenProperties extra properties to expose on the wrapped resource, or
     *                             {@code null}. They are visible to the delegated script as
     *                             {@code properties} but are never written back to the JCR.
     */
    public CustomImageResourceWrapper(Resource resource, String resourceType,
                                      Map<String, Object> overriddenProperties) {
        super(resource);

        if (resource == null) {
            throw new IllegalArgumentException("The " + CustomImageResourceWrapper.class.getName()
                    + " cannot wrap a null resource.");
        }
        if (StringUtils.isEmpty(resourceType)) {
            throw new IllegalArgumentException("The " + CustomImageResourceWrapper.class.getName() + " needs to override the resource type of " +
                    "the wrapped resource, but the resourceType argument was null or empty.");
        }
        this.resourceType = resourceType;

        Map<String,Object> properties = new HashMap<>(resource.getValueMap());
        properties.put(ResourceResolver.PROPERTY_RESOURCE_TYPE,resourceType);
        if (overriddenProperties != null) {
            properties.putAll(overriddenProperties);
        }
        this.valueMap = new ValueMapDecorator(properties);
    }

    @Override
    public String getResourceType() {
        return resourceType;
    }

    @Override
    public ValueMap getValueMap(){
        return valueMap;
    }

    @Override
    public String getResourceSuperType() {
        return null;
    }

    @Override
    public boolean isResourceType(String resourceType) {
        return this.getResourceResolver().isResourceType(this, resourceType);
    }
}
