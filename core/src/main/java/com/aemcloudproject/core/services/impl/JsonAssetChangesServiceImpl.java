package com.aemcloudproject.core.services.impl;

import com.aemcloudproject.core.services.JsonAssetChangesService;
import com.aemcloudproject.core.services.JsonChanges;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.Revision;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;

/**
 * Compares a JSON asset's current content with its most recent version, on the requesting user's session. Author only.
 */
@Component(service = JsonAssetChangesService.class, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class JsonAssetChangesServiceImpl implements JsonAssetChangesService {

    private static final Logger LOG = LoggerFactory.getLogger(JsonAssetChangesServiceImpl.class);

    @Override
    public JsonChanges getChanges(Resource assetResource) {
        Asset asset = JsonAssets.damAsset(assetResource);
        if (asset == null) {
            return JsonChanges.failed(JsonChanges.Status.NOT_DAM_ASSET);
        }
        if (!JsonAssets.isJson(asset)) {
            return JsonChanges.failed(JsonChanges.Status.NOT_JSON);
        }
        JsonChanges.Side current = new JsonChanges.Side(text(JsonAssets.readOriginal(asset)), null,
                asset.getLastModified() > 0 ? asset.getLastModified() : null, asset.getModifier());
        return JsonChanges.of(asset.getPath(), current, latestVersion(assetResource, asset));
    }

    private static JsonChanges.Side latestVersion(Resource assetResource, Asset asset) {
        Collection<Revision> revisions;
        try {
            revisions = asset.getRevisions(null);
        } catch (Exception e) {
            LOG.warn("JSON changes could not list versions of {}: {}", asset.getPath(), e.getMessage());
            return null;
        }
        Revision latest = revisions == null ? null : revisions.stream()
                .filter(revision -> revision.getCreated() != null)
                .max(Comparator.comparing(Revision::getCreated))
                .orElse(null);
        if (latest == null) {
            return null;
        }
        Resource original = assetResource.getResourceResolver().getResource(latest.getRenditionPath(JsonAssets.ORIGINAL));
        Calendar created = latest.getCreated();
        return new JsonChanges.Side(text(JsonAssets.readFile(original)), latest.getLabel(), created.getTimeInMillis(), null);
    }

    private static String text(byte[] bytes) {
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }
}
