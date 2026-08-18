package com.aemcloudproject.core.models.impl;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.apache.sling.models.factory.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.wcm.core.components.models.Image;
import com.aemcloudproject.core.models.CustomImage;

/**
 * Implementation of {@link CustomImage}.
 *
 * <p>Deliberately contains no image logic. For each of the two child resources it asks
 * Sling's {@link ModelFactory} for the Adobe {@link Image} model, using a request whose
 * resource is overridden with that child. Core Image v3 is request based, so adapting
 * the child {@link Resource} directly would not be sufficient.</p>
 *
 * <pre>
 *   original request -&gt; wrapped request (getResource() == /&lt;component&gt;/desktop)
 *                    -&gt; ModelFactory -&gt; Image.class
 * </pre>
 *
 * <p>Because the wrapped request keeps the original request attributes, the Core Image
 * model still sees the component context of this component and therefore resolves
 * {@code currentStyle} from the Custom Image content policy - which is where the allowed
 * rendition widths, sizes, JPEG quality and lazy loading settings live. The child
 * resources keep their own identity and path, so their generated delivery URLs are
 * distinct and the Adaptive Image Servlet can resolve the correct {@code fileReference}
 * later.</p>
 */
@Model(
        adaptables = SlingHttpServletRequest.class,
        adapters = CustomImage.class,
        resourceType = CustomImageImpl.RESOURCE_TYPE,
        defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
public class CustomImageImpl implements CustomImage {

    /** Resource type of the reusable Custom Image component. */
    public static final String RESOURCE_TYPE = "aemcloudproject/components/custom-image";

    /** Name of the child resource holding the desktop asset. */
    public static final String NN_DESKTOP = "desktop";

    /** Name of the child resource holding the mobile asset. */
    public static final String NN_MOBILE = "mobile";

    /**
     * Default mobile breakpoint in pixels. Matches the {@code phone} breakpoint of the
     * project grid ({@code clientlib-grid}, {@code @media (max-width: 768px)}), so the
     * image art direction switches at the same viewport width as the layout.
     */
    public static final int DEFAULT_BREAKPOINT = 768;

    private static final Logger LOG = LoggerFactory.getLogger(CustomImageImpl.class);

    @Self
    private SlingHttpServletRequest request;

    @OSGiService
    private ModelFactory modelFactory;

    @ValueMapValue
    private Integer breakpoint;

    private Image desktopImage;

    private Image mobileImage;

    @PostConstruct
    protected void initModel() {
        this.desktopImage = imageModelOf(NN_DESKTOP);
        this.mobileImage = imageModelOf(NN_MOBILE);
    }

    @Override
    public Image getDesktopImage() {
        return desktopImage;
    }

    @Override
    public Image getMobileImage() {
        return mobileImage;
    }

    @Override
    public boolean hasDesktopImage() {
        return desktopImage != null;
    }

    @Override
    public boolean hasMobileImage() {
        return mobileImage != null;
    }

    @Override
    public String getMobileMedia() {
        return "(max-width: " + effectiveBreakpoint() + "px)";
    }

    private int effectiveBreakpoint() {
        return (breakpoint != null && breakpoint > 0) ? breakpoint : DEFAULT_BREAKPOINT;
    }

    /**
     * Builds a Core Image v3 model for the given child resource.
     *
     * @param childName {@link #NN_DESKTOP} or {@link #NN_MOBILE}
     * @return the {@link Image} model, or {@code null} when the child resource is absent,
     *         has no authored asset, or could not be adapted
     */
    private Image imageModelOf(String childName) {
        if (request == null) {
            return null;
        }
        Resource parent = request.getResource();
        Resource child = parent.getChild(childName);
        if (child == null) {
            LOG.debug("No '{}' child resource below {}", childName, parent.getPath());
            return null;
        }
        if (modelFactory == null) {
            LOG.error("ModelFactory is not available, cannot build the Core Image model for {}",
                    child.getPath());
            return null;
        }
        try {
            Image image = modelFactory.getModelFromWrappedRequest(request, child, Image.class);
            if (image == null) {
                LOG.warn("{} could not be adapted to {}. Check that its resource type inherits "
                        + "core/wcm/components/image/v3/image.", child.getPath(), Image.class.getName());
                return null;
            }
            if (StringUtils.isBlank(image.getSrc())) {
                LOG.debug("No asset authored on {}", child.getPath());
                return null;
            }
            return image;
        } catch (Exception e) {
            LOG.error("Unable to create a Core Image model for {}", child.getPath(), e);
            return null;
        }
    }
}
