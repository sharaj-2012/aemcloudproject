package com.aemcloudproject.core.models;

import org.apache.sling.api.resource.Resource;

/**
 * Sling Model for the reusable Custom Image component.
 *
 * <p>The component itself implements <strong>no</strong> image logic. It is pure
 * orchestration: it looks up the {@code desktop} and {@code mobile} child resources -
 * each of which is a real, independently addressable AEM resource inheriting
 * {@code core/wcm/components/image/v3/image} - and re-types each one so that a dedicated
 * script renders it as a bare {@code <source>} or {@code <img>} element. All {@code src},
 * {@code srcset}, {@code sizes}, Adaptive Image Servlet, Web Optimized Image Delivery and
 * Dynamic Media behaviour is produced by Core Components, not by this project.</p>
 *
 * <p>Resources rather than models are exposed deliberately: the delegated scripts call
 * {@code data-sly-use} on Adobe's {@code Image} model themselves, because the resource
 * they render <em>is</em> the image resource. Nothing here needs {@code ModelFactory}.</p>
 *
 * <p>Consuming components (Hero, Banner, Card, Promo, ...) should never talk to this
 * model directly for image delivery concerns; they simply render the Custom Image
 * resource type via a {@code ResourceWrapper}.</p>
 */
public interface CustomImage {

    /**
     * Returns the {@code desktop} child re-typed to the component that renders a bare
     * {@code <img>}, ready for {@code data-sly-resource}.
     *
     * @return the desktop image {@link Resource}, or {@code null} when none is authored
     */
    Resource getDesktopImageResource();

    /**
     * Returns the {@code mobile} child re-typed to the component that renders a bare
     * {@code <source>}, ready for {@code data-sly-resource}. The mobile image is optional.
     *
     * @return the mobile image {@link Resource}, or {@code null} when none is authored
     */
    Resource getMobileImageResource();

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
     * HTL, CSS logic or JavaScript. It is handed to the mobile {@code <source>} script as
     * a property on the re-typed resource, so that script does not need to know the
     * default either. There is deliberately no JavaScript that switches images based on
     * viewport width - the browser resolves the media condition natively.</p>
     *
     * @return the CSS media condition for the mobile source
     */
    String getMobileMedia();
}
