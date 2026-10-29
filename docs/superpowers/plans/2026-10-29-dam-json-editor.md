# DAM JSON Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an "Edit JSON" action to the classic Assets console. It opens an editor page with validation, a live tree preview and a side-by-side compare, and saves back to the same asset with a version.

**Architecture:** Front end: a Granite shell page plus a no-build clientlib. It vendors `vanilla-jsoneditor` (an ES module loaded with `import()`), `jsdiff` and `diff2html` (UMD bundles). Back end: one POST-only Sling servlet bound to `dam:Asset` (selector `jsoneditor`). It delegates to an author-only OSGi service that validates, checks privileges, creates a revision and writes the original rendition, all on the user's own session. Read-only mode uses the OOTB `privilege` render condition.

**Tech Stack:** AEMaaCS SDK `2025.5.21005.20250522T173058Z-250500`, Java 11, Jackson (from the SDK), JUnit 5 + AEM Mocks + Mockito (core), JUnit 4 + `aem-cloud-testing-clients` 1.2.6 (it.tests), Granite UI / Coral 3, vanilla-jsoneditor, jsdiff, diff2html.

**Spec:** `docs/superpowers/specs/2026-10-29-dam-json-editor-design.md`. Read it first; this plan refers to its sections as §n.

## Global Constraints

- No front-end build: do not touch `ui.frontend`. All JS and CSS are plain files under `ui.apps` clientlibs.
- Author only. The service uses `configurationPolicy = REQUIRE`, and its config exists only in `osgiconfig/config.author`.
- No service user, and no change to `myaemproject-serviceuser` or repoinit.
- All repository access uses the request's `ResourceResolver`. Never use `ResolverUtil.newResolver`.
- Max size default `2097152` bytes (OSGi `maxSizeBytes`).
- Version label: `"jsoneditor-" + yyyyMMddHHmmssSSS`. Version comment: `"Saved from JSON editor"`.
- Concurrent edits: last save wins (§8.1). No fingerprint or conflict code.
- Header shows the asset path only.
- No CDN. Vendored files are pinned, with licences and SHA-256 in `VENDOR.md`. Pick the latest stable release that is at least two weeks old.
- JS: our code never assigns `innerHTML`; use `textContent`. Only diff2html output goes into the Compare container.
- **Java style (user rule): concise Javadoc only, no inline comments.**
- Build only the modules you touch: `mvn -pl <module> …`. The root build pulls in `ui.frontend`.
- After each task, explain the flow and the inputs/outputs in the task's commit/PR notes (user rule).

## Review Focus

1. **A `.json` file whose stored MIME type is `application/octet-stream` or `text/plain`** must still save, because eligibility is by name **or** MIME. Test in Task 2.
2. **A file with a UTF-8 BOM, saved without edits,** must return `unchanged` and create no version. Test in Task 2.
3. **An asset with no original rendition** (`getOriginal()` returns `null`) must save without an NPE. Test in Task 2.
4. **Top-level scalar JSON** (`42`, `"text"`, `null`) is valid JSON and must be accepted. Test in Task 1.
5. **Whitespace-only body** must be rejected as invalid JSON, not saved as an empty file. Jackson returns a `MissingNode` here without throwing. Test in Task 1.

---

### Task 1: JSON validation and result types

**Files:**
- Create: `core/src/main/java/com/aemcloudproject/core/services/SaveStatus.java`
- Create: `core/src/main/java/com/aemcloudproject/core/services/SaveResult.java`
- Create: `core/src/main/java/com/aemcloudproject/core/services/impl/JsonValidator.java`
- Test: `core/src/test/java/com/aemcloudproject/core/services/impl/JsonValidatorTest.java`

**Interfaces:**
- Produces:
  - `enum SaveStatus { SAVED(200,"saved"), UNCHANGED(200,"unchanged"), NOT_DAM_ASSET(400,"not_dam_asset"), INVALID_JSON(400,"invalid_json"), FORBIDDEN(403,"forbidden"), TOO_LARGE(413,"too_large"), NOT_JSON(415,"not_json"), VERSION_FAILED(500,"version_failed"), SAVE_FAILED(500,"save_failed"); int httpStatus(); String code(); }`
  - `final class SaveResult` with getters `getStatus(): SaveStatus`, `getMessage(): String`, `getLine(): Integer`, `getColumn(): Integer`, `getVersionLabel(): String`, and factories:
    - `of(SaveStatus)`: uses the default message
    - `invalidJson(String message, Integer line, Integer column)`
    - `tooLarge(long maxBytes)`: message `"File exceeds the size limit of " + maxBytes + " bytes."`
    - `saved(String versionLabel)`
  - Default messages (fixed copy):

    | Status | Message |
    |---|---|
    | SAVED | `"Saved."` |
    | UNCHANGED | `"Nothing to save."` |
    | NOT_DAM_ASSET | `"Not a DAM asset."` |
    | NOT_JSON | `"Only JSON assets can be edited."` |
    | FORBIDDEN | `"You do not have permission to modify this asset."` |
    | VERSION_FAILED | `"Could not create a version; nothing was saved."` |
    | SAVE_FAILED | `"Save failed; nothing was changed."` |
  - `final class JsonValidator { static byte[] stripBom(byte[] content); Optional<SaveResult> validate(byte[] content); }`. It returns empty when valid; otherwise an `INVALID_JSON` result.

