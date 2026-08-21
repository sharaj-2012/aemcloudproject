package com.aemcloudproject.core.models.impl;

import javax.annotation.PostConstruct;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;

import com.aemcloudproject.core.commons.AbstractDelegatedImageModel;
import com.aemcloudproject.core.models.ResponsiveImage;

@Model(adaptables = SlingHttpServletRequest.class,
        adapters = ResponsiveImage.class,
        resourceType = ResponsiveImageImpl.RESOURCE_TYPE,
        defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class ResponsiveImageImpl extends AbstractDelegatedImageModel implements ResponsiveImage {

    public static final String RESOURCE_TYPE = "aemcloudproject/components/responsiveimage";

    private Resource desktopImageResource;
    private Resource mobileImageResource;

    @PostConstruct
    private void init() {
        desktopImageResource = buildDelegatedImageResource(NN_DESKTOP_IMAGE,
                PN_DESKTOP_IMAGE_PATH, PN_DESKTOP_ALT_TEXT, PN_DESKTOP_ALT_FROM_DAM);
        mobileImageResource = buildDelegatedImageResource(NN_MOBILE_IMAGE,
                PN_MOBILE_IMAGE_PATH, PN_MOBILE_ALT_TEXT, PN_MOBILE_ALT_FROM_DAM);
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
    public boolean isHasContent() {
        return desktopImageResource != null || mobileImageResource != null;
    }

    @Override
    public boolean isArtDirected() {
        return desktopImageResource != null && mobileImageResource != null;
    }
}
