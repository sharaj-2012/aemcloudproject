package com.aemcloudproject.core.services;

import org.apache.sling.api.resource.Resource;

/**
 * Reads the latest change to a JSON asset: its current content and its most recent version.
 */
public interface JsonAssetChangesService {

    /**
     * @param assetResource asset resource, resolved with the requesting user's resolver
     * @return current and previous content, or a failure status
     */
    JsonChanges getChanges(Resource assetResource);
}
