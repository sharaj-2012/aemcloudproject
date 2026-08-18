package com.aemcloudproject.core.models;

import com.adobe.cq.wcm.core.components.models.Image;

/**
 * Sling Model for the reusable Custom Image component.
 *
 * <p>The component itself implements <strong>no</strong> image logic. It is pure
 * orchestration: it looks up the {@code desktop} and {@code mobile} child resources -
 * each of which is a real, independently addressable AEM resource inheriting
 * {@code core/wcm/components/image/v3/image} - and exposes an Adobe
 * {@link Image} model for each of them. All {@code src}, {@code srcset}, {@code sizes},
 * Adaptive Image Servlet, Web Optimized Image Delivery and Dynamic Media behaviour is
 * produced by Core Components, not by this project.</p>
 *
 * <p>Consuming components (Hero, Banner, Card, Promo, ...) should never talk to this
 * model directly for image delivery concerns; they simply render the Custom Image
 * resource type via a {@code ResourceWrapper}.</p>
 */
public interface CustomImage {

    /**
     * Returns the Core Image v3 model built against the {@code desktop} child resource,
     * or {@code null} when no desktop asset is authored.
     *
     * @return the desktop {@link Image} model, or {@code null}
     */
    Image getDesktopImage();

    /**
     * Returns the Core Image v3 model built against the {@code mobile} child resource,
     * or {@code null} when no mobile asset is authored. The mobile image is optional.
     *
     * @return the mobile {@link Image} model, or {@code null}
     */
    Image getMobileImage();

    /**
     * @return {@code true} when a renderable desktop image exists
     */
    boolean hasDesktopImage();

    /**
     * @return {@code true} when a renderable mobile image exists
     */
    boolean hasMobileImage();

    /**
     * Returns the media condition used by the mobile {@code <source>} element, for
     * example {@code (max-width: 768px)}.
     *
     * <p>This is the single source of truth for the breakpoint: it is not duplicated in
     * HTL, CSS logic or JavaScript. There is deliberately no JavaScript that switches
     * images based on viewport width - the browser resolves the media condition
     * natively.</p>
     *
     * @return the CSS media condition for the mobile source
     */
    String getMobileMedia();
}
