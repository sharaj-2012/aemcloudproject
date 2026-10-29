package com.aemcloudproject.it.tests;

import com.adobe.cq.testing.client.CQClient;
import com.adobe.cq.testing.junit.rules.CQAuthorClassRule;
import com.adobe.cq.testing.junit.rules.CQRule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.Header;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Exercises the JSON editor save servlet against a running author: saved, unchanged and invalid JSON.
 */
public class JsonAssetSaveIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ClassRule
    public static final CQAuthorClassRule cqBaseClassRule = new CQAuthorClassRule();

    @Rule
    public CQRule cqBaseRule = new CQRule(cqBaseClassRule.authorRule);

    static CQClient adminAuthor;

    private String folder;
    private String path;

    @BeforeClass
    public static void beforeClass() {
        adminAuthor = cqBaseClassRule.authorRule.getAdminClient(CQClient.class);
    }

    @Before
    public void createAsset() throws Exception {
        folder = "/content/dam/aemcloudproject/jsoneditor-it-" + UUID.randomUUID().toString().substring(0, 8);
        path = folder + "/sample.json";
        adminAuthor.createNode(folder, "sling:OrderedFolder");
        adminAuthor.createNode(path, "dam:Asset");
        adminAuthor.createNode(path + "/jcr:content", "dam:AssetContent");
        adminAuthor.createNode(path + "/jcr:content/metadata", "nt:unstructured");
        adminAuthor.createNode(path + "/jcr:content/renditions", "nt:folder");
        File original = File.createTempFile("sample", ".json");
        original.deleteOnExit();
        Files.write(original.toPath(), "{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        adminAuthor.upload(original, "application/json", path + "/jcr:content/renditions/original", false);
    }

    @After
    public void deleteAsset() throws Exception {
        adminAuthor.deletePath(folder);
    }

    private JsonNode post(String body, int expectedStatus) throws Exception {
        String token = MAPPER.readTree(adminAuthor.doGet("/libs/granite/csrf/token.json", 200).getContent()).get("token").asText();
        List<Header> headers = Collections.singletonList(new BasicHeader("CSRF-Token", token));
        StringEntity entity = new StringEntity(body, ContentType.APPLICATION_JSON);
        return MAPPER.readTree(adminAuthor.doPost(path + ".jsoneditor.json", entity, headers, expectedStatus).getContent());
    }

    @Test
    public void savesNewContentAndReturnsVersionLabel() throws Exception {
        JsonNode body = post("{\"a\":2}", 200);
        assertEquals("saved", body.get("status").asText());
        assertTrue(body.get("versionLabel").asText().startsWith("jsoneditor-"));
        assertEquals("{\"a\":2}", adminAuthor.doGet(path + "/jcr:content/renditions/original", 200).getContent());
    }

    @Test
    public void sameContentIsUnchanged() throws Exception {
        assertEquals("unchanged", post("{\"a\":1}", 200).get("status").asText());
    }

    @Test
    public void invalidJsonIs400WithPosition() throws Exception {
        JsonNode body = post("{\n  \"a\": 1,\n}", 400);
        assertEquals("invalid_json", body.get("status").asText());
        assertEquals(3, body.get("line").asInt());
    }
}
