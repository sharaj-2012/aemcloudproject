package com.aemcloudproject.core.models.impl;

import javax.annotation.PostConstruct;

import com.day.cq.wcm.api.components.Component;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ScriptVariable;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import com.aemcloudproject.core.models.ContentImageModel;

/**
 * Implementation of {@link ContentImageModel}.
 *
 * <p>This consumer holds business properties only. It has no {@code getSrcset()},
 * {@code getImageWidth()}, {@code getCoreImgUrl()} or rendition logic - all image
 * concerns belong to the Custom Image component.</p>
 *
 * <p>It uses the <em>markup</em> delegation mode of {@link AbstractImageDelegatingModel}:
 * unlike {@link CustomImageDelegatingImpl} it wants the delegated component's whole output, not
 * individual values, so a re-typed resource rendered by {@code data-sly-resource} is
 * exactly the right tool.</p>
 */
@Model(
        adaptables = {SlingHttpServletRequest.class, Resource.class},
        adapters = ContentImageModel.class,
        resourceType = ContentImageDelegatingModelImpl.RESOURCE_TYPE,
        defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
public class ContentImageDelegatingModelImpl extends AbstractImageDelegatingModel
        implements ContentImageModel {

    public static final String RESOURCE_TYPE =
            "aemcloudproject/components/customcomponents/contentimage";

    @ScriptVariable
    private Component component;

    @SlingObject
    private Resource resource;

    @Self
    private SlingHttpServletRequest request;

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
            imageResource = delegateTo(resource, CustomImageImpl.RESOURCE_TYPE);
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
