package com.aemcloudproject.core.models;

import java.util.Arrays;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.InjectionStrategy;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.ScriptVariable;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.factory.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.wcm.core.components.models.Image;
import com.day.cq.wcm.api.designer.Style;
import com.day.cq.wcm.api.policies.ContentPolicy;
import com.day.cq.wcm.api.policies.ContentPolicyManager;

/**
 * Exposes the desktop and mobile variants of the Responsive Image component as
 * Core Components Image v3 models, so the HTL can assemble a {@code <picture>}.
 * <p>
 * Each variant lives on its own child node ({@code desktopImage} /
 * {@code mobileImage}) that carries the <em>standard</em> Image v3 properties
 * ({@code sling:resourceType}, {@code fileReference}, {@code alt},
 * {@code altValueFromDAM}). Because those are real JCR nodes of a real image
 * resource type, nothing has to be renamed, wrapped or mirrored: Image v3 reads
 * them directly and the Adaptive Image Servlet can resolve the {@code .coreimg.}
 * URLs against them.
 * <p>
 * The model is deliberately not bound to a resource type - any component whose
 * node has these two children can render it, which is what lets Hero Banner (and
 * anything else) reuse the component as-is.
 */
@Model(adaptables = SlingHttpServletRequest.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class ResponsiveImage {

    private static final Logger LOG = LoggerFactory.getLogger(ResponsiveImage.class);

    /** Child node holding the desktop variant. */
    public static final String NN_DESKTOP_IMAGE = "desktopImage";
    /** Child node holding the mobile variant. */
    public static final String NN_MOBILE_IMAGE = "mobileImage";

    private static final String PN_FILE_REFERENCE = "fileReference";
    private static final String PN_MOBILE_BREAKPOINT = "mobileBreakpoint";
    private static final String DEFAULT_MOBILE_BREAKPOINT = "767px";

    @Self
    private SlingHttpServletRequest request;

    @OSGiService(injectionStrategy = InjectionStrategy.REQUIRED)
    private ModelFactory modelFactory;

    /**
     * Content policy of this component - the place the design dialog writes to.
     * Note that the Image v3 settings (allowedRenditionWidths, sizes, jpegQuality,
     * ...) are NOT read from here: both the Image model and the AdaptiveImageServlet
     * read them from the policy of the child node they render. Only settings this
     * class reads itself, such as the mobile breakpoint, come from this style.
     */
    @ScriptVariable(injectionStrategy = InjectionStrategy.OPTIONAL)
    private Style currentStyle;

    private Image desktop;
    private Image mobile;

    @PostConstruct
    private void init() {
        desktop = coreImage(NN_DESKTOP_IMAGE);
        mobile = coreImage(NN_MOBILE_IMAGE);
    }

    private Image coreImage(String childName) {
        Resource child = request.getResource().getChild(childName);
        if (child == null || StringUtils.isBlank(child.getValueMap().get(PN_FILE_REFERENCE, String.class))) {
            return null;
        }
        // getModelFromWrappedRequest returns null when the child cannot be adapted,
        // typically because it is missing the sling:resourceType that puts it in the
        // image chain. A model without a src means the fileReference points at an
        // asset that is gone. Either way the variant is unusable, and reporting it as
        // absent lets the other variant take over instead of rendering a broken img.
        Image image = modelFactory.getModelFromWrappedRequest(request, child, Image.class);
        if (image == null || StringUtils.isBlank(image.getSrc())) {
            LOG.warn("No renderable image at {}", child.getPath());
            return null;
        }
        return image;
    }

    /** @return the desktop variant, or {@code null} when it is not authored. */
    public Image getDesktop() {
        return desktop;
    }

    /** @return the mobile variant, or {@code null} when it is not authored. */
    public Image getMobile() {
        return mobile;
    }

    /**
     * @return the variant rendered by the {@code <img>} fallback inside the
     *         {@code <picture>}: the desktop image when authored, otherwise the
     *         mobile one, so a single authored variant still renders everywhere.
     */
    public Image getPrimary() {
        return desktop != null ? desktop : mobile;
    }

    /** @return {@code true} when the mobile variant should override on small screens. */
    public boolean isArtDirected() {
        return desktop != null && mobile != null;
    }

    /**
     * @return the media condition under which the mobile variant replaces the desktop
     *         one, built from the {@code mobileBreakpoint} design property.
     */
    public String getMobileMediaQuery() {
        String breakpoint = currentStyle == null
                ? DEFAULT_MOBILE_BREAKPOINT
                : currentStyle.get(PN_MOBILE_BREAKPOINT, DEFAULT_MOBILE_BREAKPOINT);
        if (StringUtils.isBlank(breakpoint)) {
            breakpoint = DEFAULT_MOBILE_BREAKPOINT;
        }
        return "(max-width: " + breakpoint + ")";
    }

    /**
     * Diagnostic string rendered as a data attribute outside publish mode: which
     * resource type each child actually has, which content policy resolves for it,
     * and which rendition widths the Image model ended up advertising. Use it to see
     * whether the design dialog's policy is reaching the variants; remove this and
     * its markup once that is confirmed.
     *
     * @return a human readable summary of the resolved policies
     */
    public String getDiagnostics() {
        return "desktop[" + describe(NN_DESKTOP_IMAGE, desktop) + "] "
                + "mobile[" + describe(NN_MOBILE_IMAGE, mobile) + "] "
                + "componentStyle=" + (currentStyle == null ? "NONE" : currentStyle.getPath());
    }

    private String describe(String childName, Image image) {
        Resource child = request.getResource().getChild(childName);
        if (child == null) {
            return "no child node";
        }
        ContentPolicyManager policyManager = request.getResourceResolver().adaptTo(ContentPolicyManager.class);
        ContentPolicy policy = policyManager == null ? null : policyManager.getPolicy(child, request);
        return "type=" + child.getResourceType()
                + ", policy=" + (policy == null ? "NONE" : policy.getPath())
                + ", widths=" + (image == null ? "n/a" : Arrays.toString(image.getWidths()));
    }

    /** @return {@code true} when no variant is authored. */
    public boolean isEmpty() {
        return desktop == null && mobile == null;
    }
}
