package com.aemcloudproject.core.services.impl;

import com.aemcloudproject.core.services.SaveResult;
import com.aemcloudproject.core.services.SaveStatus;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.Rendition;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.wrappers.ModifiableValueMapDecorator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.jcr.AccessDeniedException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JsonAssetSaveServiceImplTest {

    private static final String DAM = "/content/dam/aemcloudproject/";
    private static final byte[] OLD = b("{\"a\":1}");
    private static final byte[] NEW = b("{\"a\":2}");

    @Mock
    private Resource resource;
    @Mock
    private ResourceResolver resolver;
    @Mock
    private Asset asset;
    @Mock
    private Rendition rendition;
    @Mock
    private Resource metadataResource;
    @Mock
    private AssetPrivileges privileges;

    private final ModifiableValueMap metadata = new ModifiableValueMapDecorator(new HashMap<>());
    private final JsonAssetSaveServiceImpl service = new JsonAssetSaveServiceImpl();

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] withBom(byte[] content) {
        byte[] result = new byte[content.length + 3];
        result[0] = (byte) 0xEF;
        result[1] = (byte) 0xBB;
        result[2] = (byte) 0xBF;
        System.arraycopy(content, 0, result, 3, content.length);
        return result;
    }

    @BeforeEach
    void setUp() {
        service.privileges = privileges;
        service.clock = Clock.fixed(Instant.parse("2026-10-29T10:15:30.123Z"), ZoneOffset.UTC);
        when(privileges.canEdit(any(), any())).thenReturn(true);
        when(resource.getResourceResolver()).thenReturn(resolver);
        when(resource.adaptTo(Asset.class)).thenReturn(asset);
        when(metadataResource.adaptTo(ModifiableValueMap.class)).thenReturn(metadata);
    }

    private SaveResult save(String path, String name, String mime, byte[] original, byte[] body) {
        when(resource.getPath()).thenReturn(path);
        when(asset.getPath()).thenReturn(path);
        when(asset.getName()).thenReturn(name);
        when(asset.getMimeType()).thenReturn(mime);
        when(resolver.getResource(path + "/jcr:content/metadata")).thenReturn(metadataResource);
        if (original == null) {
            when(asset.getOriginal()).thenReturn(null);
        } else {
            when(asset.getOriginal()).thenReturn(rendition);
            when(rendition.getStream()).thenAnswer(i -> new ByteArrayInputStream(original));
        }
        return service.save(resource, body);
    }

    private SaveResult save(byte[] original, byte[] body) {
        return save(DAM + "a.json", "a.json", "application/json", original, body);
    }

    @Test
    void outsideDamIsRejected() {
        assertEquals(SaveStatus.NOT_DAM_ASSET, save("/content/site/a.json", "a.json", "application/json", OLD, NEW).getStatus());
    }

    @Test
    void pngIsRejected() {
        assertEquals(SaveStatus.NOT_JSON, save(DAM + "a.png", "a.png", "image/png", OLD, NEW).getStatus());
    }

    @Test
    void jsonNameWithOctetStreamSaves() {
        assertEquals(SaveStatus.SAVED, save(DAM + "a.json", "a.json", "application/octet-stream", OLD, NEW).getStatus());
    }

    @Test
    void jsonMimeWithOtherNameSaves() {
        assertEquals(SaveStatus.SAVED, save(DAM + "a.txt", "a.txt", "application/json", OLD, NEW).getStatus());
    }

    @Test
    void invalidJsonWritesNothing() throws Exception {
        assertEquals(SaveStatus.INVALID_JSON, save(OLD, b("{,}")).getStatus());
        verify(asset, never()).createRevision(any(), any());
    }

    @Test
    void forbiddenWritesNothing() throws Exception {
        when(privileges.canEdit(any(), any())).thenReturn(false);
        assertEquals(SaveStatus.FORBIDDEN, save(OLD, NEW).getStatus());
        verify(asset, never()).createRevision(any(), any());
    }

    @Test
    void unchangedCreatesNoVersion() throws Exception {
        assertEquals(SaveStatus.UNCHANGED, save(OLD, OLD).getStatus());
        verify(asset, never()).createRevision(any(), any());
    }

    @Test
    void bomOnlyDifferenceIsUnchanged() {
        assertEquals(SaveStatus.UNCHANGED, save(withBom(OLD), OLD).getStatus());
    }

    @Test
    void missingOriginalStillSaves() {
        assertEquals(SaveStatus.SAVED, save(null, NEW).getStatus());
    }

    @Test
    void savesInOrderWithLabel() throws Exception {
        SaveResult r = save(OLD, NEW);
        assertEquals(SaveStatus.SAVED, r.getStatus());
        assertEquals("jsoneditor-20261029101530123", r.getVersionLabel());
        InOrder o = inOrder(asset, resolver);
        o.verify(asset).createRevision("jsoneditor-20261029101530123", "Saved from JSON editor");
        o.verify(asset).setBatchMode(true);
        o.verify(asset).addRendition(eq("original"), any(InputStream.class), eq("application/json"));
        o.verify(resolver).commit();
        assertEquals((long) NEW.length, metadata.get("dam:size"));
        assertEquals(DigestUtils.sha1Hex(NEW), metadata.get("dam:sha1"));
    }

    @Test
    void revisionFailureWritesNothing() throws Exception {
        doThrow(new Exception("x")).when(asset).createRevision(any(), any());
        assertEquals(SaveStatus.VERSION_FAILED, save(OLD, NEW).getStatus());
        verify(asset, never()).addRendition(anyString(), any(InputStream.class), anyString());
    }

    @Test
    void commitFailureReverts() throws Exception {
        doThrow(new PersistenceException("x")).when(resolver).commit();
        assertEquals(SaveStatus.SAVE_FAILED, save(OLD, NEW).getStatus());
        verify(resolver).revert();
    }

    @Test
    void accessDeniedOnCommitIsForbidden() throws Exception {
        doThrow(new PersistenceException("x", new AccessDeniedException("no"))).when(resolver).commit();
        assertEquals(SaveStatus.FORBIDDEN, save(OLD, NEW).getStatus());
        verify(resolver).revert();
    }
}
