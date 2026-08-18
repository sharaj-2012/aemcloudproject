package com.aemcloudproject.core.internal.resource;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceWrapper;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;

/**
 * Presents a resource as though it were of a different resource type, with
 * selected properties suppressed or added.
 *
 * Mirrors the CoreResourceWrapper used internally by the Core Components
 * Teaser, which lives in a non-exported package and cannot be reused directly.
 *
 * This object is read-only with respect to the JCR: it copies the ValueMap and
 * never persists anything.
 */
public class DelegatingImageResourceWrapper extends ResourceWrapper {

    private final String resourceType;
    private final ValueMap valueMap;

    /**
     * @param resource             the resource to present under a different type
     * @param resourceType         the type to report (the image delegate)
     */
    public DelegatingImageResourceWrapper(Resource resource,
                                          String resourceType) {

        super(resource);

        if (StringUtils.isBlank(resourceType)) {
            throw new IllegalArgumentException("resourceType must not be blank");
        }
        this.resourceType = resourceType;

        Map<String, Object> properties = new HashMap<>(resource.getValueMap());
        properties.put(ResourceResolver.PROPERTY_RESOURCE_TYPE, resourceType);

        this.valueMap = new ValueMapDecorator(properties);
    }

    @Override
    public <AdapterType> AdapterType adaptTo(Class<AdapterType> type) {
        if (type == ValueMap.class) {
            return (AdapterType) valueMap;
        }
        return super.adaptTo(type);
    }

    @Override
    public ValueMap getValueMap() {
        return valueMap;
    }

    @Override
    public String getResourceType() {
        return resourceType;
    }

    @Override
    public boolean isResourceType(String resourceType) {
        return this.getResourceResolver().isResourceType(this, resourceType);
    }
}
