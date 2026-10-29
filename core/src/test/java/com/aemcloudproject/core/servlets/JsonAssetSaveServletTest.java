package com.aemcloudproject.core.servlets;

import com.aemcloudproject.core.services.JsonAssetSaveService;
import com.aemcloudproject.core.services.SaveResult;
import com.aemcloudproject.core.services.SaveStatus;
import com.aemcloudproject.core.testcontext.AppAemContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(AemContextExtension.class)
class JsonAssetSaveServletTest {

    private final AemContext context = AppAemContext.newAemContext();
    private final JsonAssetSaveService service = mock(JsonAssetSaveService.class);
    private JsonAssetSaveServlet servlet;

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @BeforeEach
    void setUp() {
        when(service.getMaxSizeBytes()).thenReturn(2097152L);
        context.registerService(JsonAssetSaveService.class, service);
        servlet = context.registerInjectActivateService(new JsonAssetSaveServlet());
        context.currentResource(context.create().resource("/content/dam/aemcloudproject/a.json"));
    }

    private MockSlingHttpServletResponse post(byte[] body) throws Exception {
        context.request().setMethod("POST");
        context.request().setContent(body);
        servlet.doPost(context.request(), context.response());
        return context.response();
    }

    private static JsonNode json(MockSlingHttpServletResponse response) throws Exception {
        return new ObjectMapper().readTree(response.getOutputAsString());
    }

    @Test
    void savedMapsTo200WithLabel() throws Exception {
        when(service.save(any(), any())).thenReturn(SaveResult.saved("jsoneditor-1"));
        MockSlingHttpServletResponse response = post(b("{\"a\":1}"));
        assertEquals(200, response.getStatus());
        assertEquals("application/json;charset=UTF-8", response.getContentType());
        JsonNode body = json(response);
        assertEquals("saved", body.get("status").asText());
        assertEquals("jsoneditor-1", body.get("versionLabel").asText());
        assertFalse(body.has("line"));
    }

    @Test
    void invalidJsonMapsTo400WithPosition() throws Exception {
        when(service.save(any(), any())).thenReturn(SaveResult.invalidJson("Unexpected character", 3, 1));
        MockSlingHttpServletResponse response = post(b("{,}"));
        assertEquals(400, response.getStatus());
        JsonNode body = json(response);
        assertEquals(3, body.get("line").asInt());
        assertEquals(1, body.get("column").asInt());
    }

    @Test
    void bodyOverLimitIs413AndServiceNotCalled() throws Exception {
        when(service.getMaxSizeBytes()).thenReturn(4L);
        MockSlingHttpServletResponse response = post(b("{\"a\":1}"));
        assertEquals(413, response.getStatus());
        assertEquals("too_large", json(response).get("status").asText());
        verify(service, never()).save(any(), any());
    }

    @Test
    void bodyAtLimitIsPassedThrough() throws Exception {
        when(service.getMaxSizeBytes()).thenReturn(7L);
        when(service.save(any(), any())).thenReturn(SaveResult.of(SaveStatus.UNCHANGED));
        post(b("{\"a\":1}"));
        verify(service).save(any(), eq(b("{\"a\":1}")));
    }

    @Test
    void multiByteBodyAtLimitIsPassedThrough() throws Exception {
        byte[] body = b("\"é\"");
        when(service.getMaxSizeBytes()).thenReturn((long) body.length);
        when(service.save(any(), any())).thenReturn(SaveResult.of(SaveStatus.UNCHANGED));
        post(body);
        verify(service).save(any(), eq(body));
    }
}
