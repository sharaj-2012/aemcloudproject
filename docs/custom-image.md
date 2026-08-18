# Custom Image — art-directed responsive image component

Reusable component that composes **two genuine Core Image v3 instances** (desktop +
mobile) into a native `<picture>` element. It builds no image engine of its own.

Core Components version in this project: **2.26.0** (AEM SDK `2025.5.21005`).

---

## 1. Final architecture

```
CONTENT IMAGE (consumer)                       any future Hero / Banner / Card / Promo
        |  CustomImageResourceWrapper (public org.apache.sling.api.resource.ResourceWrapper)
        v  same path, resourceType -> aemcloudproject/components/custom-image
CUSTOM IMAGE  (orchestration only)
        |
        +--------------------------+
        v                          v
  <path>/desktop              <path>/mobile          <- real, separate AEM resources
  rt: .../custom-image/desktop  rt: .../custom-image/mobile
        |                          |
        v                          v
  aemcloudproject/components/image  (existing project Image proxy)
        |                          |
        v                          v
  core/wcm/components/image/v3/image
        |                          |
        v                          v
  Image v3 model             Image v3 model         <- built via Sling ModelFactory
        |                          |                   .getModelFromWrappedRequest(...)
        v                          v
  desktop src/srcset/sizes   mobile src/srcset/sizes
        \__________  ______________/
                   \/
               <picture>
    browser picks family, then resolution
```

## 2. Files

**ui.apps** — `/apps/aemcloudproject/components/`

| Path | Purpose |
|---|---|
| `custom-image/.content.xml` | The reusable component. Group *Aem Cloud Project - Content*. Deliberately has **no** `sling:resourceSuperType` — see deviation D1. |
| `custom-image/desktop/.content.xml` | Hidden (`componentGroup=".hidden"`) internal type. `sling:resourceSuperType = aemcloudproject/components/image`, `imageDelegate = aemcloudproject/components/image`. |
| `custom-image/mobile/.content.xml` | Same, for mobile. |
| `custom-image/dialogfields/desktop`, `.../mobile` | Reusable Granite field fragments (`./desktop/*`, `./mobile/*`). Included, not duplicated, by consumer dialogs. |
| `custom-image/_cq_dialog/.content.xml` | **Does not exist.** `custom-image` has no dialog of its own and is not authorable standalone; consumers include the two `dialogfields` fragments in their own dialog. A consequence is that `./breakpoint`, which `CustomImageImpl` reads, has no field anywhere — `getMobileMedia()` is always the 768px default. |
| `custom-image/custom-image.html` | The `<picture>` markup. |
| `customcomponents/contentimage/*` | Refactored consumer: title, description, link + delegated image. |

**core**

| Class | Purpose |
|---|---|
| `models/CustomImage` | `getDesktopImage()`, `getMobileImage()`, `hasDesktopImage()`, `hasMobileImage()`, `getMobileMedia()` |
| `models/impl/CustomImageImpl` | Request-adaptable. Uses `ModelFactory#getModelFromWrappedRequest` per child. |
| `models/impl/CustomImageResourceWrapper` | Public `ResourceWrapper`; re-types a consumer resource as custom-image, path and children preserved. |
| `models/ContentImageModel` / `Impl` | Consumer. Business properties only. |
| `test/.../TestImage` | Test double for the Core Image model. |
| `test/.../CustomImageImplTest`, `ContentImageModelImplTest` | Unit tests. |

**ui.content**

* `settings/wcm/policies/.content.xml` — **unchanged**. No custom policy is defined. The
  existing *Content Image* policy (`aemcloudproject/components/image/policy_651483963895698`
  — widths `[320,480,600,800,1024,1200,1600]`, `jpegQuality=85`, `disableLazyLoading=false`,
  `enableAssetDelivery=true`, crop/rotate plugins) is the single source of truth.
* `templates/page-content/policies/.content.xml` — mappings only. The mapped types are the
  two **child** types, `aemcloudproject/components/custom-image/desktop` and
  `.../custom-image/mobile`, both pointing at that same Content Image policy. The
  `custom-image` node above them is a path segment and carries no policy.

  Mapping the parent types instead (`custom-image`, `customcomponents/contentimage`) has
  **no effect** — measured, see R1. The policy is resolved per image resource from that
  resource's own type, not from the component it is nested inside.
