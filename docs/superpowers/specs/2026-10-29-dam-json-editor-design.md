# DAM JSON Editor — Design

- **Date:** 2026-10-29
- **Branch:** `feature/json-formatter`
- **Status:** Approved in design review, awaiting spec review
- **Reviews:** two GPT-6 Astra (Codex) review rounds; accepted findings are folded in, and the user then simplified the design further

## 1. Goal

Let authors edit JSON files stored in AEM Assets directly in the browser. The editor has validation, a live formatted preview, a side-by-side diff against the version they opened, and a save that writes back to the same asset with versioning.

## 2. Requirements and how they are met

| # | Requirement | How it is met |
|---|---|---|
| 1 | "Edit JSON" toolbar action for a selected JSON file; editor page with metadata | Toolbar action in the classic Assets console. The editor header shows the **asset path only**, which the user confirmed is enough. Last modified time and user are deliberately not shown. |
| 2 | Editable JSON with validation and clear errors; live read-only formatted preview | Left: `vanilla-jsoneditor` text mode, with errors shown as line, column and message. Right: `vanilla-jsoneditor` read-only tree view, updated as the user types. |
| 3 | Compare mode: side-by-side diff with highlights | `jsdiff` computes the diff and `diff2html` renders it side by side, with line and word highlights. |
| 4 | Save back to the same asset with versioning, permission check, conflict handling | One POST servlet: it validates, checks permissions, creates a version and writes the original rendition. **Conflict policy: last save wins** (decision §8.1). Overwritten content is always kept as a version. |
| 5 | Security notes, service user, testing steps | §9 security notes. **No service user** (decision §8.2, with the reasoning). §10 testing. |
| 6 | Evaluate the listed libraries; reuse or propose safe alternatives | §4 library evaluation. |

## 3. Constraints

- AEM as a Cloud Service, SDK `2025.5.21005.20250522T173058Z-250500`, Java 11, AEM archetype project.
- **No front-end build.** `ui.frontend` is not used. All JS, CSS and third-party libraries are checked in as plain files inside `ui.apps` clientlibs.
- Target UI is the **classic Touch UI Assets console** (`/assets.html`), not the new Assets View.
- **Author only.**
- Any DAM path is allowed. Normal JCR ACLs are the only access control.
- Max file size: 2 MB, set by an OSGi property.

## 4. Library evaluation

| Library | Verdict | Reason |
|---|---|---|
| `josdejong/svelte-jsoneditor` (as the framework-free `vanilla-jsoneditor` package) | **Reuse** for editing and preview | Maintained, ISC licence. Text mode gives line and column errors. Read-only tree mode is a good preview. Ships a single-file browser ES module (`standalone.js`), so no Svelte runtime and no build are needed. |
| `josdejong/jsoneditor` | **Rejected** | The legacy predecessor, in maintenance mode, and its author recommends svelte-jsoneditor instead. |
| Microsoft Monaco | **Rejected** | The Monaco 0.53.0 changelog deprecates the AMD build with no support. Its ESM build needs a bundler, which conflicts with the no-build constraint. Pinning 0.52.x would start a new feature on a dead end. |
| `jsdiff` + `diff2html` *(safe alternative)* | **Adopted** for Compare | Both ship prebuilt browser (UMD) bundles that work with no build. jsdiff is BSD-3-Clause and diff2html is MIT. They are much smaller than Monaco and need no web workers. |

All libraries are stored in the project, pinned to a version, and come with their licence files. **Nothing loads from a CDN.**

## 5. Architecture

```
Assets console (/assets.html)
  toolbar [Edit JSON]  ──(foundation.link)──►  Editor page (Granite shell page)
  (1 item, name *.json)                          /mnt/overlay/aemcloudproject/gui/content/jsoneditor.html<asset path>
                                                 ├─ header: asset path
                                                 ├─ read-only banner   (rendercondition: NOT privilege rep:write)
                                                 ├─ [Format] [Compare] [Save]   (Save: rendercondition privilege rep:write)
                                                 ├─ left: vanilla-jsoneditor (text)   right: vanilla-jsoneditor (tree, read-only)
                                                 └─ compare view: jsdiff + diff2html (side-by-side)
          load:  GET  <asset path>                       (AEM's own asset serving, no custom code)
          save:  POST <asset path>.jsoneditor.json       (JsonAssetSaveServlet)
```

