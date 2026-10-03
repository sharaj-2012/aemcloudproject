# CF Bulk Uploader — Input Format (v1)

This is the contract between authors and the uploader. The parser, validator and writer
all implement exactly what is described here — if behaviour and this doc disagree, it's a bug.

**Source of truth:** `test-data/cf-bulk-upload/cf-import-combined.xlsx` — the only reference
workbook. Its layout is the format; this doc spells out the rules behind it.

## File

- Excel workbook, `.xlsx` only (no `.xls`, no `.csv`).
- Max file size and max row count are OSGi-configurable (defaults: 10 MB, 5,000 rows).
- Formulas are allowed — the cell's calculated value is used.

## Sheets — one per model

- **The sheet name is the model name** — the model's node name under the conf root
  (default `/conf/aemcloudproject/settings/dam/cfm/models`), e.g. `offer-detail`, `merchant-venue`.
- Sheets starting with `_` (e.g. `_README`) are ignored, so authors can keep notes in the file.
- A sheet whose name isn't a model with a [model profile](#model-profiles) is reported as an
  error and skipped; the other sheets still run.
- **Sheet order and row order don't matter.** One workbook can create venues, merchants that
  reference those venues, and offers that reference those merchants. No more
  `01-venues.xlsx` … `06-offers.xlsx`.

## Rows and columns

- **Row 1 is the header.** Data starts on row 2. Completely blank rows are skipped.
- Every header is a **field Property Name from the model** (the name, not the editor label —
  `offerStartDate`, not `Offer Start Date`). Case-sensitive. Column order doesn't matter.
- A header that isn't a field of the sheet's model is an error for the whole sheet
  (almost always a typo, and silently ignoring it would drop data).
- Every message in the report points at `Sheet › Excel row › column`, using the row number
  the author sees in Excel.

## Model profiles

The uploader is generic; what's specific to a model lives in OSGi config, not in the
spreadsheet. A profile says, per model:

| Setting      | Meaning |
|--------------|---------|
| `folder`     | Folder for this model's fragments, relative to the **target folder** chosen on the upload form (default `/content/dam/aemcloudproject/cfs`). Created as `sling:OrderedFolder` if missing. |
| `nameField`  | Field whose value becomes the fragment's node name. |
| `titleField` | Field whose value becomes the fragment's title. |

Profiles for this project:

| Sheet / model      | `folder`                   | `nameField`         | `titleField`   |
|--------------------|----------------------------|---------------------|----------------|
| `offer-detail`     | `offer-listing/offers`     | `offerSlug`         | `offerTitle`   |
| `category`         | `offer-listing/categories` | `offerCategorySlug` | `name`         |
| `merchant-details` | `offer-listing/merchants`  | `merchantSlug`      | `merchantName` |
| `merchant-venue`   | `offer-listing/venues`     | `merchantVenueSlug` | `name`         |
| `offer-card`       | `offer-listing/cards`      | `offerCardSlug`     | `name`         |
| `offer-cta`        | `offer-listing/ctas`       | `offerCtaSlug`      | `label`        |

### Fragment name and title

- **Name** = the `nameField` value. It must already be a valid name — lowercase letters,
  digits, `-`, `_` — otherwise the row is an error. It is never auto-slugified, so what the
  author types is exactly the path they get.
- **Upsert key** = target folder + profile `folder` + name. Exists → update, otherwise create.
- **Title** = the `titleField` value; if that cell is empty on create, the name is used.
  On update, an empty title cell keeps the current title.
- The same name twice in one sheet is an error (both rows reported) — otherwise which one
  wins would depend on order.

## Cell values by field type

The type of each field is read from the model, so headers carry no type hints.

| Model field type   | Cell value |
|--------------------|------------|
| Single-line text   | Text as-is. Leading/trailing whitespace trimmed. |
| Multi-line text    | Text as-is, line breaks preserved. Written with the field's default content type — all multi-line fields in this project are `text/html`, so put HTML in the cell. |
| Number (integer)   | Numeric cell, or text like `10001`. `3.5` is an error. |
| Number (fraction)  | Numeric cell, or text like `1.2935`. |
| Boolean            | Excel `TRUE`/`FALSE`, `1`/`0`, or text `true/false`, `yes/no` (case-insensitive). |
| Date and time      | ISO-8601 text — `2026-10-01T00:00:00.000+05:30`, `2026-10-01T09:30`, `2026-10-01` — or an Excel date cell. Values without an offset use the configured time zone (OSGi, default `Asia/Kolkata`). |
| Enumeration        | Option **value** (not its label). |
| Tags               | Tag IDs, e.g. `aemcloudproject:offers/dining`. |
| Content reference  | Absolute repository path (`/content/dam/…`, `/content/aemcloudproject/…`) **or** an external `http(s)://` URL, stored as-is. |
| Fragment reference | Absolute path **or** bare name — see below. |
| JSON object        | Raw JSON text. Must parse. |

### Multi-value fields

For fields set to *Render as multiple field* and multi-fragment references:
**one value per line** inside the cell (Alt+Enter in Excel, Option+Return on Mac).
Each line is trimmed; blank lines are ignored.