* `META-INF/vault/filter.xml` — the page-content template's `policies` node is filtered in
  replace mode, ahead of the broad `mode="merge"` `/conf/aemcloudproject` filter. Under
  merge, FileVault skips the entire `policies` aggregate once its root node exists on the
  instance, so mapping nodes added here would never reach an already-provisioned
  environment. Policy *definitions* are unaffected and still merge.

**ui.frontend** — `components/_custom-image.scss` (layout only, no breakpoint, no JS).

## 3. Content structure produced by the dialog

```
/content/.../test
  sling:resourceType = aemcloudproject/components/customcomponents/contentimage
  title, description, linkUrl
  |
  +-- desktop
  |     sling:resourceType = aemcloudproject/components/custom-image/desktop
  |     fileReference      = /content/dam/site/desktop.jpg
  |     alt / altValueFromDAM / isDecorative
  |
  +-- mobile
        sling:resourceType = aemcloudproject/components/custom-image/mobile
        fileReference      = /content/dam/site/mobile.jpg
```

Both children persist a real `sling:resourceType` (hidden Granite fields), so each is
independently an Image v3 resource and produces its own delivery URLs:
`/…/test/desktop.coreimg.85.640.jpeg/…` vs `/…/test/mobile.coreimg.85.320.jpeg/…`.

## 4. Breakpoint

`768px`, taken from the existing project grid (`clientlib-grid`, phone breakpoint is
`@media (max-width: 768px)`) — not the generic 767 suggested in the brief. It lives in
exactly one place: `CustomImageImpl.DEFAULT_BREAKPOINT`, surfaced as
`getMobileMedia()` → `(max-width: 768px)`, and authorable per component via
`./breakpoint`. It is not repeated in HTL, CSS or JavaScript.

## 5. Deviations from the brief (and why)

**D1 — `custom-image` does not inherit the Image proxy.** The brief's §26 says the
consumer should not become an image; the same reasoning applies to the orchestrator. If
`custom-image` inherited `core/wcm/components/image/v3/image`, `/test.coreimg…` would
also be servable from the parent, which is exactly the ambiguity the two-child design
exists to remove. The two children carry the image identity. (An earlier uncommitted
draft in the repo did set this super type; it has been removed.)

**D2 — `imageDelegate` points at `aemcloudproject/components/image`, and there is no
custom-image policy.** This matches the existing project convention (`teaser/.content.xml`
already uses `imageDelegate="aemcloudproject/components/image"`) and, at
Adaptive-Image-Servlet time, aims the content-policy lookup at the shared *Content Image*
policy. Since every mapping — render-time and servlet-time, parent and child — resolves to
that one policy object, there is nothing to keep in sync and no duplicated width list.

**D3 — dialog fields are project-owned fragments, not
`core/wcm/components/include/imagedelegate`.** That Adobe include has no mechanism for
prefixing field names, and every field in the Image v3 asset tab is hard-bound to a fixed
property (`./file`, `fileReferenceParameter="./fileReference"`, `./alt`, `./isDecorative`,
`./smartCropRendition`, …). Including it twice would make the desktop and mobile tabs write
to the same properties on the same node — the §4 anti-pattern. Per §21 the fallback was
taken: a project-owned reusable fragment, included by
`granite/ui/components/coral/foundation/include`. Adobe's Image v3 dialog XML is not
copied. POC field set: `fileReference` (via the standard `fileupload` widget), `alt`,
`altValueFromDAM`, `isDecorative`.

An attempt to go further — a project-owned Granite include that reuses Adobe's asset tab
and rewrites its field names to `./desktop` / `./mobile` — was built and then reverted
because the dialog would not open. The sources are kept at `_to_delete/` in the repo root.

**D4 — the consumer keeps a link property.** `imageUrl` from the old ContentImage is
read as a fallback for the new `linkUrl`, so existing authored content keeps working.
A link is a business property, not image delivery.

## 6. Risks that need a running AEM to close

**R1 — content policy resolution for the nested child resources (§16). CLOSED, measured on
a running instance.** The two times resolve through completely different mechanisms, and
only one of them needs a mapping.

*Servlet-time works with no mapping of its own.* `AdaptiveImageServlet#getContentPolicy`
re-types the resource through the `imageDelegate` property on the child component and asks
`ContentPolicyManager`, so it lands on the already-mapped
`aemcloudproject/components/image`. Confirmed by requesting widths directly against
`/…/contentimage/desktop`: `85.800` and `85.1600` return 200, `85.777` returns **404**.
That 404 is the assertion — an unresolved policy would not enforce the width list.

