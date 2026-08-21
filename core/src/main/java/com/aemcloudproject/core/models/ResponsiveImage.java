package com.aemcloudproject.core.models;

import org.apache.sling.api.resource.Resource;

/**
 * Sling Model for the Responsive Image component. Exposes desktop and mobile
 * image resources that delegate rendering to Core Components Image v3.
 */
public interface ResponsiveImage {

    /** Property holding the DAM path of the desktop image. */
    String PN_DESKTOP_IMAGE_PATH = "desktopImagePath";
    /** Property holding the desktop alternative text. */
    String PN_DESKTOP_ALT_TEXT = "desktopAltText";
    /** Property holding the desktop "inherit alt from DAM" flag. */
    String PN_DESKTOP_ALT_FROM_DAM = "desktopAltValueFromDAM";
    /** Property holding the DAM path of the mobile image. */
    String PN_MOBILE_IMAGE_PATH = "mobileImagePath";
    /** Property holding the mobile alternative text. */
    String PN_MOBILE_ALT_TEXT = "mobileAltText";
    /** Property holding the mobile "inherit alt from DAM" flag. */
    String PN_MOBILE_ALT_FROM_DAM = "mobileAltValueFromDAM";

    /** Mirrored child node used by the Adaptive Image Servlet for the desktop image. */
    String NN_DESKTOP_IMAGE = "desktopImage";
    /** Mirrored child node used by the Adaptive Image Servlet for the mobile image. */
    String NN_MOBILE_IMAGE = "mobileImage";

    /**
     * @return a resource renderable by Image v3 for the desktop image, or {@code null}
     *         if no valid desktop image is authored
     */
    Resource getDesktopImageResource();

    /**
     * @return a resource renderable by Image v3 for the mobile image, or {@code null}
     *         if no valid mobile image is authored
     */
    Resource getMobileImageResource();

    /**
     * @return {@code true} if at least one image variant is authored and valid
     */
    boolean isHasContent();

    /**
     * @return {@code true} when both variants are authored, i.e. media-query based
     *         art direction should be applied
     */
    boolean isArtDirected();
}