```
/content/dam/aemcloudproject/cfs/offer-listing/cards/platinum-card
/content/dam/aemcloudproject/cfs/offer-listing/cards/gold-card
```

### Fragment references

Each value in a fragment-reference cell is either:

- **an absolute path** — `/content/dam/aemcloudproject/cfs/offer-listing/cards/gold-card`, or
- **a bare name** — `gold-card`. Resolved through the profile of the model the field allows:
  `offerCards` allows `offer-card` → `<target folder>/offer-listing/cards/gold-card`.
  No search is involved, so a name can never be ambiguous.

Either way the target must exist in DAM **or be created by a row in the same workbook**, and
must use the model the field allows. Names and paths can be mixed in one cell.

```
gold-card
/content/dam/aemcloudproject/cfs/offer-listing/cards/infinite-card
```

Self-references (`category.parent`, `offer-card.parent`) work the same way — a row may point
at a row below it.

> The writer runs in two passes — pass 1 creates/updates every fragment's non-reference
> fields, pass 2 sets reference fields — so references resolve regardless of order, cycles included.

### Missing referenced content

| Reference          | Target missing → |
|--------------------|------------------|
| Fragment reference | **error** — row not written. A row that references an *errored* row is also an error, with the reason. |
| Content reference (path) | **warning** — row written, flagged in the report (assets are often uploaded later). |
| Content reference (URL)  | not checked. |

### Empty cells vs. clearing a field

Upsert makes "empty" ambiguous, so there's an explicit marker:

| Cell             | On create                 | On update |
|------------------|---------------------------|-----------|
| empty            | field keeps model default | field **left unchanged** |
| `#CLEAR` (exact) | field empty               | field **emptied** |
| any other value  | field set                 | field overwritten |

### Required fields

Fields marked *Required* in the model must have a value when a fragment is **created**.
On update an empty cell is fine (field unchanged), but `#CLEAR` on a required field is an error.
The `nameField` column must be present in the sheet and filled on every row.

## Behaviour summary

- **Upsert**, keyed on folder + name. Updating a fragment whose existing model differs from
  the sheet's model is an error (never silently re-modelled).
- Rows are independent: one bad row is reported and skipped; it doesn't fail the file.
- **Dry run** runs full validation and reports what *would* be created/updated/failed,
  without writing anything.
- Runs as a background job; the page polls for progress and offers the report for download.

## Out of scope for v1

Variations, per-cell content-type override, asset upload from the file, deleting fragments,
publishing, auto-creating stub fragments for unknown references. All can be added later
without breaking this format.

---

## Model field reference (this project)

`*` = required on create. **bold** = profile `nameField`. Arrows show the model a fragment reference allows.

### `merchant-venue`

| Property name           | Type |
|-------------------------|------|
| **`merchantVenueSlug`** | Single-line text `*` |
| `name`                  | Single-line text |
| `address`               | Multi-line text (HTML) |
| `latitude`              | Number (fraction) |
| `longitude`             | Number (fraction) |
| `telephone`             | Single-line text |

### `merchant-details`

| Property name      | Type |
|--------------------|------|
| `merchantID`       | Number (integer) `*` |
| **`merchantSlug`** | Single-line text `*` |
| `merchantName`     | Single-line text |
| `merchantLogo`     | Content reference |
| `merchantVenues`   | Fragment reference, multiple → `merchant-venue` |

### `category`

| Property name           | Type |
|-------------------------|------|
| `ID`                    | Number (integer) `*` |
| **`offerCategorySlug`** | Single-line text `*` |
| `name`                  | Single-line text |
| `categoryIconPath`      | Single-line text |
| `parent`                | Fragment reference → `category` |

### `offer-card`

| Property name       | Type |
|---------------------|------|
| `id`                | Number (integer) |
| **`offerCardSlug`** | Single-line text `*` |
| `name`              | Single-line text |
| `logo`              | Content reference |
| `parent`            | Fragment reference → `offer-card` |

### `offer-cta`

| Property name      | Type |
|--------------------|------|
| **`offerCtaSlug`** | Single-line text `*` |
| `label`            | Single-line text |
| `url`              | Content reference (external URLs allowed) |
| `deeplink`         | Single-line text |

### `offer-detail`

| Property name      | Type |
|--------------------|------|
| `offerID`          | Number (integer) `*` |
| **`offerSlug`**    | Single-line text `*` |
| `offerTitle`       | Single-line text `*` |
| `offerSummary`     | Multi-line text (HTML) |
| `offerDescription` | Multi-line text (HTML) |
| `offerImage`       | Content reference |
| `alternateText`    | Single-line text |
| `hotPromo`         | Boolean |
| `offerStartDate`   | Date and time |
| `offerEndDate`     | Date and time |
| `offerURL`         | Single-line text |
| `offerEmail`       | Single-line text |
| `offerTnc`         | Multi-line text (HTML) |
| `offerCategories`  | Fragment reference, multiple → `category` |
| `offerMerchants`   | Fragment reference, multiple → `merchant-details` |
| `offerCards`       | Fragment reference, multiple → `offer-card` |
| `offerCta`         | Fragment reference, multiple → `offer-cta` |
| `offerOrigin`      | Single-line text |
| `brCode`           | Single-line text |
| `qrCode`           | Single-line text |
