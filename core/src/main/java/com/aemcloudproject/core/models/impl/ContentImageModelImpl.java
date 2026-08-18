package com.aemcloudproject.core.models.impl;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import com.aemcloudproject.core.models.ContentImageModel;

/**
 * Implementation of {@link ContentImageModel}.
 *
 * <p>This consumer holds business properties only. It has no {@code getSrcset()},
 * {@code getImageWidth()}, {@code getCoreImgUrl()} or rendition logic - all image
 * concerns belong to the Custom Image component, which is rendered through a
 * {@link CustomImageResourceWrapper}.</p>
 */
@Model(
        adaptables = {SlingHttpServletRequest.class, Resource.class},
        adapters = ContentImageModel.class,
        resourceType = ContentImageModelImpl.RESOURCE_TYPE,
        defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
public class ContentImageModelImpl implements ContentImageModel {

    public static final String RESOURCE_TYPE =
            "aemcloudproject/components/customcomponents/contentimage";

    @SlingObject
    private Resource resource;

    @ValueMapValue
    private String title;

    @ValueMapValue
    private String description;

    @ValueMapValue
    private String linkUrl;

    /** Legacy property name, kept so existing authored content keeps its link. */
    @ValueMapValue
    private String imageUrl;

    private Resource imageResource;

    @PostConstruct
    protected void initModel() {
        if (resource == null) {
            return;
        }
        boolean hasImageChild = resource.getChild(CustomImageImpl.NN_DESKTOP) != null
                || resource.getChild(CustomImageImpl.NN_MOBILE) != null;

        if (hasImageChild) {
            imageResource = new CustomImageResourceWrapper(resource, CustomImageImpl.RESOURCE_TYPE);
        }
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getLink() {
        return StringUtils.isNotBlank(linkUrl) ? linkUrl : StringUtils.trimToNull(imageUrl);
    }

    @Override
    public Resource getImageResource() {
        return imageResource;
    }
}
