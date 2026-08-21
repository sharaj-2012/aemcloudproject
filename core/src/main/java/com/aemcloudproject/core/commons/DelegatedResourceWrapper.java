package com.aemcloudproject.core.commons;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceWrapper;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;

/**
 * A {@link ResourceWrapper} that presents an existing resource under a different
 * resource type and with an adjusted {@link ValueMap}, without touching the JCR.
 * <p>
 * Used to delegate rendering of custom-authored properties to another component
 * (e.g. Core Components Image v3), following the same composition pattern the
 * Core Teaser component uses with its {@code imageDelegate}.
 */
public class DelegatedResourceWrapper extends ResourceWrapper {

    private final String resourceType;
    private final ValueMap valueMap;

    /**
     * @param resource            the resource to wrap; its properties are copied, never modified
     * @param resourceType        the resource type the wrapper reports (the delegate component)
     * @param hiddenProperties    property names removed from the exposed {@link ValueMap}
     * @param overriddenProperties property names/values added to or replaced in the exposed {@link ValueMap}
     */
    public DelegatedResourceWrapper(Resource resource, String resourceType,
                                    Set<String> hiddenProperties, Map<String, Object> overriddenProperties) {
        super(resource);
        this.resourceType = resourceType;
        Map<String, Object> properties = new HashMap<>(resource.getValueMap());
        for (String hidden : hiddenProperties == null ? Collections.<String>emptySet() : hiddenProperties) {
            properties.remove(hidden);
        }
        if (overriddenProperties != null) {
            properties.putAll(overriddenProperties);
        }
        properties.put(ResourceResolver.PROPERTY_RESOURCE_TYPE, resourceType);
        this.valueMap = new ValueMapDecorator(properties);
    }

    @Override
    public String getResourceType() {
        return resourceType;
    }

    @Override
    public boolean isResourceType(String candidateType) {
        return getResourceResolver().isResourceType(this, candidateType);
    }

    @Override
    public ValueMap getValueMap() {
        return valueMap;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <AdapterType> AdapterType adaptTo(Class<AdapterType> type) {
        if (type == ValueMap.class) {
            return (AdapterType) valueMap;
        }
        return super.adaptTo(type);
    }
}