- [ ] **Step 1: Write the failing tests**

```java
class JsonValidatorTest {
    private final JsonValidator validator = new JsonValidator();
    private static byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    @Test void acceptsObject()            { assertTrue(validator.validate(b("{\"a\":1}")).isEmpty()); }
    @Test void acceptsTopLevelScalars()   { for (String s : new String[]{"42", "\"text\"", "null"}) assertTrue(validator.validate(b(s)).isEmpty(), s); }
    @Test void trailingCommaReportsLineAndColumn() {
        SaveResult r = validator.validate(b("{\n  \"a\": 1,\n}")).orElseThrow();
        assertEquals(SaveStatus.INVALID_JSON, r.getStatus());
        assertEquals(3, r.getLine());
        assertEquals(1, r.getColumn());
        assertNotNull(r.getMessage());
    }
    @Test void rejectsTrailingContent()   { assertTrue(validator.validate(b("{} {}")).isPresent()); }
    @Test void rejectsDuplicateKeys()     { assertTrue(validator.validate(b("{\"a\":1,\"a\":2}")).orElseThrow().getMessage().contains("a")); }
    @Test void rejectsEmptyAndWhitespace(){ assertTrue(validator.validate(b("")).isPresent()); assertTrue(validator.validate(b("  \n ")).isPresent()); }
    @Test void rejectsMalformedUtf8()     { assertEquals("File is not valid UTF-8.", validator.validate(new byte[]{'"', (byte) 0xC3, '"'}).orElseThrow().getMessage()); }
    @Test void stripBomRemovesLeadingBom(){ assertArrayEquals(b("{}"), JsonValidator.stripBom(new byte[]{(byte)0xEF,(byte)0xBB,(byte)0xBF,'{','}'})); }
    @Test void stripBomKeepsOtherContent(){ assertArrayEquals(b("{}"), JsonValidator.stripBom(b("{}"))); }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -pl core test -Dtest=JsonValidatorTest`
Expected: compilation failure (`JsonValidator` not found).

- [ ] **Step 3: Implement `SaveStatus`, `SaveResult` and `JsonValidator`**

