package com.aemcloudproject.core.services.impl;

import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.Rendition;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Shared checks and reads for JSON assets in the DAM.
 */
final class JsonAssets {

    static final String JSON_MIME = "application/json";
    static final String ORIGINAL = "original";

    private static final String DAM_ROOT = "/content/dam/";

    private JsonAssets() {
    }

    /**
     * @param resource resource resolved with the requesting user's resolver
     * @return the asset when the resource is a DAM asset under /content/dam, otherwise null
     */
    static Asset damAsset(Resource resource) {
        return resource.getPath().startsWith(DAM_ROOT) ? resource.adaptTo(Asset.class) : null;
    }

    /**
     * @param asset DAM asset
     * @return true when the asset name ends in .json or its MIME type is application/json
     */
    static boolean isJson(Asset asset) {
        return asset.getName().toLowerCase(Locale.ROOT).endsWith(".json") || JSON_MIME.equals(asset.getMimeType());
    }

    /**
     * @param asset DAM asset
     * @return the original rendition's bytes without a UTF-8 byte order mark, or null when unavailable
     */
    static byte[] readOriginal(Asset asset) {
        Rendition original = asset.getOriginal();
        if (original == null) {
            return null;
        }
        try (InputStream in = original.getStream()) {
            return in == null ? null : JsonValidator.stripBom(IOUtils.toByteArray(in));
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Reads {@code jcr:content/jcr:data} directly so frozen files inside a version can be read too.
     *
     * @param file file resource, such as a rendition inside a version
     * @return the file's bytes without a UTF-8 byte order mark, or null when unavailable
     */
    static byte[] readFile(Resource file) {
        Resource content = file == null ? null : file.getChild(JcrConstants.JCR_CONTENT);
        InputStream stream = content == null ? null : content.getValueMap().get(JcrConstants.JCR_DATA, InputStream.class);
        if (stream == null) {
            return null;
        }
        try (InputStream in = stream) {
            return JsonValidator.stripBom(IOUtils.toByteArray(in));
        } catch (IOException e) {
            return null;
        }
    }
}
