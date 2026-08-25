# Content Fragment Bulk Upload — Implementation Plan

**Project:** aemcloudproject (AEM as a Cloud Service)
**Feature:** Excel-driven bulk create/update of Offer Detail Content Fragments and their referenced child fragments
**Author:** Shashank
**Status:** Draft for lead review

---

## 1. What we are actually building (plain English)

The Excel workbook is not a "document". It is a **small relational database in
spreadsheet clothing**.

- Each sheet is a **table**: `OfferDetail`, `OfferCategory`, `MerchantDetails`,
  `merchantVenues`, `offerCard`, `offerCta`.
- The **slug** column is the **primary key** of each table.
- Where one sheet mentions another sheet's slug (`offerMerchants = shilla-duty-free`),
  that is a **foreign key**.
- The DAM folder tree is the **destination database**, and a Content Fragment is a **row**.

So the job we are writing is a classic **ETL job**: Extract from Excel, Transform into
a validated object graph, Load into the JCR as Content Fragments.

**The single most important consequence of that framing:** an ETL job is judged on
*idempotency* and *reporting*, not on features. Running the same sheet twice must
produce the same repository state, and when 400 of 500 rows succeed, the author must be
able to see exactly which 100 failed and why. Everything in the task breakdown below
serves those two goals.

**Loading order analogy.** You cannot bolt a wheel onto a car before the wheel exists.
Offer Detail references Merchant Details, which references Merchant Venues. So the job
must build **bottom-up**: venues → merchants → categories → cards → CTAs → offers.
Get this order wrong and you will be writing broken reference paths into the repository.

---

## 2. Findings from the sample workbook (read these before estimating)

I parsed the uploaded `offerBulkUpload.xlsx`. Six sheets, and several structural things
that must become explicit rules:

### 2.1 Multi-value cells use "spill-down" rows

```
offerID   offerSlug            offerCategories  offerMerchants          offerCards  offerCta
811806    duty-free-shopping   travel           shilla-duty-free        visa        register-now
(blank)   (blank)              (blank)          shinsegae-duty-free     (blank)     redeem-now
(blank)   (blank)              (blank)          dufry-thomas-julie      (blank)     (blank)
```

A row with a non-blank key starts a new record; blank-key rows below it are
**continuation rows**. Note that `offerMerchants` has 3 values and `offerCta` has 2 —
**each column's list is independent**. The parser must read column-wise within a row
group, never row-wise. `MerchantDetails` → `merchantVenues` uses the same convention.

> **Recommendation to raise with business:** spill-down is fragile — one stray sort in
> Excel silently destroys the data. A pipe-delimited single cell
> (`shilla-duty-free|shinsegae-duty-free|dufry-thomas-julie`) is far safer. If business
> will not move, we support spill-down but we **must lock the sheet against sorting**.
> Either way, this is a contract decision, not a dev decision — get it signed off.

### 2.2 The sample already has four dangling foreign keys

| Sheet | Value | Problem |
|---|---|---|
| `OfferDetail.offerCta` | `register-now` | Not present in `offerCta` sheet |
| `OfferDetail.offerMerchants` | `dufry-thomas-julie` | Not present in `MerchantDetails` sheet |
| `MerchantDetails.merchantVenues` | `shilla-duty-free-macau`, `shilla-duty-free-hongkong` | Not present in `merchantVenues` sheet |
| `OfferCategory.parent` | `restrelax` | Not present in `OfferCategory` sheet |

This is not a bug in the sample — it is **the normal state of a hand-maintained sheet**.
We need a decided policy before coding (see Decision D3). Silently creating empty stub
fragments is the worst option: it pollutes the DAM with fragments nobody authored.

### 2.3 `OfferCategory.parent` is self-referential

Categories point at other categories. This forces a **two-pass write** for that sheet:
pass 1 creates all category fragments, pass 2 wires up the `parent` references. It also
means we need **cycle detection** (A → B → A) or the reference graph becomes unusable.

### 2.4 Child sheets have empty ID columns

`OfferCategory.ID`, `MerchantDetails.merchantID`, `offerCard.ID` are all blank in the
sample, while `OfferDetail.offerID` is populated. This confirms the requirement that IDs
are generated server-side — and raises Decision D2 below.

---

## 3. Key architecture decisions

### D1 — Upload page or workflow? **Answer: neither first. Build the engine first.**

This is the question you asked, and I think the framing itself is the trap. Both a page
and a workflow are just **triggers**. If either one contains the logic, you can never
reuse it and you can never unit-test it.