`JsonValidator` details:
- Decode strictly first, with a `CharsetDecoder` set to `CodingErrorAction.REPORT`.
- Mapper: `new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)`.
- A `MissingNode` result means an empty document: return message `"Empty document."` with no line or column.
- On `JsonProcessingException`, use `getOriginalMessage()`, `getLocation().getLineNr()` and `getLocation().getColumnNr()`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -pl core test -Dtest=JsonValidatorTest`
Expected: `Tests run: 9, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/aemcloudproject/core/services/SaveStatus.java core/src/main/java/com/aemcloudproject/core/services/SaveResult.java core/src/main/java/com/aemcloudproject/core/services/impl/JsonValidator.java core/src/test/java/com/aemcloudproject/core/services/impl/JsonValidatorTest.java
git commit -m "feat(jsoneditor): add JSON validation and save result types"
```

---

### Task 2: Save service

**Files:**
- Create: `core/src/main/java/com/aemcloudproject/core/config/JsonAssetSaveConfiguration.java`
- Create: `core/src/main/java/com/aemcloudproject/core/services/JsonAssetSaveService.java`
- Create: `core/src/main/java/com/aemcloudproject/core/services/impl/JsonAssetSaveServiceImpl.java`
- Create: `core/src/main/java/com/aemcloudproject/core/services/impl/AssetPrivileges.java`
- Test: `core/src/test/java/com/aemcloudproject/core/services/impl/JsonAssetSaveServiceImplTest.java`

**Interfaces:**
- Consumes: `SaveResult`, `SaveStatus` and `JsonValidator` (Task 1).
- Produces:
  - `@ObjectClassDefinition(name = "AEM Cloud Project - JSON Asset Save") @interface JsonAssetSaveConfiguration { long maxSizeBytes() default 2097152; }`
  - `interface JsonAssetSaveService { long getMaxSizeBytes(); SaveResult save(Resource assetResource, byte[] content); }`
  - `JsonAssetSaveServiceImpl`: `@Component(service = JsonAssetSaveService.class, configurationPolicy = ConfigurationPolicy.REQUIRE)`, `@Designate(ocd = JsonAssetSaveConfiguration.class)`. Package-private fields for tests: `AssetPrivileges privileges = new AssetPrivileges()`, `Clock clock = Clock.systemDefaultZone()`, `JsonValidator validator = new JsonValidator()`.
  - `class AssetPrivileges { boolean canEdit(ResourceResolver resolver, String assetPath); }` (package-private). Through `AccessControlManager.hasPrivileges`, it requires:
    - `rep:write` on the asset and on whichever of `jcr:content`, `jcr:content/metadata` and `jcr:content/renditions/original` exist;
    - `jcr:versionManagement` on the asset.
    - Any `RepositoryException` → `false`.

`save` runs §7 in order:
1. **1a** – the path must start with `/content/dam/` and the resource must adapt to `Asset`, else `NOT_DAM_ASSET`.
2. **1b** – the name must end in `.json` (case-insensitive) **or** `getMimeType()` must equal `application/json`, else `NOT_JSON`.
3. **3** – `validator.validate(JsonValidator.stripBom(content))`.
4. **4** – `privileges.canEdit(...)`, else `FORBIDDEN`.
5. **5** – compare with the BOM-stripped bytes of the current original (`getOriginal()` may be `null` → treated as different). Equal → `UNCHANGED`.
6. **6** – `asset.createRevision(label, "Saved from JSON editor")`. Any exception → `VERSION_FAILED`.
7. **7** – `asset.setBatchMode(true)`; `asset.addRendition("original", new ByteArrayInputStream(bytes), "application/json")`; on `<asset>/jcr:content/metadata`'s `ModifiableValueMap`, set `dam:size` (Long) and `dam:sha1` (`DigestUtils.sha1Hex`).
8. **8** – `resolver.commit()`.
   - Any exception in steps 7–8 → `resolver.revert()`.
   - If the cause chain contains `javax.jcr.AccessDeniedException` → `FORBIDDEN`, else `SAVE_FAILED`.
   - Log `error(path, reason)` without content.
9. → `SaveResult.saved(label)`.

The size check (§7 step 2) lives in the servlet (Task 3), because the body is capped while it is read.

- [ ] **Step 1: Write the failing tests**

Test fixture (pure Mockito, `@ExtendWith(MockitoExtension.class)`, lenient stubs):
- `fixture(String path, String name, String mime, byte[] original)` builds a mock `Resource`, `ResourceResolver`, `Asset`, `Rendition` and a metadata `ModifiableValueMap`.
- `service.privileges` is a mock returning `true` by default.
- `service.clock = Clock.fixed(Instant.parse("2026-10-29T10:15:30.123Z"), ZoneOffset.UTC)`.

```java
@Test void outsideDamIsRejected()          { assertEquals(SaveStatus.NOT_DAM_ASSET, save("/content/site/a.json", "a.json", "application/json", OLD, NEW).getStatus()); }
@Test void pngIsRejected()                 { assertEquals(SaveStatus.NOT_JSON, save(DAM + "a.png", "a.png", "image/png", OLD, NEW).getStatus()); }
@Test void jsonNameWithOctetStreamSaves()  { assertEquals(SaveStatus.SAVED, save(DAM + "a.json", "a.json", "application/octet-stream", OLD, NEW).getStatus()); }   // Review Focus 1
@Test void jsonMimeWithOtherNameSaves()    { assertEquals(SaveStatus.SAVED, save(DAM + "a.txt", "a.txt", "application/json", OLD, NEW).getStatus()); }
@Test void invalidJsonWritesNothing()      { assertEquals(SaveStatus.INVALID_JSON, save(DAM_JSON, OLD, b("{,}")).getStatus()); verify(asset, never()).createRevision(any(), any()); }
@Test void forbiddenWritesNothing()        { when(privileges.canEdit(any(), any())).thenReturn(false); assertEquals(SaveStatus.FORBIDDEN, save(DAM_JSON, OLD, NEW).getStatus()); verify(asset, never()).createRevision(any(), any()); }
@Test void unchangedCreatesNoVersion()     { assertEquals(SaveStatus.UNCHANGED, save(DAM_JSON, OLD, OLD).getStatus()); verify(asset, never()).createRevision(any(), any()); }
@Test void bomOnlyDifferenceIsUnchanged()  { assertEquals(SaveStatus.UNCHANGED, save(DAM_JSON, withBom(OLD), OLD).getStatus()); }                                   // Review Focus 2
@Test void missingOriginalStillSaves()     { assertEquals(SaveStatus.SAVED, save(DAM_JSON, null, NEW).getStatus()); }                                               // Review Focus 3
@Test void savesInOrderWithLabel() throws Exception {
    SaveResult r = save(DAM_JSON, OLD, NEW);
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
@Test void revisionFailureWritesNothing() throws Exception { doThrow(new Exception("x")).when(asset).createRevision(any(), any()); assertEquals(SaveStatus.VERSION_FAILED, save(DAM_JSON, OLD, NEW).getStatus()); verify(asset, never()).addRendition(anyString(), any(InputStream.class), anyString()); }
@Test void commitFailureReverts() throws Exception { doThrow(new PersistenceException("x")).when(resolver).commit(); assertEquals(SaveStatus.SAVE_FAILED, save(DAM_JSON, OLD, NEW).getStatus()); verify(resolver).revert(); }
@Test void accessDeniedOnCommitIsForbidden() throws Exception { doThrow(new PersistenceException("x", new AccessDeniedException("no"))).when(resolver).commit(); assertEquals(SaveStatus.FORBIDDEN, save(DAM_JSON, OLD, NEW).getStatus()); verify(resolver).revert(); }
```
`DAM = "/content/dam/aemcloudproject/"`, `DAM_JSON` is shorthand for `DAM + "a.json", "a.json", "application/json"`, `OLD = b("{\"a\":1}")`, `NEW = b("{\"a\":2}")`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -pl core test -Dtest=JsonAssetSaveServiceImplTest`
Expected: compilation failure (`JsonAssetSaveServiceImpl` not found).

- [ ] **Step 3: Implement the configuration, interface, `AssetPrivileges` and `JsonAssetSaveServiceImpl`** as specified in the Interfaces block. Use `@Activate`/`@Modified` to read `maxSizeBytes`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -pl core test -Dtest=JsonAssetSaveServiceImplTest`
Expected: `Tests run: 13, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/aemcloudproject/core/config/JsonAssetSaveConfiguration.java core/src/main/java/com/aemcloudproject/core/services/JsonAssetSaveService.java core/src/main/java/com/aemcloudproject/core/services/impl/ core/src/test/java/com/aemcloudproject/core/services/impl/JsonAssetSaveServiceImplTest.java
git commit -m "feat(jsoneditor): add author-only JSON asset save service"
```

---

### Task 3: Save servlet and author config

**Files:**
- Create: `core/src/main/java/com/aemcloudproject/core/servlets/JsonAssetSaveServlet.java`
- Create: `ui.config/src/main/content/jcr_root/apps/aemcloudproject/osgiconfig/config.author/com.aemcloudproject.core.services.impl.JsonAssetSaveServiceImpl.cfg.json` → `{ "maxSizeBytes:Long": 2097152 }`
- Test: `core/src/test/java/com/aemcloudproject/core/servlets/JsonAssetSaveServletTest.java`

**Interfaces:**
- Consumes: `JsonAssetSaveService.getMaxSizeBytes()` and `save(Resource, byte[])` (Task 2), and `SaveResult.tooLarge(long)` (Task 1).
- Produces:
  - HTTP `POST <assetPath>.jsoneditor.json`.
  - Response body: `{"status": code, "message": …, "line"?: n, "column"?: n, "versionLabel"?: …}`. Null fields are omitted.
  - HTTP status: `SaveStatus.httpStatus()`.
  - `Content-Type: application/json;charset=UTF-8`.
  - Servlet registration: `@Component(service = Servlet.class)` and `@SlingServletResourceTypes(resourceTypes = "dam:Asset", selectors = "jsoneditor", extensions = MyProjectConstants.JSON, methods = HttpConstants.METHOD_POST)`. The `@Reference` to `JsonAssetSaveService` is mandatory.
  - Size handling: if `getContentLengthLong() > max`, return `tooLarge` without reading the body. Otherwise read through `BoundedInputStream(in, max + 1)`; if the length is greater than `max`, return `tooLarge`.

- [ ] **Step 1: Write the failing tests** (`AppAemContext.newAemContext()`, Mockito mock service registered with `context.registerService`, servlet created with `context.registerInjectActivateService(new JsonAssetSaveServlet())`)

```java
@Test void savedMapsTo200WithLabel() throws Exception {
    when(service.save(any(), any())).thenReturn(SaveResult.saved("jsoneditor-1"));
    post(b("{\"a\":1}"));
    assertEquals(200, response.getStatus());
    assertEquals("application/json;charset=UTF-8", response.getContentType());
    JsonNode body = new ObjectMapper().readTree(response.getOutputAsString());
    assertEquals("saved", body.get("status").asText());
    assertEquals("jsoneditor-1", body.get("versionLabel").asText());
    assertFalse(body.has("line"));
}
@Test void invalidJsonMapsTo400WithPosition() throws Exception {
    when(service.save(any(), any())).thenReturn(SaveResult.invalidJson("Unexpected character", 3, 1));
    post(b("{,}"));
    assertEquals(400, response.getStatus());
    JsonNode body = new ObjectMapper().readTree(response.getOutputAsString());
    assertEquals(3, body.get("line").asInt());
    assertEquals(1, body.get("column").asInt());
}
@Test void bodyOverLimitIs413AndServiceNotCalled() throws Exception {
    when(service.getMaxSizeBytes()).thenReturn(4L);
    post(b("{\"a\":1}"));
    assertEquals(413, response.getStatus());
    verify(service, never()).save(any(), any());
}
@Test void bodyAtLimitIsPassedThrough() throws Exception {
    when(service.getMaxSizeBytes()).thenReturn(7L);
    when(service.save(any(), any())).thenReturn(SaveResult.of(SaveStatus.UNCHANGED));
    post(b("{\"a\":1}"));
    verify(service).save(any(), eq(b("{\"a\":1}")));
}
@Test void multiByteBodyAtLimitIsPassedThrough() throws Exception {
    byte[] body = b("\"é\"");
    when(service.getMaxSizeBytes()).thenReturn((long) body.length);
    when(service.save(any(), any())).thenReturn(SaveResult.of(SaveStatus.UNCHANGED));
    post(body);
    verify(service).save(any(), eq(body));
}
```
`post(bytes)` sets `context.request().setMethod("POST")`, `setContent(bytes)`, and the current resource to a resource created at `/content/dam/aemcloudproject/a.json`. `service.getMaxSizeBytes()` defaults to `2097152`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -pl core test -Dtest=JsonAssetSaveServletTest`
Expected: compilation failure (`JsonAssetSaveServlet` not found).

- [ ] **Step 3: Implement `JsonAssetSaveServlet`** (extends `SlingAllMethodsServlet`, overrides `doPost`; serialise a `LinkedHashMap` with Jackson). Add the OSGi config file.

- [ ] **Step 4: Run all core tests**

Run: `mvn -pl core clean test`
Expected: `BUILD SUCCESS`; the new test classes report 0 failures.

- [ ] **Step 5: Deploy and smoke-test on the local SDK**

Run: `mvn -pl core clean install -PautoInstallBundle` then `mvn -pl ui.config clean install -PautoInstallPackage`
Check:
- In `/system/console/components`, `JsonAssetSaveServiceImpl` and `JsonAssetSaveServlet` are **active**.
- Upload any `.json` to `/content/dam/aemcloudproject/jsontest/`. With a CSRF token, `curl -u admin:admin -H "CSRF-Token: $(curl -s -u admin:admin http://localhost:4502/libs/granite/csrf/token.json | sed 's/.*"token":"\([^"]*\)".*/\1/')" -H "Content-Type: application/json" --data-binary '{"ok":true}' http://localhost:4502/content/dam/aemcloudproject/jsontest/<file>.json.jsoneditor.json` returns `{"status":"saved",…}`.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/aemcloudproject/core/servlets/JsonAssetSaveServlet.java core/src/test/java/com/aemcloudproject/core/servlets/JsonAssetSaveServletTest.java ui.config/src/main/content/jcr_root/apps/aemcloudproject/osgiconfig/config.author/com.aemcloudproject.core.services.impl.JsonAssetSaveServiceImpl.cfg.json
git commit -m "feat(jsoneditor): add POST save servlet and author-only config"
```

---

### Task 4: Vendored libraries and editor clientlib skeleton

**Files (all under `ui.apps/src/main/content/jcr_root/apps/aemcloudproject/clientlibs/jsoneditor/`):**
- Create:
  - `.content.xml`: `jcr:primaryType="cq:ClientLibraryFolder"`, `categories="[aemcloudproject.jsoneditor]"`, `allowProxy="{Boolean}true"`, `jsProcessor="[default:none,min:none]"`, `cssProcessor="[default:none,min:none]"`
  - `js.txt`: `vendor/jsdiff-<v>/diff.min.js`, `vendor/diff2html-<v>/diff2html-ui-slim.min.js`, `js/editor.js`
  - `css.txt`: `vendor/diff2html-<v>/diff2html.min.css`, `css/editor.css`
  - `js/editor.js`: empty placeholder, filled in Task 5
  - `css/editor.css`: empty placeholder, filled in Task 5
  - `vendor/jsdiff-<v>/` and `vendor/diff2html-<v>/`, each with its files and `LICENSE`
  - `resources/vendor/vanilla-jsoneditor-<v>/standalone.js` and `LICENSE.md`
  - `VENDOR.md`: one table row per file: name, version, npm tarball URL, licence, SHA-256

**Interfaces:**
- Produces:
  - globals `window.Diff` (jsdiff) and `window.Diff2HtmlUI` (diff2html slim UI);
  - the ES module URL `/etc.clientlibs/aemcloudproject/clientlibs/jsoneditor/resources/vendor/vanilla-jsoneditor-<v>/standalone.js`, which exports `createJSONEditor`;
  - the category `aemcloudproject.jsoneditor`.

- [ ] **Step 1: Fetch the pinned packages into the scratchpad**, not into the repo root. Run `npm pack vanilla-jsoneditor@<v> diff@<v> diff2html@<v>` in an empty scratch directory and extract each tarball. Record the exact versions.

- [ ] **Step 2: Copy only the needed files**
  - `vanilla-jsoneditor/standalone.js` and `LICENSE.md`
  - `diff/dist/diff.min.js` and `LICENSE`
  - `diff2html/bundles/js/diff2html-ui-slim.min.js`, `diff2html/bundles/css/diff2html.min.css` and `LICENSE.md`

- [ ] **Step 3: Write `VENDOR.md`**, with SHA-256 from `shasum -a 256 <file>` for each copied file.

- [ ] **Step 4: Add the filter entry.** In `ui.apps/src/main/content/META-INF/vault/filter.xml`, `/apps/aemcloudproject/clientlibs` is already covered, so no change is needed. Confirm this.

- [ ] **Step 5: Deploy and verify delivery** (§11 items 3 and 4)

Run: `mvn -pl ui.apps clean install -PautoInstallPackage`
Check:
- `curl -sI -u admin:admin http://localhost:4502/etc.clientlibs/aemcloudproject/clientlibs/jsoneditor/resources/vendor/vanilla-jsoneditor-<v>/standalone.js` → `200` and `Content-Type: application/javascript` (or `text/javascript`).
- `curl -s -u admin:admin http://localhost:4502/etc.clientlibs/aemcloudproject/clientlibs/jsoneditor.js | head -c 200` begins with jsdiff's banner, not minified by AEM.
- `grep -c "insertCss\|<style\|document.head" standalone.js` shows whether the module injects its own CSS. Record the answer in `VENDOR.md`. If it doesn't inject CSS, also vendor its stylesheet and add it to `css.txt`.

- [ ] **Step 6: Commit**

```bash
git add ui.apps/src/main/content/jcr_root/apps/aemcloudproject/clientlibs/jsoneditor
git commit -m "feat(jsoneditor): vendor vanilla-jsoneditor, jsdiff and diff2html clientlib"
```

---

### Task 5: Editor page and editor behaviour

**Files:**
- Create: `ui.apps/src/main/content/jcr_root/apps/aemcloudproject/gui/content/jsoneditor/.content.xml`
- Create: `ui.apps/src/main/content/jcr_root/apps/aemcloudproject/gui/components/jsoneditor/.content.xml` and `jsoneditor.html`
- Modify: `ui.apps/src/main/content/jcr_root/apps/aemcloudproject/clientlibs/jsoneditor/js/editor.js` and `css/editor.css`
- Modify: `ui.apps/src/main/content/META-INF/vault/filter.xml` → add `<filter root="/apps/aemcloudproject/gui"/>`

**Interfaces:**
- Consumes: Task 3's POST contract and Task 4's globals and module URL.
- Produces: the page URL `/mnt/overlay/aemcloudproject/gui/content/jsoneditor.html<assetPath>`, which Task 6 links to.

**Page structure (`gui/content/jsoneditor/.content.xml`):** `sling:resourceType="granite/ui/components/shell/page"`, `jcr:title="Edit JSON"`, with these children:
- `head/clientlibs`: `granite/ui/components/coral/foundation/includeclientlibs`, `categories="[coralui3,granite.ui.coral.foundation,aemcloudproject.jsoneditor]"`
- `title`: `jcr:title="Edit JSON"`
- `actions/primary/save`: `granite/ui/components/coral/foundation/button`, `text="Save"`, `variant="primary"`, `granite:id="jsoneditor-save"`. Its child `granite:rendercondition` uses `granite/ui/components/coral/foundation/renderconditions/privilege` with `path="${requestPathInfo.suffix}"` and `privileges="[rep:write]"`.
- `actions/secondary/compare`: button `text="Compare"`, `granite:id="jsoneditor-compare"`
- `actions/secondary/format`: button `text="Format"`, `granite:id="jsoneditor-format"`
- `actions/secondary/close`: button `text="Close"`, `granite:id="jsoneditor-close"`
- `content/items/readonly`: `granite/ui/components/coral/foundation/alert`, `variant="info"`, `text="You can view this file but not change it."`, `granite:id="jsoneditor-readonly"`. Its rendercondition is `renderconditions/not` wrapping the same privilege condition.
- `content/items/editor`: `sling:resourceType="aemcloudproject/gui/components/jsoneditor"`

**`jsoneditor.html` (HTL markup only):** `<p id="jsoneditor-path">`, `<div id="jsoneditor-error" role="alert">`, `<div class="jsoneditor-panes"><div id="jsoneditor-left"></div><div id="jsoneditor-right"><p id="jsoneditor-stale" hidden>Preview out of date — fix errors</p></div></div>`, `<div id="jsoneditor-compare-view" hidden><label><input type="checkbox" id="jsoneditor-normalize"> Normalize formatting</label><div id="jsoneditor-diff"></div></div>`.

**`editor.js` contract** (an IIFE with no globals except the constants below):

| Function | Behaviour |
|---|---|
| `assetPath()` | `decodeURIComponent(location.pathname.split('/jsoneditor.html')[1])` |
| `load()` | `fetch(assetPath(), {cache:'no-store', credentials:'same-origin'})`. Over `MAX_BYTES = 2097152` (header or text length) → error `"File too large to edit"`. A non-OK response → `"Could not load file (HTTP <status>)"`. Strip a leading `﻿`. Sets `baseline`. |
| `init()` | Writes the path with `textContent`. Runs `load()`, then `import(EDITOR_MODULE_URL)`. Left editor: `createJSONEditor({target, props:{content:{text:baseline}, mode:'text', mainMenuBar:false, navigationBar:false, statusBar:true, onChange}})`. Right editor: the same with `mode:'tree', readOnly:true`. |
| `onChange(content, prev, {contentErrors})` | With a parse error: show `"Line " + line + ", column " + column + ": " + message` (convert zero-based positions if the library reports them that way, verified in Step 4), disable Save, unhide the stale note. Otherwise: hide the error and stale note, enable Save when `text !== baseline`, and debounce (300 ms) `preview.set({text})`. |
| `reindent(text)` | Pretty-prints with 2 spaces **without parsing numbers**, so every token is kept exactly (algorithm below). |
| `format()` | `left.set({text: reindent(current)})`; the editor's undo reverts it. |
| `renderCompare()` | `a, b = baseline, current`; with Normalize checked, `reindent` both. `patch = Diff.createTwoFilesPatch('original','modified',a,b,'','',{context:3, timeout:3000, maxEditLength:20000})`. If `patch` is `undefined`, show `"Too many differences to display"`. Otherwise `new Diff2HtmlUI(diffEl, patch, {outputFormat:'side-by-side', drawFileList:false, matching:'words', diffMaxChanges:5000, diffMaxLineLength:10000, matchingMaxComparisons:2500, highlight:false}).draw()`. |
| `save()` | Disable Save; `token = (await (await fetch('/libs/granite/csrf/token.json',{credentials:'same-origin'})).json()).token`; `fetch(assetPath() + '.jsoneditor.json', {method:'POST', credentials:'same-origin', headers:{'Content-Type':'application/json','CSRF-Token':token}, body:submitted})`. On `saved`: Coral toast `"Saved — version " + versionLabel`, `baseline = submitted`. On `unchanged`: toast `"Nothing to save."`. Otherwise show the `message`, plus line and column when present. Never change the editor text. Re-enable Save in `finally`. |
| `close()` | `location.href = '/assets.html' + assetPath().substring(0, assetPath().lastIndexOf('/'))`; the `beforeunload` guard still applies. |
| `beforeunload` | Prevent default while `current !== baseline`. |

`reindent` algorithm: walk the characters, tracking `inString` and `escaped`. Inside strings, copy every character. Outside strings, drop whitespace and handle:
- `{` or `[`: if the next non-whitespace character is the matching closer, emit the pair and skip ahead. Otherwise emit the opener, a newline and the indentation, and add one level.
- `}` or `]`: remove one level, then emit a newline, the indentation and the closer.
- `,`: emit `,`, a newline and the indentation.
- `:`: emit `": "`.
- Anything else: copy it.

**`editor.css`:** two 50/50 panes, each `height: calc(100vh - 220px)`, stacked below 900 px wide. The error box is red text. `#jsoneditor-diff` scrolls horizontally.

- [ ] **Step 1: Add the filter entry, page node, component and CSS**
- [ ] **Step 2: Implement `editor.js` to the contract above**
- [ ] **Step 3: Deploy**: `mvn -pl ui.apps clean install -PautoInstallPackage`
- [ ] **Step 4: Verify by hand on the SDK** (§10.3 steps 3–9 and 11–14), opening `http://localhost:4502/mnt/overlay/aemcloudproject/gui/content/jsoneditor.html/content/dam/aemcloudproject/jsontest/sample.json` directly. Check §11 item 2 (the privilege render condition with a suffix path), and confirm whether `parseError.line`/`column` are zero-based by deleting a comma on line 3. Also confirm that `createTwoFilesPatch` returns `undefined` when a limit is hit: replace a 2 MB file's whole content and expect "Too many differences to display". Expected: every listed manual step behaves as the spec describes, and the browser console shows no errors.
- [ ] **Step 5: Commit**

```bash
git add ui.apps/src/main/content/jcr_root/apps/aemcloudproject/gui ui.apps/src/main/content/jcr_root/apps/aemcloudproject/clientlibs/jsoneditor/js ui.apps/src/main/content/jcr_root/apps/aemcloudproject/clientlibs/jsoneditor/css ui.apps/src/main/content/META-INF/vault/filter.xml
git commit -m "feat(jsoneditor): add editor page with validation, preview, compare and save"
```

---

### Task 6: "Edit JSON" toolbar action

**Files:**
- Create: `ui.apps/src/main/content/jcr_root/apps/dam/gui/content/assets/.content.xml`, `_jcr_content/.content.xml`, `_jcr_content/actions/.content.xml`, `_jcr_content/actions/selection/.content.xml`. Each is only `jcr:primaryType="nt:unstructured"`. They are ancestors outside the filter, used only if missing.
- Create: `ui.apps/src/main/content/jcr_root/apps/dam/gui/content/assets/_jcr_content/actions/selection/editjson/.content.xml`
- Create: `ui.apps/src/main/content/jcr_root/apps/aemcloudproject/clientlibs/jsoneditor-action/.content.xml` (`categories="[dam.gui.actions.coral]"`), `js.txt`, `js/eligible.js`
- Modify: `ui.apps/src/main/content/META-INF/vault/filter.xml` → add `<filter root="/apps/dam/gui/content/assets/jcr:content/actions/selection/editjson"/>`

**Interfaces:**
- Consumes: the Task 5 page URL.
- Produces: the activecondition name `aemcloudproject.jsoneditor.eligible`.

**`editjson` node:**
- `sling:resourceType="granite/ui/components/coral/foundation/collection/action"`
- `text="Edit JSON"`, `icon="code"`
- `action="foundation.link"`
- `activeSelectionCount="single"`
- `target=".cq-damadmin-admin-childpages"`
- `activeCondition="aemcloudproject.jsoneditor.eligible"`
- `relScope="none"`
- `variant="actionBar"`
- child `data`: `href.uritemplate="/mnt/overlay/aemcloudproject/gui/content/jsoneditor.html{+item}"`

**`eligible.js`:** registers through `$(window).adaptTo('foundation-registry').register('foundation.collection.action.activecondition', {name:'aemcloudproject.jsoneditor.eligible', handler(name, el, config, collection, selections)})`. It returns `true` iff:
- `selections.length === 1`,
- the item is not a folder (use the attribute recorded in Step 2), and
- `selections[0].dataset.foundationCollectionItemId` ends in `.json` (case-insensitive).

- [ ] **Step 1: Add the ancestor stubs, action node, filter entry and clientlib**
- [ ] **Step 2: Inspect the Assets console markup** (§11 item 1). In card, list and column views, record which attribute marks a folder item (check the `foundation-collection-item` element and its children in DevTools), and use it in `eligible.js`. Record the attribute in the commit message.
- [ ] **Step 3: Deploy**: `mvn -pl ui.apps clean install -PautoInstallPackage`
- [ ] **Step 4: Verify by hand** (§10.3 step 2): one `.json` selected → **Edit JSON** visible, and clicking it opens the editor for that file; PNG → hidden; two items → hidden; a folder → hidden. Check card, list and column views. Also check the Delete/other OOTB actions are unaffected.
- [ ] **Step 5: Commit**

```bash
git add ui.apps/src/main/content/jcr_root/apps/dam ui.apps/src/main/content/jcr_root/apps/aemcloudproject/clientlibs/jsoneditor-action ui.apps/src/main/content/META-INF/vault/filter.xml
git commit -m "feat(jsoneditor): add Edit JSON action to the Assets toolbar"
```

---

### Task 7: Integration tests

**Files:**
- Create: `it.tests/src/main/java/com/aemcloudproject/it/tests/JsonAssetSaveIT.java`

**Interfaces:**
- Consumes: Task 3's POST contract.

Setup follows the `CreatePageIT` conventions (`CQAuthorClassRule`, `CQRule`, admin `CQClient`). In `@Before`, create `/content/dam/aemcloudproject/jsoneditor-it-<random>/sample.json`:
- `createNode(path, "dam:Asset")`
- `createNode(path + "/jcr:content", "dam:AssetContent")`
- `createNode(path + "/jcr:content/metadata", "nt:unstructured")`
- `createNode(path + "/jcr:content/renditions", "nt:folder")`
- `upload(tempFile("{\"a\":1}"), "application/json", path + "/jcr:content/renditions/original", false)`

In `@After`, `deletePath(folder)`. Every POST sends `Content-Type: application/json` and a `CSRF-Token` read from `/libs/granite/csrf/token.json`.

- [ ] **Step 1: Write the tests**

```java
@Test public void savesNewContentAndReturnsVersionLabel() throws Exception {
    JsonNode body = post("{\"a\":2}", 200);
    assertEquals("saved", body.get("status").asText());
    assertTrue(body.get("versionLabel").asText().startsWith("jsoneditor-"));
    assertEquals("{\"a\":2}", adminAuthor.doGet(path + "/jcr:content/renditions/original", 200).getContent());
}
@Test public void sameContentIsUnchanged() throws Exception {
    assertEquals("unchanged", post("{\"a\":1}", 200).get("status").asText());
}
@Test public void invalidJsonIs400WithPosition() throws Exception {
    JsonNode body = post("{\n  \"a\": 1,\n}", 400);
    assertEquals("invalid_json", body.get("status").asText());
    assertEquals(3, body.get("line").asInt());
}
```

- [ ] **Step 2: Run against the local SDK**

Run: `mvn -pl it.tests clean verify -Plocal -Dit.test=JsonAssetSaveIT` (the `local` profile points at `localhost:4502`/`4503` with admin/admin).
Expected: `Tests run: 3, Failures: 0`.

Three spec §10.2 checks stay manual, because `aem-cloud-testing-clients` has no simple API for them: version contents (§10.3 step 8), the read-only user's 403 (step 11) and the CSRF rejection (Task 8). They are listed in the PR description.

- [ ] **Step 3: Commit**

```bash
git add it.tests/src/main/java/com/aemcloudproject/it/tests/JsonAssetSaveIT.java
git commit -m "test(jsoneditor): add save servlet integration tests"
```

---

### Task 8: SDK verification and handoff

**Files:**
- Modify: `docs/superpowers/specs/2026-10-29-dam-json-editor-design.md` §11 → append a "Result" line under each item.

- [ ] **Step 1: Run the remaining §11 checks**
  - **Item 5:** batch mode holds back the write. Use a throwaway probe, not committed: a temporary debug log line printing `session.hasPendingChanges()` after `addRendition`. Expected: `true`.
  - **Item 6:** `jcr:lastModified`/`jcr:lastModifiedBy` update after a save. If not, add them in Task 2's step 7, with a test.
  - **Item 7:** Timeline → Versions → the newest `jsoneditor-…` holds the old content.
  - **Item 8:** load an asset through its own URL on a Cloud dev environment, if one is available. If `fetch` hits a cross-origin redirect, add GET to the servlet per §11 item 8. That is a new mini-task with its own test.
- [ ] **Step 2: Run the remaining manual checks**
  - §10.3 step 10 (last save wins), step 15 (publish: `curl -X POST http://localhost:4503/content/dam/…/sample.json.jsoneditor.json` returns no `status` JSON) and step 16 (no external network requests).
  - CSRF: `curl -u admin:admin` POST **with a login-token cookie and without** `CSRF-Token` → rejected (403).
- [ ] **Step 3: Full module build**: `mvn -pl core,ui.apps,ui.config,it.tests clean install`. Expected: `BUILD SUCCESS`.
- [ ] **Step 4: Commit the spec results**

```bash
git add docs/superpowers/specs/2026-10-29-dam-json-editor-design.md
git commit -m "docs(jsoneditor): record SDK verification results"
```
- [ ] **Step 5: Write the PR description.** It explains the flow (toolbar → page → load → validate/preview/compare → POST → service steps) and the inputs/outputs of each component, lists the manual checks with their results, and calls out §8.1 (last save wins).