### 5.1 Components

| # | Component | Module | Path / class |
|---|---|---|---|
| C1 | Toolbar action | ui.apps | `/apps/dam/gui/content/assets/jcr:content/actions/selection/editjson` |
| C2 | Toolbar eligibility JS | ui.apps | clientlib `aemcloudproject.jsoneditor.action`, category `dam.gui.actions.coral` |
| C3 | Editor page | ui.apps | `/apps/aemcloudproject/gui/content/jsoneditor` |
| C4 | Editor clientlib | ui.apps | `/apps/aemcloudproject/clientlibs/jsoneditor`, category `aemcloudproject.jsoneditor` |
| C5 | Save servlet | core | `com.aemcloudproject.core.servlets.JsonAssetSaveServlet` |
| C6 | Save service + config | core | `JsonAssetSaveService` / `impl.JsonAssetSaveServiceImpl` / `config.JsonAssetSaveConfiguration` |
| C7 | OSGi config (author only) | ui.config | `osgiconfig/config.author/com.aemcloudproject.core.services.impl.JsonAssetSaveServiceImpl.cfg.json` |
| C8 | Package filters | ui.apps, ui.apps.structure | filter entries for C1 and `/apps/aemcloudproject/gui`; `/apps/dam` repository-structure root |

## 6. Component design

### C1 — Toolbar action
- A Granite `collection/action` node (`granite/ui/components/coral/foundation/collection/action`) added through the `/apps/dam` overlay, which Sling Resource Merger combines with `/libs`.
- `action="foundation.link"` with `data/href.uritemplate="/mnt/overlay/aemcloudproject/gui/content/jsoneditor.html{+item}"`, so it opens in the same tab.
- `activeSelectionCount="single"`, `target=".cq-damadmin-admin-childpages"`, `activeCondition="aemcloudproject.jsoneditor.eligible"`, `relScope="none"`. The last one stops Granite's relationship filtering from hiding the custom action.
- Icon and title: `code` / "Edit JSON".
- The vault filter covers **only this leaf node** (default replace mode), so redeploys update it and nothing else under `/apps/dam` is touched.

### C2 — Toolbar eligibility
- Registers `foundation.collection.action.activecondition` named `aemcloudproject.jsoneditor.eligible`.
- Returns `true` only when exactly one item is selected, the item is an asset (not a folder), and its name (from `data-foundation-collection-item-id`) ends in `.json`, case-insensitive.
- This logic is synchronous. The server checks again on save (§7).

### C3 — Editor page
- `sling:resourceType="granite/ui/components/shell/page"`, titled "Edit JSON".
- Head clientlibs: `coralui3`, `granite.ui.coral.foundation`, `aemcloudproject.jsoneditor`.
- **Read-only mode with no Java:** the Save button has `granite:rendercondition` of type `granite/ui/components/coral/foundation/renderconditions/privilege`, with `path="${requestPathInfo.suffix}"` and `privileges="[rep:write]"`. A read-only coral alert banner uses the inverse (`renderconditions/not` around the same privilege condition). This is the same out-of-the-box render-condition approach already used in this project.
- The header shows the asset path, set with `textContent` from the URL suffix.
- Back/close link returns to `/assets.html` + the parent folder.

