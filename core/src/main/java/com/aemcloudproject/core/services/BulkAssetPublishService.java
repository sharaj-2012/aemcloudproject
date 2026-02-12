package com.aemcloudproject.core.services;

import org.apache.sling.api.resource.ResourceResolver;

import java.util.List;

public interface BulkAssetPublishService {
    void processAssets(List<String> assetPaths, ResourceResolver resourceResolver);
}
