package com.aemcloudproject.core.commons;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.annotations.injectorspecific.InjectionStrategy;
import org.apache.sling.models.annotations.injectorspecific.ScriptVariable;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.wcm.api.components.Component;
import com.day.cq.wcm.api.components.ComponentManager;

/**
 * Reusable base class for Sling Models that delegate image rendering to
 * Core Components Image v3 while persisting custom property names.
 * <p>
 * Subclasses call {@link #buildDelegatedImageResource} once per image variant
 * (e.g. desktop/mobile). The returned resource reports the delegate image
 * resource type and translates the custom properties into the ones Image v3
 * expects ({@code fileReference}, {@code alt}, {@code altValueFromDAM}).
 * <p>
 * When a mirrored child node exists (created on dialog save by the
 * ResponsiveImagePostProcessor), it is used as the wrapped resource so the
 * Adaptive Image Servlet can resolve the {@code .coreimg.} URLs against a real
 * JCR node carrying a standard {@code fileReference}.
 */
public abstract class AbstractDelegatedImageModel {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractDelegatedImageModel.class);

    public static final String CORE_IMAGE_V3_RT = "core/wcm/components/image/v3/image";
    public static final String PN_IMAGE_DELEGATE = "imageDelegate";

    private static final String PN_FILE_REFERENCE = "fileReference";
    private static final String PN_ALT = "alt";
    private static final String PN_ALT_VALUE_FROM_DAM = "altValueFromDAM";
    private static final String PN_IMAGE_FROM_PAGE_IMAGE = "imageFromPageImage";
    private static final String PN_ALT_VALUE_FROM_PAGE_IMAGE = "altValueFromPageImage";

    @Self
    protected SlingHttpServletRequest request;

    @ScriptVariable(injectionStrategy = InjectionStrategy.OPTIONAL)
    protected Component component;

    /**
     * Builds a wrapped resource that Image v3 can render, or {@code null} when the
     * variant is not authored or the referenced DAM asset no longer exists.
     *
     * @param childName              name of the mirrored child node holding the standard image properties
     * @param pathProperty           custom property holding the DAM asset path
     * @param altProperty            custom property holding the alternative text
     * @param altFromDamProperty     custom property holding the "inherit alt from DAM" flag
     */
    protected Resource buildDelegatedImageResource(String childName, String pathProperty,
                                                   String altProperty, String altFromDamProperty) {
        Resource resource = request.getResource();
        ValueMap properties = resource.getValueMap();

        String imagePath = properties.get(pathProperty, String.class);
        if (StringUtils.isBlank(imagePath)) {
            return null;
        }
        if (resource.getResourceResolver().getResource(imagePath) == null) {
            LOG.warn("Image reference {} configured on {} does not resolve to a resource; skipping.",
                    imagePath, resource.getPath());
            return null;
        }

        Set<String> hidden = new HashSet<>();
        hidden.add(pathProperty);
        hidden.add(altProperty);
        hidden.add(altFromDamProperty);

        Map<String, Object> overrides = new HashMap<>();
        overrides.put(PN_FILE_REFERENCE, imagePath);
        overrides.put(PN_IMAGE_FROM_PAGE_IMAGE, Boolean.FALSE);
        overrides.put(PN_ALT_VALUE_FROM_PAGE_IMAGE, Boolean.FALSE);
        Boolean altFromDam = properties.get(altFromDamProperty, Boolean.class);
        if (altFromDam != null) {
            overrides.put(PN_ALT_VALUE_FROM_DAM, altFromDam);
        }
        String altText = properties.get(altProperty, String.class);
        if (StringUtils.isNotBlank(altText)) {
            overrides.put(PN_ALT, altText);
        }

        // Prefer the mirrored child node: its path gives the Adaptive Image Servlet a
        // real resource to serve. Fall back to the component node so the first render
        // still works if the mirror has not been written yet.
        Resource backing = resource.getChild(childName) != null ? resource.getChild(childName) : resource;
        return new DelegatedResourceWrapper(backing, getImageDelegate(), hidden, overrides);
    }

    /**
     * Resolves the delegate image component from the {@code imageDelegate} property of
     * the current component, mirroring the Core Teaser pattern.
     */
    protected String getImageDelegate() {
        Component resolved = component;
        if (resolved == null) {
            ComponentManager componentManager = request.getResourceResolver().adaptTo(ComponentManager.class);
            if (componentManager != null) {
                resolved = componentManager.getComponentOfResource(request.getResource());
            }
        }
        if (resolved != null) {
            String delegate = resolved.getProperties().get(PN_IMAGE_DELEGATE, String.class);
            if (StringUtils.isNotBlank(delegate)) {
                return delegate;
            }
        }
        return CORE_IMAGE_V3_RT;
    }
}