### C4 — Editor clientlib
```
clientlibs/jsoneditor/
├── .content.xml          categories=[aemcloudproject.jsoneditor], allowProxy=true,
│                         jsProcessor=[default:none,min:none], cssProcessor=[default:none,min:none]
├── js.txt                vendor/jsdiff-<v>/diff.min.js, vendor/diff2html-<v>/diff2html-ui-slim.min.js, js/editor.js
├── css.txt               vendor/diff2html-<v>/diff2html.min.css, css/editor.css
├── js/editor.js          our code (plain script, no build)
├── css/editor.css
├── vendor/               classic UMD bundles, listed in js.txt / css.txt
│   ├── jsdiff-<v>/diff.min.js + LICENSE
│   └── diff2html-<v>/diff2html-ui-slim.min.js, diff2html.min.css + LICENSE
├── resources/vendor/     ES module, served as a separate file (never concatenated)
│   └── vanilla-jsoneditor-<v>/standalone.js + LICENSE.md
└── VENDOR.md             name, version, source URL, licence, SHA-256 of every vendored file
```
`<v>` is the latest stable release at implementation time (at least two weeks old), recorded in `VENDOR.md`.
- **Why the minifier is off:** the clientlib minifier can mangle the `import()` syntax that loads the editor library. Vendored files are already minified.
- **Why vanilla-jsoneditor sits under `resources/`:** it is an ES module, and clientlib file concatenation would break it. `editor.js` loads it with `import('/etc.clientlibs/aemcloudproject/clientlibs/jsoneditor/resources/vendor/vanilla-jsoneditor-<v>/standalone.js')`.
- Version numbers in the folder names make upgrades change the URL, so browsers never serve a stale cached copy.

**Editor behaviour (`editor.js`)**
1. **Load.** The asset path comes from the URL suffix. It runs `fetch(<path>, {cache: 'no-store', credentials: 'same-origin'})`. If `Content-Length` (or the received text) is over 2 MB, it shows "File too large to edit" and stops. A leading BOM is removed. The text becomes the **baseline**, which stays fixed while the page is open.
2. **Editor (left).** `vanilla-jsoneditor` in text mode, with the editor's own menus turned off: transform, query, sort and mode switch. The raw text the user types is what gets saved.
3. **Validation.** Errors appear as *"Line L, column C: message"*, at the first position where the text stops being valid JSON (a parser cannot locate a deleted character earlier in the file). The library's Auto repair is hidden with CSS (the library has no option to disable it) because it guesses at the structure and can produce valid but wrong JSON; Undo and Compare are the way to recover. Save is disabled while the text is invalid.
4. **Preview (right).** A second `vanilla-jsoneditor` in read-only tree mode, debounced (about 300 ms). While the text is invalid, the preview keeps the last valid state and shows a "Preview out of date — fix errors" note.
5. **Compare.** It toggles a view rendered by `Diff2HtmlUI` (side-by-side, word-level highlights) from `Diff.createTwoFilesPatch('original', 'modified', baseline, current)`.
   - **Normalize formatting** toggle: re-indents both sides before diffing. It only changes the display, keeps key order, and leaves the saved text alone.
   - **Limits:** jsdiff `timeout`/`maxEditLength` and diff2html `diffMaxChanges`/`diffMaxLineLength`/`matchingMaxComparisons`. When a limit is hit, it shows "Too many differences to display".
6. **Format.** Pretty-prints the current text (2 spaces) in the left editor. This is an explicit, undoable action.
7. **Save.** Gets a CSRF token (`GET /libs/granite/csrf/token.json`), then sends `POST <path>.jsoneditor.json` with `Content-Type: application/json`, header `CSRF-Token`, and the current text as the body.
   - Save is disabled while a request is running.
   - On success it shows a toast ("Saved — version jsoneditor-…") and makes the **submitted** text the new baseline.
   - On failure it shows the server's error message and leaves the editor text untouched.
8. **Leave guard.** A `beforeunload` warning appears while the text differs from the baseline.
9. **XSS rule.** Our code only inserts text (`textContent`). The only HTML that goes into the page is diff2html's own escaped output, and only inside the Compare container.

