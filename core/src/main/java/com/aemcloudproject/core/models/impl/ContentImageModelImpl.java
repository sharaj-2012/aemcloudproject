package com.aemcloudproject.core.models.impl;

import com.aemcloudproject.core.models.ContentImageModel;
import com.day.cq.wcm.api.components.Component;
import com.day.cq.wcm.api.components.ComponentManager;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ScriptVariable;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;

@Model(
        adaptables = Resource.class,
        adapters = ContentImageModel.class,
        resourceType = ContentImageModelImpl.RESOURCE_TYPE,
        defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
public class ContentImageModelImpl extends AbstractDelegatingImageModel implements ContentImageModel {

    public static final String RESOURCE_TYPE =
            "aemcloudproject/components/customcomponents/contentimage";

    @ScriptVariable
    private Component component;

    @ValueMapValue
    private String title;

    @ValueMapValue
    private String description;

    @ValueMapValue
    private String fileReference;

    @ValueMapValue(name = "alt")
    private String alt;

    /** Kept as a fallback for content authored before Core Image delegation. */
    @ValueMapValue(name = "altText")
    private String altText;

    @ValueMapValue
    private String imageUrl;

    @Self
    private Resource resource;

    private boolean hasImage;

    @PostConstruct
    protected void init() {
        hasImage = hasResolvableAsset(resource, resource.getResourceResolver());

        if (hasImage) {
            initImageResource(component,resource);
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
    public String getFileReference() {
        return fileReference;
    }

    @Override
    public String getAltText() {
        return alt != null ? alt : altText;
    }

    @Override
    public String getImageUrl() {
        return imageUrl;
    }

    @Override
    public Resource getImageResource() {
        return getWrappedImageResource();
    }

    @Override
    public boolean hasImage() {
        return hasImage;
    }
}
