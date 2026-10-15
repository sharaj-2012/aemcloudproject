# CF Bulk Upload – Test Scenarios

Every scenario below was run on local AEM (author, port 4507) on 15 Oct 2026 and gave exactly the
result shown. Use them to demo the tool, to retest after a change, or as a checklist on a new
environment.

The workbooks are not stored in the repo (only `test-data/cf-bulk-upload/cf-import-combined.xlsx`
is). On 4507 they are in `/content/dam/aemcloudproject/imports`; on another environment, rebuild
them from the rows below, starting from a saved report so every sheet has the right headers.

**When:**
- **Before:** works on an instance with no fragments under `offer-listing`.
- **After:** needs the fragments from scenario 3 (real run of `demo-offers-fixed.xlsx`).

All paths below are under `/content/dam/aemcloudproject/cfs/offer-listing` (shown as `…`).

## Summary

| # | Scenario | Workbook | When | Expected result |
| --- | --- | --- | --- | --- |
| 1 | Empty template | Save report to DAM | Before | `_Guide` + 6 header-only sheets, red required headers, new version in Timeline |
| 2 | Create a full set, with two mistakes | `demo-offers.xlsx` (dry run) | Before | 6 Pass with generated IDs, 2 Fail |
| 3 | Create a full set | `demo-offers-fixed.xlsx` (dry run, then real run) | Before | 8 Pass; fragments created |
| 4 | Whole workbook rejected | `demo-workbook-errors.xlsx` | Any | Red alert listing all 3 problems; nothing checked or written |
| 5 | Wrong file picked | the `imports` folder / a non-Excel file | Any | "is not a DAM asset" / "is not an .xlsx file" |
| 6 | CREATE twice | `demo-offers-fixed.xlsx` again | After | Every row Fail: "already exists" |
| 7 | Update, clear, partial columns, `_Notes` | `demo-updates.xlsx` | After | 3 Pass with `(cleared: …)`, 1 Skip |
| 8 | Row errors; the rest still runs | `demo-row-errors.xlsx` | After | 1 Pass + 10 Fail, each with its own message |
| 9 | Report with data | Save report to DAM | After | One row per fragment, sorted by ID |
| 10 | Re-upload the report unchanged | the saved report | After | `_Guide` ignored, every sheet Skip, status ok |
| 11 | Legacy and hand-edited slug fields | report → UPDATE all rows | After | Report shows node names; UPDATE back-fills / resets the slug field |

Suggested demo order: 2 → 3 → 7 → 8 (about 10 minutes).

## 1. Empty template

**Steps:** on the upload page click **Save report to DAM**, then **Open in Assets**.

**Expected:**
- File `/content/dam/aemcloudproject/reports/offer-listing-report.xlsx`, opening on `_Guide`.
- Sheets: `_Guide`, `merchant-venue`, `merchant-details`, `category`, `offer-card`, `offer-cta`,
  `offer-detail`; the six model sheets have only a header row.
- Required headers in red: `merchantVenueSlug`; `merchantID`, `merchantSlug`; `ID`,
  `offerCategorySlug`; `offerCardSlug`; `offerCtaSlug`; `offerID`, `offerTitle`, `offerSlug`.
- The page shows "Saved … · previous file kept as version <date time>"; the asset's Timeline
  lists that version.

## 2. Create a full set, with two mistakes

**Workbook `demo-offers.xlsx`** (built from the empty template):

| Sheet | action | Key cells |
| --- | --- | --- |
| merchant-venue | CREATE | `merchantVenueSlug` spice-garden-orchard, `name` Spice Garden Orchard, `address` `<p>1 Orchard Road, #02-10, Singapore 238801</p>`, `latitude` 1.3048, `longitude` 103.8318, `telephone` +65 6000 1234, `ophr` 11:00-22:00 |
| merchant-details | CREATE | `merchantSlug` spice-garden, `merchantName` Spice Garden, `merchantLogo` true, `merchantVenues` …/merchants/venues/spice-garden-orchard |
| category | CREATE | `offerCategorySlug` dining, `name` Dining |
| offer-card | CREATE | `offerCardSlug` platinum-card, `name` Platinum Card |
| offer-cta | CREATE | `offerCtaSlug` book-table-spice-garden, `label` Book a table, `url` https://example.com/spice-garden/book, `deeplink` app://book/spice-garden |
| offer-detail | CREATE | `offerSlug` spice-garden-weekend-deal, `offerTitle` Weekend dinner deal, summary, description, `hotPromo` true, `offerStartDate` 2026-11-01, `offerEndDate` 2026-12-31, `offerURL`, `offerLabel` 20% off, `offerEmail`, `offerTnc`, `offerType` Dining, `offerPromoCodes` SPICE20, `offerBadge` New, `offerTags` dining, `offerLocation` Orchard, `offerLocationType` In-store, `preview` false, `cache` true, references below |
| offer-detail | CREATE | **mistake 1:** `offerSlug` Festive Bundle, `offerTitle` Festive bundle |
| offer-detail | CREATE | **mistake 2:** `offerSlug` lunch-special, no `offerTitle` |

All three offers reference `offerCategories` …/categories/dining, `offerMerchants`
…/merchants/spice-garden, `offerCards` …/cards/platinum-card, `offerCta`
…/offer-cta/book-table-spice-garden, `offerOrigin` SCB. Dates are typed as text.

**Steps:** upload to `imports`, pick it, keep **Dry run** ticked, **Run**.

**Expected (dry run, nothing written):**