### C5 — Save servlet
- `@SlingServletResourceTypes(resourceTypes = "dam:Asset", selectors = "jsoneditor", extensions = "json", methods = "POST")`.
  - A request to `/content/dam/a/file.json.jsoneditor.json` resolves to resource `file.json`, selector `jsoneditor`, extension `json`.
  - Sling only reaches the servlet if the user can read the asset. No part of the request is used to build a repository path.
- It has a mandatory `@Reference` to `JsonAssetSaveService`, so the servlet is not active wherever the service is not (publish).
- It reads the body as UTF-8 through a stream capped at `maxSizeBytes + 1` bytes, passes it to the service, and turns the result into JSON: `{ "status": "...", "message": "...", "line": n, "column": n, "versionLabel": "..." }`.
- Every response uses `Content-Type: application/json; charset=UTF-8`.

### C6 — Save service
`JsonAssetSaveServiceImpl`, `@Component(configurationPolicy = ConfigurationPolicy.REQUIRE)`, `@Designate(ocd = JsonAssetSaveConfiguration.class)`:
- `maxSizeBytes` (long, default `2097152`).

It runs the save sequence in §7 on the **requesting user's resource resolver**. No service resolver is ever used.

### C7 — Config
`config.author` only. Publish has no config, so neither the service nor the servlet starts there.

## 7. Save sequence

All steps run in one request, on the logged-in user's own session.

| Step | Check / action | Result if it fails |
|---|---|---|
| 1a | Resource is under `/content/dam/` and adapts to `com.day.cq.dam.api.Asset` | **400** `not_dam_asset` |
| 1b | Asset name ends in `.json` **or** its MIME type is `application/json` | **415** `not_json` |
| 2 | Body size ≤ `maxSizeBytes` (stream cap; `Content-Length` checked first when present) | **413** `too_large` |
| 3 | Body is valid UTF-8 and valid JSON. Jackson `ObjectMapper` with `FAIL_ON_TRAILING_TOKENS`, `StreamReadFeature.STRICT_DUPLICATE_DETECTION`, and Jackson's default `StreamReadConstraints`. A leading BOM is removed. | **400** `invalid_json` with message, line and column |
| 4 | The user holds `rep:write` on the asset, `jcr:content`, `jcr:content/metadata` and `jcr:content/renditions/original`, and `jcr:versionManagement` on the asset, checked with `AccessControlManager.hasPrivileges`. This only gives a clearer error; Oak still enforces permissions at commit. | **403** `forbidden` |
| 5 | Read the current original rendition and compare it byte-for-byte with the new content (after the BOM is removed) | Identical → **200** `unchanged`, no version created, stop |
| 6 | `asset.createRevision("jsoneditor-" + yyyyMMddHHmmssSSS, "Saved from JSON editor")`. This snapshots the **old** content; the API saves the session itself, and nothing is pending at this point. | Exception → **500** `version_failed`, nothing written |
| 7 | `asset.setBatchMode(true)`; `asset.addRendition("original", newBytes, "application/json")`; set `jcr:content/metadata/dam:size` and `dam:sha1` (SHA-1 hex of the new bytes, so duplicate detection stays correct). `jcr:lastModified`/`jcr:lastModifiedBy` are updated by the rendition write; verify on SDK (§11) and set them explicitly if not. | — |
| 8 | `resourceResolver.commit()` | Any exception in steps 7–8 → `resourceResolver.revert()`. If the cause is `AccessDeniedException` → **403**, otherwise **500** `save_failed`. Never retried automatically. |
| 9 | Success | **200** `saved` with `versionLabel` |

A version left behind by a failed step 7 or 8 is acceptable, because it is only a copy of the old content.

## 8. Decisions

### 8.1 Concurrent edits: last save wins
There is no change detection between open and save. If two authors edit the same file, the later save replaces the earlier one **without a warning**. This is acceptable because step 6 always versions the current content first, so the overwritten content can be recovered with **Timeline → Versions → Revert**. The trade-off is accepted knowingly: nobody is told when it happens, so someone has to notice. If this becomes a problem, the upgrade path is a content fingerprint sent on save, with a 409 when it no longer matches (designed and set aside in review).