```
                    ┌──────────────────────────────────┐
   Trigger layer    │  Workflow step  │  Author UI  │  JMX/dev servlet  │
                    └──────────────────────────────────┘
                                    │  (thin — parse args, hand off)
                    ┌──────────────────────────────────┐
   Engine layer     │  BulkUploadService (OSGi)        │
                    │  parse → validate → resolve →    │
                    │  write → report                  │
                    └──────────────────────────────────┘
```

The engine is a plain OSGi service that takes a workbook `InputStream` + options and
returns a report. It has no idea what triggered it. That is what makes it testable with
plain JUnit + `io.wcm.testing.mock.aem`.

**Then, on triggers, my recommendation is: workflow for the MVP, upload page in phase 2.**

| | Synchronous servlet from a page | Workflow / Sling Job |
|---|---|---|
| Long-running (500+ rows) | ✗ Dies on Dispatcher/CDN timeout (~60s) | ✓ Runs async, no HTTP connection held |
| Retry on failure | ✗ Manual re-upload | ✓ Built-in |
| Author visibility | Need to build it | ✓ Workflow instance + Inbox for free |
| Pod recycle mid-run (AEMaaCS) | ✗ Work lost silently | ✓ Job resumes / workflow retries |
| Time to first working version | Days (UI + backend) | Hours (launcher + step) |

**Concrete MVP path:** author drops the `.xlsx` into
`/content/dam/aemcloudproject/bulk-upload/inbox/` using the **standard DAM upload that
already exists**. A Workflow Launcher watches that path and fires a single-step workflow
model that calls the engine. Zero UI code, working feature in sprint 1.

The custom upload page is then a **UX improvement** in phase 2 — folder picker, dry-run
toggle, run history, one-click report download. Real value, but not on the critical path,
and building it first would delay the thing that actually matters.

> If the sheets grow beyond a few thousand rows, swap the workflow step for a **Sling Job**
> (`JobManager`) with chunking. Same engine, different trigger — which is exactly why the
> engine is separate.

### D2 — What is the real primary key: `offerID` or `offerSlug`? **Recommend: slug.**

The requirement says "check for offerID, if not present create it". But offerID is
*generated by us* when missing — which means it cannot be what the author uses to identify
a record. The slug is stated to be unique and is author-supplied. So:

- **`offerSlug` = primary key, and also the JCR node name.**
- **`offerID` = a stable generated attribute**, written once at creation, never changed.

This is not a cosmetic choice. If the slug is the node name, then "does this fragment
already exist?" is a **direct path lookup**:

```java
resolver.getResource("/content/dam/.../offer-listing/" + slug)   // O(1)
```

versus a **repository-wide query** on `jcr:content/data/master/@offerSlug`, which needs a
custom Oak index, gets slower as content grows, and can return stale results. We remove
an entire class of performance and indexing problems for free.

We still add the Oak property index (Task 3.2) — but for duplicate-detection and
reporting, not for the hot path.

### D3 — Dangling reference policy. **Recommend: row-level failure, not auto-stub.**

Proposed default: if `OfferDetail` row references a merchant slug that exists in neither
the workbook nor the DAM, **fail that offer row**, log it in the report with the exact
missing slug, and continue with the rest of the sheet. The author fixes the sheet and
re-runs — which is safe because the job is idempotent.

Make this configurable via OSGi config (`strict` / `lenient-skip-reference` /
`create-stub`) so business can change their mind without a code change.

### D4 — Update semantics for multifields. **Needs business sign-off.**

If an offer currently has merchants `[A, B, C]` and the sheet says `[A, D]`, do we end up
with `[A, D]` (replace) or `[A, B, C, D]` (merge)? **Recommend replace** — the sheet is
the source of truth, and merge makes it impossible to ever remove a reference. But this
must be an explicit decision, because it is destructive and there is no undo unless we
implement D5.

### D5 — Version before update. **Recommend yes.**

Create a JCR version of every fragment before we modify it. Cheap to implement, and it
turns "the bulk upload wrecked 200 offers" from an incident into a rollback.

---

## 4. Target folder structure

```
/content/dam/aemcloudproject/cfs/
└── offer-listing/                 ← Offer Detail CFs        (node name = offerSlug)
    ├── cards/                     ← Offer Card CFs          (node name = offerCardSlug)
    ├── categories/                ← Offer Category CFs      (node name = offerCategoryslug)
    ├── offer-cta/                 ← Offer CTA CFs           (node name = offerCtaSlug)
    └── merchants/                 ← Merchant Details CFs    (node name = merchantSlug)
        └── venues/                ← Merchant Venue CFs      (node name = merchantvenueSlug)
```

Base path comes from OSGi config, never hard-coded. Missing folders are created as
`sling:OrderedFolder` on the fly.

