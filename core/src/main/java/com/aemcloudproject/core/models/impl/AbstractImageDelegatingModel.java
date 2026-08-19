package com.aemcloudproject.core.models.impl;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aemcloudproject.core.internal.resource.CustomImageResourceWrapper;

/**
 * Base class for components that delegate image rendering to Core Image v3 instead of
 * owning any {@code src}, {@code srcset}, {@code sizes} or Adaptive Image Servlet logic
 * of their own.
 *
 * <p>Delegation here is <em>markup</em> delegation: a resource is re-typed so that another
 * component's script renders it, and the result is included with
 * {@code data-sly-resource}. Because the delegated script is the one that calls
 * {@code data-sly-use} on Adobe's {@code Image} model, no {@code ModelFactory} plumbing is
 * needed anywhere in this project - the resource being rendered <em>is</em> the image
 * resource.</p>
 *
 * <p>The re-typed resource keeps its original <strong>path</strong>, so desktop and mobile
 * keep distinct delivery URLs and the Adaptive Image Servlet still resolves the correct
 * {@code fileReference}.</p>
 *
 * <p>This class is deliberately <strong>stateless</strong>. An earlier revision cached a
 * single {@code imageResource} field, which cannot serve a component that delegates more
 * than one image - the desktop resource would have been overwritten by the mobile one.
 * Every method takes the resource it should act on and returns a fresh result.</p>
 *
 * <p><strong>Delegation alone does not make an image deliverable.</strong> The second HTTP
 * request for the bytes resolves its servlet from the resource's <em>persisted</em>
 * {@code sling:resourceType}, long after the wrapper has been garbage collected. The image
 * resource must therefore persist a type that reaches
 * {@code core/wcm/components/image/v3/image}. Equally, the type it is re-typed <em>to</em>
 * must have a content policy mapped to it, or Core Image v3 finds no
 * {@code allowedRenditionWidths} and {@code srcset} silently disappears - see
 * {@code docs/custom-image.md}.</p>
 */
public abstract class AbstractImageDelegatingModel {

    /**
     * Component property name that indicates which Image Component will perform the image rendering for composed components.
     * When rendering images, the composed components that provide this property will be able to retrieve the content policy defined for the
     * Image Component's resource type.
     */
    public static final String IMAGE_DELEGATE = "imageDelegate";

    public static final String PN_FILE_DELEGATE = "fileReference";

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractImageDelegatingModel.class);

    /**
     * Re-types a resource so that another component's script renders it, without changing
     * its path or hiding its children.
     *
     * @param resource     the resource to re-type
     * @param resourceType the resource type to render it as
     * @return the re-typed resource, ready for {@code data-sly-resource}, or {@code null}
     */
    protected Resource delegateTo(Resource resource, String resourceType) {
        return delegateTo(resource, resourceType, null);
    }

    /**
     * Re-types a resource and exposes extra properties on it.
     *
     * <p>The extra properties are how a parent passes render-time context down to the
     * delegated script - the mobile {@code media} condition, for example - without that
     * script having to reach back up the tree or duplicate a constant.</p>
     *
     * @param resource             the resource to re-type
     * @param resourceType         the resource type to render it as
     * @param overriddenProperties extra properties visible to the delegated script as
     *                             {@code properties}, or {@code null}
     * @return the re-typed resource, or {@code null} when there is nothing to wrap
     */
    protected Resource delegateTo(Resource resource, String resourceType,
                                  Map<String, Object> overriddenProperties) {
        if (resource == null || StringUtils.isEmpty(resourceType)) {
            return null;
        }
        return new CustomImageResourceWrapper(resource, resourceType, overriddenProperties);
    }

    /**
     * Returns a named child re-typed to the given resource type - the desktop or mobile
     * <em>image resource</em> - or {@code null} when there is no usable asset on it.
     *
     * @param parent               the resource holding the child, normally the current resource
     * @param childName            name of the child resource carrying the asset
     * @param resourceType         the resource type that should render the child
     * @param overriddenProperties extra properties for the delegated script, or {@code null}
     * @return the delegated image resource, or {@code null}
     */
    protected Resource imageResourceOf(Resource parent, String childName, String resourceType,
                                       Map<String, Object> overriddenProperties) {
        if (parent == null || StringUtils.isEmpty(childName)) {
            return null;
        }
        Resource child = parent.getChild(childName);
        if (child == null) {
            LOGGER.debug("No '{}' child resource below {}", childName, parent.getPath());
            return null;
        }
        if (!hasResolvableAsset(child, child.getResourceResolver())) {
            LOGGER.debug("{} has no resolvable asset; it will not be rendered.", child.getPath());
            return null;
        }
        return delegateTo(child, resourceType, overriddenProperties);
    }

    /**
     * Checks if the component has an image.
     *
     * <p>A non-empty check on {@code fileReference} is not enough: long lived content
     * accumulates references to deleted assets, and Core Image v3 still returns a
     * {@code src} for one, so the page would render images that 404.</p>
     *
     * @return True if the component has an image, false if it does not.
     */
    protected boolean hasResolvableAsset(Resource resource, ResourceResolver resolver) {
        if (resource == null || resolver == null) {
            return false;
        }
        String ref = resource.getValueMap().get(PN_FILE_DELEGATE, String.class);
        return StringUtils.isNotBlank(ref) && resolver.getResource(ref) != null;
    }
}
