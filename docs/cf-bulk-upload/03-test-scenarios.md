# CF Bulk Upload – Test Scenarios

Use these scenarios to demo the tool, to retest after a change, or as a checklist on a new
environment. Scenarios 1–10 were first run on local AEM (author, port 4507) on 15 Oct 2026 with the
old per-model slug fields; they are written here in the current format (`slug` column, Dry run /
Final run buttons). The **Verified** column says when each one was last run in its current form;
scenarios not yet re-run since the 20 Oct changes are marked *to re-run*.

The workbooks are not stored in the repo (only `test-data/cf-bulk-upload/cf-import-combined.xlsx`
is). Rebuild them from the rows below, starting from **Download sample sheet** so every sheet has the
right headers, and upload them to `/content/dam/aemcloudproject/imports`.

**When:**
- **Before:** works on an instance with no fragments under `offer-listing`.
- **After:** needs the fragments from scenario 3 (final run of `demo-offers-fixed.xlsx`).

The fragment folder on the page is `/content/dam/aemcloudproject/cfs/offer-listing` (the default);
all paths below are under it (shown as `…`).

## Summary

| # | Scenario | Workbook | When | Expected result | Verified |
| --- | --- | --- | --- | --- | --- |
| 1 | Sample sheet | Download sample sheet | Any | `_Guide` + 6 header-only sheets, `slug` and required headers red, action dropdown | 20 Oct, 8080 |
| 2 | Create a full set, with two mistakes | `demo-offers.xlsx` (dry run) | Before | 6 Pass with generated IDs, 2 Fail; Final run stays disabled | 20 Oct, 8080 (equivalent rows) |
| 3 | Create a full set | `demo-offers-fixed.xlsx` (dry run, then final run) | Before | 8 Pass; fragments created; run report saved | 20 Oct, 8080 (equivalent rows) |
| 4 | Whole workbook rejected | `demo-workbook-errors.xlsx` | Any | Red alert listing all 3 problems; nothing checked or written | *to re-run* |
| 5 | Wrong file or folder picked | the `imports` folder / a non-Excel file / a bad folder | Any | "is not a DAM asset" / "is not an .xlsx file" / "No folder found at …" | 20 Oct, 8080 (folder) |
| 6 | CREATE twice | `demo-offers-fixed.xlsx` again | After | Every row Fail: "already exists" | *to re-run* |
| 7 | Update, clear, partial columns, `_Notes` | `demo-updates.xlsx` | After | 3 Pass with `(cleared: …)`, 1 Skip | *to re-run* |
| 8 | Row errors; the rest still runs | `demo-row-errors.xlsx` | After | 1 Pass + 10 Fail, each with its own message | *to re-run* |
| 9 | Report with data | Save report to DAM | After | New dated file; one row per fragment; offers by end date, latest first | 20 Oct, 8080 |
| 10 | Re-upload the report unchanged | the saved report | After | `_Guide` ignored, every sheet Skip, status ok | *to re-run* |
| 11 | Final run gate | any clean workbook | Any | Final run enabled only after a clean dry run of the same file and folder | 20 Oct, 8080 |
| 12 | Workbook changed after the dry run | re-upload after the dry run | Any | Final run refused: "The workbook changed after the dry run…" | 20 Oct, 8080 |
| 13 | DELETE left in place | `demo-deletes.xlsx` | After | Warn for referenced / published fragments, Fail for a missing one | 20 Oct, 8080 (equivalent rows) |
| 14 | Delete an offer and its CTA together | `demo-delete-chain.xlsx` | After | Both Pass, in dry run and final run | 20 Oct, 8080 (equivalent rows) |
| 15 | IDs continue from DAM | sample with one new offer | After | New offer gets the highest existing `offerID` + 1 | 20 Oct, 8080 |

Suggested demo order: 1 → 2 → 3 → 11 → 7 → 13 → 9 (about 15 minutes).

## 1. Sample sheet

**Steps:** on the upload page click **Download sample sheet**.

**Expected:**
- The browser downloads `cf-bulk-upload-sample.xlsx`, opening on `_Guide`.
- Sheets: `_Guide`, `merchant-venue`, `merchant-details`, `category`, `offer-card`, `offer-cta`,
  `offer-detail`; the six model sheets have only a header row: `action`, `slug`, then the model fields.
- Red headers: `slug` on every sheet; `merchantID`; `ID` (category); `offerID`, `offerTitle`.
- The action column (A2:A5001) has a dropdown with CREATE, UPDATE, DELETE; empty is allowed.