---

## 5. Processing pipeline

```
1. PARSE       Excel → POJOs (row numbers retained for reporting)
2. VALIDATE    mandatory fields, slug format, in-sheet duplicates, FK resolution,
               category cycle detection
                    │
                    ├── DRY RUN? → skip to step 6, report "would create / would update"
                    │
3. RESOLVE     for each slug, path lookup → EXISTS (update) or ABSENT (create).
               Cache every result in an in-run map.
4. WRITE       bottom-up, batched commits:
                 venues → merchants → categories(pass 1) → categories(pass 2: parents)
                 → cards → CTAs → offers
5. VERSION     snapshot each fragment immediately before modification
6. REPORT      per-row outcome → XLSX written to DAM + summary in workflow log
```

**Dry-run mode is not a nice-to-have.** It is the thing that lets an author validate a
500-row sheet without touching production content, and it costs almost nothing once
validation is a separate phase.

---

## 6. Task breakdown

Estimates are dev-days, excluding QA. Adjust to your team's velocity.

### Phase 0 — De-risk (do this before committing to a sprint plan)

| # | Task | Est. | Notes |
|---|---|---|---|
| 0.1 | **Spike: Apache POI inside an AEMaaCS OSGi bundle** | 2 | POI + XMLBeans in OSGi is a known classloader trap. Prove `XSSFWorkbook` opens the sample sheet inside a deployed bundle before anything else. Check the ServiceMix POI bundle vs embedding via `bnd`. If this fails, fallback is CSV input — which changes the whole story. |
| 0.2 | **Data contract sign-off with business** | 1 | Sheet names, column names, mandatory vs optional, multi-value convention (§2.1), decisions D3 and D4. Produce a locked template `.xlsx` that business must use. |
| 0.3 | **Confirm CF model element names** | 0.5 | Read the actual models from `/conf/aemcloudproject/settings/dam/cfm/models` and map every Excel column → element name + data type. Mismatches here cause silent no-op writes. |

**Phase 0 total: ~3.5 days.** Do not skip 0.1.

### Phase 1 — Engine (the real work)

| # | Task | Est. | Acceptance criteria |
|---|---|---|---|
| 1.1 | Domain model + `WorkbookParser` | 2 | POJOs for all 6 sheets; spill-down grouping handled; every POJO retains its source sheet + row number; unit tests cover the sample workbook, trailing blank rows, missing sheets, extra columns. |
| 1.2 | `ValidationService` | 2.5 | Mandatory-field and slug-regex (`^[a-z0-9][a-z0-9-]*$`) checks; duplicate slug detection within a sheet; cross-sheet FK resolution; category parent cycle detection; returns a list of `ValidationIssue(severity, sheet, row, column, message)`. No repository access — pure, fully unit-testable. |
| 1.3 | `FragmentLocator` (slug → path) | 1 | Direct path lookup per D2; per-run cache; handles "exists but is not a Content Fragment" and "exists with wrong model" as errors, not crashes. |
| 1.4 | `IdGenerationService` | 1.5 | Cluster-safe (AEMaaCS runs multiple author pods — a naive counter node **will** collide). Optimistic-retry counter under `/var/aemcloudproject/sequences/` or a collision-resistant scheme. Must never reuse an ID; must never change an existing one. |
| 1.5 | `FragmentWriter` — create | 2 | `FragmentTemplate#createFragment`; auto-create folders; set simple, multi-value and reference elements; `JcrUtil.createValidName` on slugs; batched `resolver.commit()` every N (configurable, default 50) with `resolver.refresh()`. |
| 1.6 | `FragmentWriter` — update + versioning | 2 | Only writes changed elements (avoids pointless versions); multifield replace-vs-merge behind D4 config; creates a version before first modification. |
| 1.7 | `BulkUploadService` orchestrator | 2 | Enforces bottom-up order incl. the category two-pass; dry-run mode; partial-failure handling (one bad row never aborts the run); MDC correlation ID on every log line. |
| 1.8 | Service user + repoinit | 0.5 | Dedicated system user, minimal ACLs on the CF base path and the sequence path, wired via `ui.config` repoinit. **No admin resolver.** |

**Phase 1 total: ~13.5 days.**

### Phase 2 — Trigger + reporting (MVP ships at the end of this)

