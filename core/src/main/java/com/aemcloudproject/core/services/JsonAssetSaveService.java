package com.aemcloudproject.core.services;

import org.apache.sling.api.resource.Resource;

/**
 * Saves edited JSON back to a DAM asset's original rendition, versioning the previous content first.
 */
public interface JsonAssetSaveService {

    /**
     * @return largest request body, in bytes, the editor may save
     */
    long getMaxSizeBytes();

    /**
     * @param assetResource asset resource, resolved with the requesting user's resolver
     * @param content       new file content as sent by the editor
     * @return outcome of the save
     */
    SaveResult save(Resource assetResource, byte[] content);
}
