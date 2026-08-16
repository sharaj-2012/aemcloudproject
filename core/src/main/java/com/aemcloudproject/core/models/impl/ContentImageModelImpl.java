package com.aemcloudproject.core.models.impl;

import com.aemcloudproject.core.models.ContentImageModel;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

@Model(
        adaptables = Resource.class,
        adapters = ContentImageModel.class,
        resourceType = ContentImageModelImpl.RESOURCE_TYPE,
        defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
public class ContentImageModelImpl implements ContentImageModel {

    public static final String RESOURCE_TYPE =
            "aemcloudproject/components/customcomponents/contentimage";

    @ValueMapValue
    private String title;

    @ValueMapValue
    private String description;

    @ValueMapValue
    private String fileReference;

    @ValueMapValue
    private String altText;

    @ValueMapValue
    private String imageUrl;

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
        return altText;
    }

    @Override
    public String getImageUrl() {
        return imageUrl;
    }
}