### 8.2 No service user
Every read and write runs on the logged-in user's own session:
- AEM permissions apply exactly as they do everywhere else in Assets. The feature cannot let anyone do more than they already could.
- AEM records the real user as the modifier and version creator automatically.
- A service user would only be needed to read data the user is not allowed to see. Nothing in this feature needs that, and the display-name lookup was dropped. A service user here would only add risk (privilege escalation if the code had a bug).
- The existing `myaemproject-serviceuser` is neither used nor changed.

### 8.3 Header shows the asset path only
No last-modified or user metadata is fetched (user decision), which removes the need for a GET servlet. The file is loaded straight from AEM's own asset URL.

## 9. Security notes

1. **Authorization.** All repository access uses the requesting user's session. Oak enforces ACLs at commit, and the step 4 precheck exists only to give a clear 403. The read-only UI uses the OOTB `privilege` render condition.
2. **CSRF.** AEM's Granite CSRF filter is left fully on, with no exclusions. It stops a malicious website open in the same browser from silently making the author's browser POST a save to AEM. The editor sends a fresh token from `/libs/granite/csrf/token.json` in the `CSRF-Token` header.
3. **No path injection.** The servlet is bound by resource type. The target asset is whatever Sling resolved, and it is checked to be a DAM asset under `/content/dam/`.
4. **Input limits.** A body cap of `maxSizeBytes`, Jackson's default nesting and string-length limits, and strict UTF-8 decoding stop oversized or deeply nested JSON from exhausting the server.
5. **XSS.** Our code never uses `innerHTML`. diff2html escapes file content, and its HTML only goes into the Compare container. A test with hostile content covers this (§10).
6. **Author only.** The service requires an OSGi config that exists only in `config.author`, and the servlet depends on the service, so nothing is active on publish. The editor page and toolbar only exist in the author Assets console.
7. **Supply chain.** Libraries are vendored and pinned. `VENDOR.md` records the version, source URL, licence and SHA-256 for each file. There is no CDN and no third-party network request at runtime. Upgrades go through a reviewed PR.
8. **Recovery.** Every save that changes content creates a version first. Rollback uses the OOTB Timeline → Versions → Revert.
9. **No content in logs.** The servlet logs errors (path and failure reason) but never file content. No separate audit log is kept, by decision.

## 10. Testing

### 10.1 Unit tests — `core` (JUnit 5, AEM Mocks, Mockito; existing conventions)
- **Validation:** valid JSON passes; trailing comma → correct line and column; content after the JSON → 400; duplicate key → 400; invalid UTF-8 → 400; empty body → 400; leading BOM removed.
- **Eligibility:** `.json` name passes; `application/json` with another name passes; PNG → 415; resource outside `/content/dam` → 400 `not_dam_asset`.
- **Size:** at the limit passes; one byte over → 413; multi-byte UTF-8 right at the limit.
- **Permissions:** missing privileges → 403 and nothing written. The privilege check sits behind a small package-private method so tests can stub it, because AEM Mocks has no ACL support.
- **Unchanged:** identical content → 200 `unchanged`; `createRevision` never called.
- **Success:** Mockito `InOrder`: `createRevision` → `setBatchMode(true)` → `addRendition("original", …)` → `commit()`; `dam:size` and `dam:sha1` updated; 200 with the version label. A Mockito `Asset` is used here because AEM Mocks does not implement `createRevision`.
- **Failures:** `createRevision` throws → 500 and no `addRendition`; `commit` throws `PersistenceException` → `revert()` and 500; cause `AccessDeniedException` → 403.
- **Servlet:** maps service results to status and JSON body; `Content-Type` header set.

