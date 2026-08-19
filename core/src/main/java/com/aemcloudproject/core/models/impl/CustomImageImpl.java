package com.aemcloudproject.core.models.impl;

import java.util.Collections;

import javax.annotation.PostConstruct;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import com.aemcloudproject.core.models.CustomImage;

/**
 * Implementation of {@link CustomImage}.
 *
 * <p>Deliberately contains no image logic. It re-types each of the two child resources to
 * a dedicated hidden component - {@link #RT_SOURCE} for mobile, {@link #RT_IMG} for
 * desktop - whose scripts render nothing but a {@code <source>} or an {@code <img>}. That
 * keeps both elements direct children of the {@code <picture>}, which is what the browser
 * requires for art direction to work.</p>
 *
 * <pre>
 *   /&lt;component&gt;/desktop  -&gt; re-typed to custom-image/img     -&gt; &lt;img&gt;
 *   /&lt;component&gt;/mobile   -&gt; re-typed to custom-image/source  -&gt; &lt;source media&gt;
 * </pre>
 *
 * <p>The children keep their own path, so their delivery URLs stay distinct and the
 * Adaptive Image Servlet resolves the correct {@code fileReference}. Both re-typed types
 * are mapped to the Content Image policy in the page template, which is where their
 * rendition widths come from.</p>
 */
@Model(
        adaptables = SlingHttpServletRequest.class,
        adapters = CustomImage.class,
        resourceType = CustomImageImpl.RESOURCE_TYPE,
        defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
public class CustomImageImpl extends AbstractImageDelegatingModel implements CustomImage {

    /** Resource type of the reusable Custom Image component. */
    public static final String RESOURCE_TYPE = "aemcloudproject/components/custom-image";

    /** Hidden component that renders a bare {@code <img>} element. */
    public static final String RT_IMG = "aemcloudproject/components/custom-image/img";

    /** Hidden component that renders a bare {@code <source>} element. */
    public static final String RT_SOURCE = "aemcloudproject/components/custom-image/source";

    /** Name of the child resource holding the desktop asset. */
    public static final String NN_DESKTOP = "desktop";

    /** Name of the child resource holding the mobile asset. */
    public static final String NN_MOBILE = "mobile";

    /** Property carrying the media condition down to the {@code <source>} script. */
    public static final String PN_MEDIA = "media";

    /**
     * Default mobile breakpoint in pixels. Matches the {@code phone} breakpoint of the
     * project grid ({@code clientlib-grid}, {@code @media (max-width: 768px)}), so the
     * image art direction switches at the same viewport width as the layout.
     */
    public static final int DEFAULT_BREAKPOINT = 768;

    @SlingObject
    private Resource resource;

    @ValueMapValue
    private Integer breakpoint;

    private Resource desktopImageResource;

    private Resource mobileImageResource;

    @PostConstruct
    protected void initModel() {
        if (resource == null) {
            return;
        }
        this.desktopImageResource = imageResourceOf(resource, NN_DESKTOP, RT_IMG, null);
        this.mobileImageResource = imageResourceOf(resource, NN_MOBILE, RT_SOURCE,
                Collections.singletonMap(PN_MEDIA, getMobileMedia()));
    }

    @Override
    public Resource getDesktopImageResource() {
        return desktopImageResource;
    }

    @Override
    public Resource getMobileImageResource() {
        return mobileImageResource;
    }

    @Override
    public boolean hasDesktopImage() {
        return desktopImageResource != null;
    }

    @Override
    public boolean hasMobileImage() {
        return mobileImageResource != null;
    }

    @Override
    public String getMobileMedia() {
        return "(max-width: " + effectiveBreakpoint() + "px)";
    }

    private int effectiveBreakpoint() {
        return (breakpoint != null && breakpoint > 0) ? breakpoint : DEFAULT_BREAKPOINT;
    }
}