## 2. Create a full set, with two mistakes

**Workbook `demo-offers.xlsx`** (built from the sample sheet):

| Sheet | action | Key cells |
| --- | --- | --- |
| merchant-venue | CREATE | `slug` spice-garden-orchard, `name` Spice Garden Orchard, `address` `<p>1 Orchard Road, #02-10, Singapore 238801</p>`, `latitude` 1.3048, `longitude` 103.8318, `telephone` +65 6000 1234, `ophr` 11:00-22:00 |
| merchant-details | CREATE | `slug` spice-garden, `merchantName` Spice Garden, `merchantLogo` true, `merchantVenues` …/merchants/venues/spice-garden-orchard |
| category | CREATE | `slug` dining, `name` Dining |
| offer-card | CREATE | `slug` platinum-card, `name` Platinum Card |
| offer-cta | CREATE | `slug` book-table-spice-garden, `label` Book a table, `url` https://example.com/spice-garden/book, `deeplink` app://book/spice-garden |
| offer-detail | CREATE | `slug` spice-garden-weekend-deal, `offerTitle` Weekend dinner deal, summary, description, `hotPromo` true, `offerStartDate` 2026-11-01, `offerEndDate` 2026-12-31, `offerURL`, `offerLabel` 20% off, `offerEmail`, `offerTnc`, `offerType` Dining, `offerPromoCodes` SPICE20, `offerBadge` New, `offerTags` dining, `offerLocation` Orchard, `offerLocationType` In-store, `preview` false, `cache` true, references below |
| offer-detail | CREATE | **mistake 1:** `slug` Festive Bundle, `offerTitle` Festive bundle |
| offer-detail | CREATE | **mistake 2:** `slug` lunch-special, no `offerTitle` |

All three offers reference `offerCategories` …/categories/dining, `offerMerchants`
…/merchants/spice-garden, `offerCards` …/cards/platinum-card, `offerCta`
…/offer-cta/book-table-spice-garden, `offerOrigin` SCB. Dates are typed as text.

**Steps:** upload to `imports`, pick it, click **Dry run**.

**Expected (dry run, nothing written; red banner "Dry run: 2 rows failed", Final run disabled):**

```
merchant-venue   2 spice-garden-orchard       CREATE PASS …/merchants/venues/spice-garden-orchard
merchant-details 2 spice-garden               CREATE PASS …/merchants/spice-garden (merchantID 100001)
category         2 dining                     CREATE PASS …/categories/dining (ID 1001)
offer-card       2 platinum-card              CREATE PASS …/cards/platinum-card (ID 1001)
offer-cta        2 book-table-spice-garden    CREATE PASS …/offer-cta/book-table-spice-garden
offer-detail     2 spice-garden-weekend-deal  CREATE PASS …/offers/spice-garden-weekend-deal (offerID 100001)
offer-detail     3 Festive Bundle             CREATE FAIL slug: "Festive Bundle" must be lowercase letters, digits, - or _
offer-detail     4 lunch-special              CREATE FAIL offerTitle: required
```

## 3. Create a full set

**Workbook `demo-offers-fixed.xlsx`:** scenario 2 with `slug` festive-bundle and
`offerTitle` Weekday lunch special on the `lunch-special` row.

**Steps:** upload, **Dry run** (yellow banner, Final run enabled), then **Final run**.

**Expected:** 8 Pass; offers numbered 100001, 100002, 100003; the fragments appear in their
folders in Assets; no slug field in the fragments. Green banner "Final run done." with a link to
`/content/dam/aemcloudproject/reports/cf-upload-run-<date>-<time>.xlsx` (Results sheet: workbook,
folder, user, time, then the 8 rows). Final run is disabled again.

## 4. Whole workbook rejected

**Workbook `demo-workbook-errors.xlsx`:**

| Sheet | Headers | Rows |
| --- | --- | --- |
| offer-detail | `action`, `slug`, `Offer Title` | CREATE ok-row; UPDTE typo-row |
| offer-card | `slug`, `name` (no `action`) | gold-card, Gold |

**Expected:** a red alert, nothing checked or written:

```
Sheet "offer-detail", column C: header "Offer Title" must be a single word.
Sheet "offer-detail", row 3: action "UPDTE" must be CREATE, UPDATE or DELETE.
Sheet "offer-card": has no action column.
```

## 5. Wrong file or folder picked