### 10.2 Integration tests — `it.tests` (JUnit 4 + `aem-cloud-testing-clients`, matching that module)
- Upload `sample.json` to `/content/dam/aemcloudproject/jsoneditor-it/`.
- POST valid JSON with a CSRF token → 200 `saved`; asset content equals the new text; version count +1; the newest version holds the **old** text.
- POST the same text again → 200 `unchanged`; version count unchanged.
- POST invalid JSON → 400 with line and column.
- POST without a CSRF token → rejected by the CSRF filter.
- A test user with read-only access → 403; content unchanged.

### 10.3 Manual test script (local SDK)
1. Deploy, then upload `sample.json`, `broken.json` (invalid), `large.json` (3 MB) and `image.png` to `/content/dam/aemcloudproject/jsoneditor-test/`.
2. **Toolbar:** select `sample.json` → **Edit JSON** shows. Select `image.png` → hidden. Select two items → hidden. Check card, list and column views.
3. **Editor:** opens with the asset path in the header; content loads; the preview shows the tree.
4. **Validation:** delete a comma → "Line …, column …" error, Save disabled, preview marked out of date; no Auto repair button in the error bar or the line tooltip; Ctrl+Z restores the text.
5. **Preview:** edit a value → the tree updates within about 300 ms.
6. **Compare:** changes are highlighted side by side with word-level highlights. **Normalize formatting** hides whitespace-only changes.
7. **Hostile content:** add `"x": "<img src=x onerror=alert(1)>"`, open Compare → no alert; the text appears literally.
8. **Save:** success toast with a version label. **Timeline → Versions** lists `jsoneditor-…`, and **Revert** restores the old content.
9. **Unchanged save:** save again without editing → "Nothing to save", no new version.
10. **Last save wins:** open `sample.json` in two tabs, save in A, then save in B → B's content wins, and A's content is in the version history.
11. **Read-only:** sign in as a user with read-only access → the banner shows and there's no Save button. A forced POST with curl → 403.
12. **broken.json:** opens and shows the error immediately; it can be fixed and saved.
13. **large.json:** "File too large to edit".
14. **Leave guard:** edit, then navigate away → browser warning.
15. **Publish:** `POST /content/dam/…/sample.json.jsoneditor.json` on publish is not handled by our servlet (no `status` JSON in the response).
16. **Network:** DevTools Network tab shows no requests to any external domain.

## 11. Verify on the local SDK during implementation

These are integration assumptions the SDK API jar cannot prove. Each one is checked once, and the result goes into the PR description:
1. **`relScope="none"`** keeps the custom action visible, and the eligibility condition behaves the same in card, list and column views.
2. **Privilege render condition** accepts `path="${requestPathInfo.suffix}"` on a `/mnt/overlay` page with a suffix.
3. **ES module loading** from `/etc.clientlibs/.../resources/...`: served with a JavaScript MIME type, runs under the author CSP, and works with the minifier disabled.
4. **Whether `vanilla-jsoneditor` standalone injects its own CSS** or needs a stylesheet added to `css.txt`.
5. **Batch mode** really holds back the `addRendition` write until `commit()` (throwaway probe, not committed).
6. **`addRendition("original")`** updates `jcr:lastModified`/`jcr:lastModifiedBy`; if not, step 7 sets them.
7. **Version contents:** the version created in step 6 holds the old bytes.
8. **Loading the file from its asset URL on a real AEMaaCS environment, not only the SDK.** Cloud author can answer a binary GET with a redirect to blob storage (direct binary download). A cross-origin redirect would stop `fetch` from reading the body because of CORS. **Fallback if so:** add `GET` to `JsonAssetSaveServlet` (same selector) to stream the original rendition's text from the same origin, with the same size cap. This is a small addition and doesn't change the rest of the design.

## 12. Out of scope

- The new Assets View (Adobe App Builder extension).
- Conflict detection and a merge UI (§8.1).
- Showing last-modified or user metadata.
- JSON Schema validation.
- Editing on publish, bulk editing, editing files that aren't JSON.
- Restoring the original line-ending style (the editor saves `\n` line endings).
- Automated front-end tests (there is no node build). A `ui.tests` browser test can be added later.
