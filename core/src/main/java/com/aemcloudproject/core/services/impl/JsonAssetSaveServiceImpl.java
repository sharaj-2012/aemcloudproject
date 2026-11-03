package com.aemcloudproject.core.services.impl;

import com.aemcloudproject.core.services.JsonAssetSaveService;
import com.aemcloudproject.core.services.SaveResult;
import com.aemcloudproject.core.services.SaveStatus;
import com.day.cq.dam.api.Asset;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.AccessDeniedException;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;

/**
 * Validates, versions and writes JSON asset content on the requesting user's session. Author only.
 */
@Component(service = JsonAssetSaveService.class, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class JsonAssetSaveServiceImpl implements JsonAssetSaveService {

    private static final Logger LOG = LoggerFactory.getLogger(JsonAssetSaveServiceImpl.class);
    private static final String METADATA = "/jcr:content/metadata";
    private static final String LABEL_PREFIX = "jsoneditor-";
    private static final String VERSION_COMMENT = "Saved from JSON editor";
    private static final DateTimeFormatter LABEL_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    AssetPrivileges privileges = new AssetPrivileges();
    Clock clock = Clock.systemDefaultZone();
    JsonValidator validator = new JsonValidator();

    @Override
    public SaveResult save(Resource assetResource, byte[] content) {
        Asset asset = JsonAssets.damAsset(assetResource);
        if (asset == null) {
            return SaveResult.of(SaveStatus.NOT_DAM_ASSET);
        }
        if (!JsonAssets.isJson(asset)) {
            return SaveResult.of(SaveStatus.NOT_JSON);
        }
        byte[] bytes = JsonValidator.stripBom(content);
        Optional<SaveResult> invalid = validator.validate(bytes);
        if (invalid.isPresent()) {
            return invalid.get();
        }
        ResourceResolver resolver = assetResource.getResourceResolver();
        if (!privileges.canEdit(resolver, asset.getPath())) {
            return SaveResult.of(SaveStatus.FORBIDDEN);
        }
        if (Arrays.equals(bytes, JsonAssets.readOriginal(asset))) {
            return SaveResult.of(SaveStatus.UNCHANGED);
        }
        String label = LABEL_PREFIX + LABEL_FORMAT.format(LocalDateTime.now(clock));
        try {
            asset.createRevision(label, VERSION_COMMENT);
        } catch (Exception e) {
            LOG.error("JSON editor could not version {}: {}", asset.getPath(), e.getMessage());
            return SaveResult.of(SaveStatus.VERSION_FAILED);
        }
        try {
            asset.setBatchMode(true);
            asset.addRendition(JsonAssets.ORIGINAL, new ByteArrayInputStream(bytes), JsonAssets.JSON_MIME);
            updateMetadata(resolver, asset.getPath(), bytes);
            resolver.commit();
        } catch (Exception e) {
            resolver.revert();
            LOG.error("JSON editor could not save {}: {}", asset.getPath(), e.getMessage());
            return ExceptionUtils.indexOfType(e, AccessDeniedException.class) >= 0
                    ? SaveResult.of(SaveStatus.FORBIDDEN)
                    : SaveResult.of(SaveStatus.SAVE_FAILED);
        }
        return SaveResult.saved(label);
    }

    private static void updateMetadata(ResourceResolver resolver, String assetPath, byte[] bytes) {
        Resource metadata = resolver.getResource(assetPath + METADATA);
        ModifiableValueMap properties = metadata == null ? null : metadata.adaptTo(ModifiableValueMap.class);
        if (properties != null) {
            properties.put("dam:size", (long) bytes.length);
            properties.put("dam:sha1", DigestUtils.sha1Hex(bytes));
        }
    }
}