| Pick | Expected message |
| --- | --- |
| The `imports` folder itself as the workbook | `/content/dam/aemcloudproject/imports is not a DAM asset.` |
| Any image or PDF (upload one to `imports` first; the path browser starts there) | `… is not an .xlsx file.` |
| No workbook | `Select a workbook first.` |
| No fragment folder | `Select the fragment folder first.` |
| A fragment folder that doesn't exist, e.g. typed `/content/dam/nope` | `No folder found at /content/dam/nope.` |

## 6. CREATE twice

**Steps:** after scenario 3, dry-run `demo-offers-fixed.xlsx` again.

**Expected:** every row Fail, e.g.
`Fragment: already exists at …/merchants/venues/spice-garden-orchard`; Final run stays disabled.

## 7. Update, clear, partial columns, `_Notes`

**Workbook `demo-updates.xlsx`** (only the columns listed; no `_Guide`):

| Sheet | Columns | Row |
| --- | --- | --- |
| `_Notes` | `note` | any text (sheet must be ignored) |
| merchant-venue | `action`, `slug`, `telephone`, `ophr` | `update` (lowercase), spice-garden-orchard, +65 6000 9999, *(empty)* |
| offer-cta | `action`, `slug`, `label`, `deeplink` | UPDATE, book-table-spice-garden, Reserve a table, *(empty)* |
| offer-detail | `action`, `slug`, `offerID`, `offerTitle`, `offerLabel`, `hotPromo` | UPDATE, spice-garden-weekend-deal, *(empty)*, Weekend dinner deal, 25% off, no |
| category | `action`, `slug`, `name` | *(no action)*, dining, Dining |

**Expected (dry run):**

```
merchant-venue 2 spice-garden-orchard       UPDATE PASS …/merchants/venues/spice-garden-orchard (cleared: ophr)
offer-cta      2 book-table-spice-garden    UPDATE PASS …/offer-cta/book-table-spice-garden (cleared: deeplink)
offer-detail   2 spice-garden-weekend-deal  UPDATE PASS …/offers/spice-garden-weekend-deal
category       – –                          –      SKIP Sheet: no row has an action (CREATE, UPDATE or DELETE)
```

Shows: lowercase action accepted (typed by hand, outside the dropdown); an empty cell clears the
field (and is listed); an empty ID on UPDATE keeps the ID; columns left out are not touched;
`_Notes` ignored; a sheet without actions is skipped.

## 8. Row errors; the rest still runs

**Workbook `demo-row-errors.xlsx`:**

| Sheet | Row | Purpose |
| --- | --- | --- |
| offer-detail | UPDATE lunch-special, title Weekday lunch special, start 2026-11-04, `offerCta` …/offer-cta/book-table-spice-garden | the valid row |
| offer-detail | CREATE spice-garden-weekend-deal | already exists |
| offer-detail | UPDATE no-such-offer | not found |
| offer-detail | UPDATE festive-bundle with `offerTitle` empty | required field emptied on UPDATE |
| offer-detail | CREATE bad-number, `offerID` 10001a | not a whole number |
| offer-detail | CREATE bad-boolean, `hotPromo` maybe | not yes/no |
| offer-detail | CREATE bad-date, `offerStartDate` 01/11/2026 | not a date |
| offer-detail | CREATE two-ctas, `offerCta` with two paths on two lines | single reference |
| offer-detail | UPDATE festive-bundle, `offerTnc` #CLEAR | retired token |
| category | CREATE shopping with an extra column `promoCode` X1 | column not in the model |
| offers | CREATE wrong-sheet | sheet name not a model |

Rows with CREATE also carry `offerTitle` Test offer and `offerStartDate` 2026-11-01 where needed,
so each row fails only for its own reason.

**Expected (dry run):**

```
offer-detail  2 lunch-special              UPDATE PASS …/offers/lunch-special
offer-detail  3 spice-garden-weekend-deal  CREATE FAIL Fragment: already exists at …/offers/spice-garden-weekend-deal
offer-detail  4 no-such-offer              UPDATE FAIL Fragment: not found at …/offers/no-such-offer
offer-detail  5 festive-bundle             UPDATE FAIL offerTitle: required
offer-detail  6 bad-number                 CREATE FAIL offerID: "10001a" is not a whole number
offer-detail  7 bad-boolean                CREATE FAIL hotPromo: "maybe" must be true/false, 1/0 or yes/no
offer-detail  8 bad-date                   CREATE FAIL offerStartDate: "01/11/2026" is not a date (use e.g. 2026-10-01T00:00:00.000+05:30)
offer-detail  9 two-ctas                   CREATE FAIL offerCta: takes one value, but the cell has several lines
offer-detail 10 festive-bundle             UPDATE FAIL offerTnc: #CLEAR is no longer used — leave the cell empty to clear the field
category      2 shopping                   CREATE FAIL promoCode: not a field of model category
offers        – –                          –      FAIL Sheet: "offers" is not a known CF model
```