| # | Task | Est. | Acceptance criteria |
|---|---|---|---|
| 2.1 | Workflow model + `BulkUploadProcess` step | 1.5 | Launcher on `/content/dam/aemcloudproject/bulk-upload/inbox/.*\.xlsx`; step reads the payload asset, calls the engine, attaches the report path to the workflow metadata. |
| 2.2 | Report generator | 2 | XLSX (POI write) with a `Summary` sheet (counts by type/action) and a `Details` sheet (`sheet, row, slug, type, action, cfPath, message`). Actions: `CREATED / UPDATED / UNCHANGED / SKIPPED / FAILED`. Written to `.../bulk-upload/reports/<timestamp>-<filename>.xlsx`. |
| 2.3 | Failure + notification handling | 1 | Engine exceptions never leave a half-committed batch silently; workflow fails loudly; optional email to the initiator with the report link. |
| 2.4 | Logging + observability | 0.5 | Structured logs, run duration, per-phase counts. |

**Phase 2 total: ~5 days. → MVP is shippable here.**

### Phase 3 — Author UI

| # | Task | Est. |
|---|---|---|
| 3.1 | Granite UI upload page: file upload, target folder picker, **dry-run toggle**, mode selector (`upsert` / `create-only` / `update-only`), Run button | 3 |
| 3.2 | Run history + status list, download-report link | 2 |
| 3.3 | Oak property index for slug fields (dedup/reporting queries) | 1 |
| 3.4 | Downloadable blank template `.xlsx` from the UI | 0.5 |

**Phase 3 total: ~6.5 days.**

### Phase 4 — Hardening / optional

| # | Task | Est. |
|---|---|---|
| 4.1 | Performance test with a 5,000-row workbook; tune batch size; consider Sling Job + chunking | 2 |
| 4.2 | Rollback tooling (restore versions for a given run ID) | 2 |
| 4.3 | Optional publish/activation step post-import | 2 |
| 4.4 | Author documentation + runbook | 1 |

---

## 7. Suggested sequencing

| Sprint | Content | Outcome |
|---|---|---|
| **Sprint 1** | Phase 0 (all) + Tasks 1.1, 1.2, 1.3 | POI risk retired, data contract locked, parse + validate working with tests. **Demo: dry-run validation report on the sample sheet.** |
| **Sprint 2** | Tasks 1.4 – 1.8 | Engine complete, invocable from a JUnit test / dev servlet. |
| **Sprint 3** | Phase 2 (all) | **MVP live:** drop sheet in DAM → CFs created/updated → report generated. |
| **Sprint 4** | Phase 3 | Author-friendly UI. |
| **Sprint 5** | Phase 4 as prioritised | Scale + safety. |

The deliberate shape here: **something demoable at the end of every sprint**, and the
riskiest unknown (POI in OSGi) attacked in week one rather than discovered in week five.

---

## 8. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Apache POI classloading fails in OSGi | High — blocks everything | Spike 0.1 first; CSV fallback |
| Business changes the sheet format mid-build | High | Lock the template in 0.2; parser reads by **column header name**, not index |
| Long runs killed by AEMaaCS pod recycling | Medium | Async trigger + batched commits + idempotent re-run |
| ID generator collisions across author pods | Medium — duplicate IDs are hard to unwind | Task 1.4 explicitly designed cluster-safe; load-test it |
| Bad sheet mass-overwrites live offers | High | Dry-run mode + versioning (D5) + rollback (4.2) |
| Reference paths break if content is later moved | Medium | Document that CFs must be moved via the DAM Move wizard, which updates references |

---

## 9. Decisions needed from the lead

1. **D1** — Confirm: engine-first, workflow trigger for MVP, upload page in phase 2?
2. **D2** — Confirm slug (not offerID) as primary key and node name?
3. **D3** — Dangling reference policy: fail the row, skip the reference, or create a stub?
4. **D4** — Multifield update: replace or merge?
5. **D5** — Version-before-update: in scope for MVP or deferred?
6. Is **publish/activation** in scope, or is this author-only?
7. Expected **volume and frequency** — 50 rows monthly or 5,000 rows nightly? This changes the trigger choice (workflow vs Sling Job) and the performance budget.
8. Do we need **multi-locale** support (`/content/dam/aemcloudproject/en/...`), or single-locale for now?

---

## 10. One-paragraph summary for the standup

> We are building an ETL job, not a page feature. The logic lives in a UI-agnostic OSGi
> service that parses the workbook, validates it as a relational graph, and writes Content
> Fragments bottom-up so references always resolve. The MVP is triggered by a Workflow
> Launcher on a watched DAM folder — no custom UI needed to ship value — with an authoring
> page added in a later phase. Slug is the primary key and the node name, which keeps
> existence checks to an O(1) path lookup instead of a repository query. Every run is
> idempotent, supports a dry run, versions content before modifying it, and produces a
> downloadable XLSX report of everything created, updated, skipped, and failed.
