package com.aemcloudproject.core.services.impl;

import com.aemcloudproject.core.services.JsonAssetSaveService;
import com.aemcloudproject.core.services.SaveResult;
import com.aemcloudproject.core.services.SaveStatus;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.Rendition;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
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
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Validates, versions and writes JSON asset content on the requesting user's session. Author only.
 */
@Component(service = JsonAssetSaveService.class, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class JsonAssetSaveServiceImpl implements JsonAssetSaveService {

    private static final Logger LOG = LoggerFactory.getLogger(JsonAssetSaveServiceImpl.class);
    private static final String DAM_ROOT = "/content/dam/";
    private static final String JSON_MIME = "application/json";
    private static final String ORIGINAL = "original";
    private static final String METADATA = "/jcr:content/metadata";
    private static final String LABEL_PREFIX = "jsoneditor-";
    private static final String VERSION_COMMENT = "Saved from JSON editor";
    private static final DateTimeFormatter LABEL_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    AssetPrivileges privileges = new AssetPrivileges();
    Clock clock = Clock.systemDefaultZone();
    JsonValidator validator = new JsonValidator();

    @Override
    public SaveResult save(Resource assetResource, byte[] content) {
        Asset asset = assetResource.getPath().startsWith(DAM_ROOT) ? assetResource.adaptTo(Asset.class) : null;
        if (asset == null) {
            return SaveResult.of(SaveStatus.NOT_DAM_ASSET);
        }
        if (!isJson(asset)) {
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
        if (Arrays.equals(bytes, currentContent(asset))) {
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
            asset.addRendition(ORIGINAL, new ByteArrayInputStream(bytes), JSON_MIME);
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

    private static boolean isJson(Asset asset) {
        return asset.getName().toLowerCase(Locale.ROOT).endsWith(".json") || JSON_MIME.equals(asset.getMimeType());
    }

    private static byte[] currentContent(Asset asset) {
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

    private static void updateMetadata(ResourceResolver resolver, String assetPath, byte[] bytes) {
        Resource metadata = resolver.getResource(assetPath + METADATA);
        ModifiableValueMap properties = metadata == null ? null : metadata.adaptTo(ModifiableValueMap.class);
        if (properties != null) {
            properties.put("dam:size", (long) bytes.length);
            properties.put("dam:sha1", DigestUtils.sha1Hex(bytes));
        }
    }
}