Note: a column that is present but empty clears that field on UPDATE. The valid row fills
`offerCta` with its current value for that reason; without it, the result would read
`(cleared: offerCta)`.

## 9. Report with data

**Steps:** after scenario 3, **Save report to DAM**; click it twice within the same second to see
the name suffix.

**Expected:**
- "Saved /content/dam/aemcloudproject/reports/offer-listing-report-<date>-<time>.xlsx"; a second
  save is a new file (`…-2.xlsx` within the same second). No versions are created.
- One row per fragment (venue 1, merchant 1, category 1, card 1, CTA 1, offers 3), with the slug
  column holding each fragment's node name.
- `offer-detail` sorted by `offerEndDate`, latest first, offers without an end date last; the other
  sheets by ID.

## 10. Re-upload the report unchanged

**Steps:** upload the saved report from scenario 9 to `imports` and dry-run it.

**Expected:** `_Guide` ignored; all six sheets `SKIP Sheet: no row has an action (CREATE, UPDATE or
DELETE)`; status `ok`; Final run enabled (nothing to write).

## 11. Final run gate

**Steps:** dry-run a clean workbook (e.g. scenario 7's), then change the fragment folder to
`/content/dam/aemcloudproject/cfs`, then back to `…/offer-listing`.

**Expected:** Final run is enabled after the clean dry run, disabled when the folder (or the
workbook) changes, and enabled again when both match the dry run. After a dry run with any Fail
row it stays disabled.

## 12. Workbook changed after the dry run

**Steps:** dry-run a clean workbook, upload a new version of the same file, click **Final run**.

**Expected:** red alert "The workbook changed after the dry run. Run the dry run again."; nothing
written.

## 13. DELETE left in place

**Setup (after scenario 3):** CREATE an offer-cta `spare-cta` and publish it (on a local instance
without publish, set `cq:lastReplicationAction` = `Activate` on its `jcr:content`), and add the
category `dining` to a test page with a Content Fragment component.

**Workbook `demo-deletes.xlsx`:**

| Sheet | action | slug |
| --- | --- | --- |
| offer-cta | DELETE | book-table-spice-garden |
| offer-cta | DELETE | spare-cta |
| offer-cta | DELETE | no-such-cta |
| category | DELETE | dining |
| merchant-venue | DELETE | spice-garden-orchard |

**Expected (dry run; deletes listed after any CREATE/UPDATE rows, offers' sheets first):**

```
offer-cta      2 book-table-spice-garden DELETE WARN Not deleted: referenced by …/offers/spice-garden-weekend-deal, …/offers/festive-bundle, …/offers/lunch-special (remove the references first)
offer-cta      3 spare-cta               DELETE WARN Not deleted: still published (unpublish it first)
offer-cta      4 no-such-cta             DELETE FAIL Fragment: not found at …/offer-cta/no-such-cta
category       2 dining                  DELETE WARN Not deleted: referenced by …/offers/…, /content/<test page> (remove the references first)
merchant-venue 2 spice-garden-orchard    DELETE WARN Not deleted: referenced by …/merchants/spice-garden (remove the references first)
```

## 14. Delete an offer and its CTA together

**Workbook `demo-delete-chain.xlsx`** (after scenario 3, on a CTA used by one offer only):

| Sheet | action | slug |
| --- | --- | --- |
| offer-cta | DELETE | the CTA |
| offer-detail | DELETE | the one offer that uses it |

**Expected:** the offer row runs first (offer-detail is last in profile order, so first for
deletes); both Pass with `(to be deleted)` in the dry run and `(deleted)` in the final run. The
same works for a merchant and its venue.

## 15. IDs continue from DAM

**Steps:** after scenario 3, download the sample sheet, add one CREATE offer with an empty
`offerID`, dry-run it.

**Expected:** `…/offers/<slug> (offerID 100004)`: the highest existing `offerID` in DAM + 1, even
though the sheet has no other rows.

## Clean-up after testing

Delete the fragments created in the scenarios (venue, merchant, category, card, CTAs and the
offers), the test page, the test workbooks in `imports` and the reports in `reports`, so the
instance is back to the "Before" state.
