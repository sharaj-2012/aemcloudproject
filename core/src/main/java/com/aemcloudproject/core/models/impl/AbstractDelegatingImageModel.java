package com.aemcloudproject.core.models.impl;

import com.drew.lang.annotations.NotNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aemcloudproject.core.internal.resource.DelegatingImageResourceWrapper;
import com.day.cq.wcm.api.components.Component;

/**
 * Base class for components that compose an image but delegate its rendering to
 * Core Image v3, so they inherit responsive srcset, native lazy loading, SVG
 * passthrough, Dynamic Media and DAM-inherited alt text without owning any of
 * that logic themselves.
 */
public abstract class AbstractDelegatingImageModel {

    /** Must match the property name read by AdaptiveImageServlet. */
    public static final String PN_IMAGE_DELEGATE = "imageDelegate";

    /** The property Core Image expects to hold the asset path. */
    public static final String PN_FILE_REFERENCE = "fileReference";

    private static final Logger LOG = LoggerFactory.getLogger(AbstractDelegatingImageModel.class);

    private Resource imageResource;
    protected void setImageResource(@NotNull Resource toBeWrappedResource) {
        this.imageResource = toBeWrappedResource;
    }

    /**
     * @param component  the current component definition, source of imageDelegate
     * @param resource   the resource to wrap (normally the component's own resource)
     */
    protected Resource initImageResource(Component component,
                                     Resource resource) {

        if (imageResource == null && component != null) {

            String delegate = component.getProperties().get(PN_IMAGE_DELEGATE, String.class);

            if (StringUtils.isBlank(delegate)) {
                LOG.error("Image delegation requires the '{}' property on component {}; "
                                + "its value must be the resource type of an image component.",
                        PN_IMAGE_DELEGATE, component.getPath());
            } else {
                imageResource = new DelegatingImageResourceWrapper(resource, delegate);
            }
        }
        return imageResource;
    }

    protected Resource getWrappedImageResource() {
        return imageResource;
    }

    /**
     * True only when fileReference points at an asset that actually exists.
     * Non-empty-string checks are insufficient: long-lived content contains
     * references to deleted assets, and Core Image v3 behaves unpredictably
     * when handed one.
     */
    protected static boolean hasResolvableAsset(Resource resource, ResourceResolver resolver) {
        String ref = resource.getValueMap().get(PN_FILE_REFERENCE, String.class);
        return StringUtils.isNotBlank(ref) && resolver.getResource(ref) != null;
    }
}