*Render-time needs the child types mapped explicitly.* Core `ImageImpl` (v1, inherited by
v2 and v3) sets `smartSizes` from `currentStyle.get("allowedRenditionWidths", …)`, and
`getSrcset()` returns null when `getWidths()` is empty. `currentStyle` is resolved from the
image resource's **own** resource type — `aemcloudproject/components/custom-image/desktop` —
not from the component it is nested in and not from the delegate. Mappings on
`custom-image` and on `customcomponents/contentimage` were added, measured, and made no
difference; removing them again changed nothing. Mapping `custom-image/desktop` and
`custom-image/mobile` produced the full 7-candidate srcset immediately.

The earlier guess in this section — that `currentStyle` follows the ambient component
context — was wrong. It follows the resource.

Note this failure is quiet: with no widths the component still renders a working `<img>`
via the plain `.coreimg.jpeg` URL, so it looks fine until you check for `srcset`. On
AEMaaCS with `enableAssetDelivery=true` the servlet is bypassed, but the render-time
`currentStyle` lookup is unchanged, so the mapping is still required.

**R2 — in-place editing / crop / rotate (§22).** Not verified. Standard Image v3
in-place editing assumes one image resource per component; two image children inside one
component are unlikely to give a correct author toolbar out of the box. Treat crop and
rotate as unverified until tested; do not add editor hacks before delivery is proven.

**R3 — Dynamic Media (§18).** No DM URLs are constructed anywhere; whatever Image v3
returns is rendered as-is. However, the Core Image v3 client library
(`data-cmp-hook-image="imageV3"`) is not wired up, because the markup is a `<picture>`
rather than Adobe's `cmp-image` wrapper. DM/NGDM features that depend on that JS
(client-side `srcUriTemplate` expansion, smart crop rendition selection) will not run.
**Do not claim DM support until tested.**

## 7. Verification checklist

Build (`aem.port` defaults to **4507** in the root `pom.xml`, so the single-package profile
targets that instance, not the usual 4502):

```bash
mvn -f /Users/shashankraj/Documents/aemcloudproject clean install -PautoInstallSinglePackage
```

Then, on the instance:

1. Add Content Image to a page on the `page-content` template.
2. Author a desktop asset only → save → verify `/test/desktop` exists with
   `sling:resourceType` and `fileReference` in CRXDE; verify a `<picture>` with one
   `<img>` and **no** `<source>` is rendered.
3. Author a mobile asset too → verify `/test/mobile` exists; verify exactly one
   `<picture>`, one `<source media="(max-width: 768px)">`, one `<img>`.
4. Inspect both `srcset` values — every desktop candidate must contain
   `/test/desktop`, every mobile candidate `/test/mobile`. No mixed families.
   **Steps 3 and 4 verified** on `/content/aemcloudproject/us/en/test-page-latest`: one
   `<source media="(max-width: 768px)">` with 7 mobile candidates, one `<img>` with 7
   desktop candidates, no mixing, all 16 delivery URLs returning 200.
   A missing `srcset` here means R1's policy mapping, not a Java bug.
5. Network tab at a wide viewport → a `/test/desktop…` request. Narrow viewport →
   a `/test/mobile…` request. Test DPR 1 and DPR 2. Do **not** assert a specific width
   candidate — the browser owns that choice; assert the family.
6. Repeat on author, publish and through the dispatcher.
7. Verify Asset Delivery URLs on AEMaaCS if enabled; verify DM separately per R3.
8. Clear the mobile asset → no empty `<source>`. Clear the desktop asset → placeholder,
   no broken `<picture>`.

## 8. Acceptance criteria status

| Criterion | Status |
|---|---|
| custom-image reusable | met |
| desktop/mobile are real separate resources | met |
| both inherit Image v3 | met (via the existing project Image proxy) |
| both return Image v3 `src` / `srcset` / `sizes` | met |
| `<picture>` rendered, mobile via `<source media>`, desktop fallback `<img>` | met |
| mobile optional, no empty `<source>` | met + unit test |
| no viewport-switching JavaScript | met |
| no custom image servlet, no custom srcset builder | met |
| no Adobe internal API extended or imported | met |
| consumer contains no image-delivery logic | met |
| policies verified | mappings in place, all pointing at the single Content Image policy; **delivery behaviour still pending R1** |
| tests pass / Maven build passes | **not run — no Maven Central access from this environment** |
| Dynamic Media | **unverified — see R3** |
| crop / rotate / in-place editing | **unverified — see R2** |