```
merchant-venue   2 spice-garden-orchard       CREATE PASS …/merchants/venues/spice-garden-orchard
merchant-details 2 spice-garden               CREATE PASS …/merchants/spice-garden (merchantID 100001)
category         2 dining                     CREATE PASS …/categories/dining (ID 1001)
offer-card       2 platinum-card              CREATE PASS …/cards/platinum-card (ID 1001)
offer-cta        2 book-table-spice-garden    CREATE PASS …/offer-cta/book-table-spice-garden
offer-detail     2 spice-garden-weekend-deal  CREATE PASS …/offers/spice-garden-weekend-deal (offerID 100001)
offer-detail     3 Festive Bundle             CREATE FAIL offerSlug: "Festive Bundle" must be lowercase letters, digits, - or _
offer-detail     4 lunch-special              CREATE FAIL offerTitle: required
```

## 3. Create a full set

**Workbook `demo-offers-fixed.xlsx`:** scenario 2 with `offerSlug` festive-bundle and
`offerTitle` Weekday lunch special on the `lunch-special` row.

**Steps:** dry run, then untick **Dry run** and **Run**.

**Expected:** 8 Pass; offers numbered 100001, 100002, 100003; the fragments appear in their
folders in Assets. Status `ok`.

## 4. Whole workbook rejected

**Workbook `demo-workbook-errors.xlsx`:**

| Sheet | Headers | Rows |
| --- | --- | --- |
| offer-detail | `action`, `offerSlug`, `Offer Title` | CREATE ok-row; UPDTE typo-row |
| offer-card | `offerCardSlug`, `name` (no `action`) | gold-card, Gold |

**Expected:** a red alert, nothing checked or written:

```
Sheet "offer-detail", column C: header "Offer Title" must be a single word.
Sheet "offer-detail", row 3: action "UPDTE" must be CREATE or UPDATE.
Sheet "offer-card": has no action column.
```

## 5. Wrong file picked

| Pick | Expected message |
| --- | --- |
| The `imports` folder itself | `/content/dam/aemcloudproject/imports is not a DAM asset.` |
| Any image or PDF (upload one to `imports` first; the path browser starts there) | `… is not an .xlsx file.` |
| Nothing | `Select a workbook first.` |

## 6. CREATE twice

**Steps:** after scenario 3, dry-run `demo-offers-fixed.xlsx` again.

**Expected:** every row Fail, e.g.
`Fragment: already exists at …/merchants/venues/spice-garden-orchard`.

## 7. Update, clear, partial columns, `_Notes`

**Workbook `demo-updates.xlsx`** (only the columns listed; no `_Guide`):

| Sheet | Columns | Row |
| --- | --- | --- |
| `_Notes` | `note` | any text (sheet must be ignored) |
| merchant-venue | `action`, `merchantVenueSlug`, `telephone`, `ophr` | `update` (lowercase), spice-garden-orchard, +65 6000 9999, *(empty)* |
| offer-cta | `action`, `offerCtaSlug`, `label`, `deeplink` | UPDATE, book-table-spice-garden, Reserve a table, *(empty)* |
| offer-detail | `action`, `offerID`, `offerTitle`, `offerSlug`, `offerLabel`, `hotPromo` | UPDATE, *(empty)*, Weekend dinner deal, spice-garden-weekend-deal, 25% off, no |
| category | `action`, `offerCategorySlug`, `name` | *(no action)*, dining, Dining |

**Expected (dry run):**

```
merchant-venue 2 spice-garden-orchard       UPDATE PASS …/merchants/venues/spice-garden-orchard (cleared: ophr)
offer-cta      2 book-table-spice-garden    UPDATE PASS …/offer-cta/book-table-spice-garden (cleared: deeplink)
offer-detail   2 spice-garden-weekend-deal  UPDATE PASS …/offers/spice-garden-weekend-deal
category       – –                          –      SKIP Sheet: no row has an action (CREATE or UPDATE)
```

Shows: lowercase action accepted; an empty cell clears the field (and is listed); an empty ID on
UPDATE keeps the ID; columns left out are not touched; `_Notes` ignored; a sheet without actions
is skipped.

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

**Steps:** after scenario 3, **Save report to DAM**.

**Expected:** one row per fragment (venue 1, merchant 1, category 1, card 1, CTA 1, offers 3),
offers sorted by `offerID`; a new version in the Timeline.

## 10. Re-upload the report unchanged

**Steps:** upload the saved report from scenario 9 to `imports` and dry-run it.

**Expected:** `_Guide` ignored; all six sheets `SKIP Sheet: no row has an action`; status `ok`.

## 11. Legacy and hand-edited slug fields

**Setup (after scenario 3):** empty the `offerSlug` field of spice-garden-weekend-deal in the
fragment editor (a legacy fragment), and change festive-bundle's `offerSlug` field to xmas-bundle
(a hand edit). The paths stay the same.

**Steps:** **Save report to DAM**, set `UPDATE` on every `offer-detail` row, upload, real run.

**Expected:**
- The report's `offerSlug` column shows spice-garden-weekend-deal, festive-bundle, lunch-special
  (the node names, not the stored values).
- All three rows Pass, with no `(cleared: …)`.
- Afterwards the `offerSlug` fields are spice-garden-weekend-deal (back-filled), festive-bundle
  (reset) and lunch-special; no path changed.

## Clean-up after testing

Delete the fragments created in scenario 3 (venue, merchant, category, card, CTA and the three
offers) and save the report again, so the instance is back to the "Before" state.
