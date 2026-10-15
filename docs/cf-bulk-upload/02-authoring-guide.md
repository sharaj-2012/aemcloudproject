# CF Bulk Upload – Authoring Guide

The CF bulk upload lets you create and change many content fragments at once from one Excel workbook, instead of editing them one by one in AEM. This guide takes you from your first workbook to a finished upload; if you are new, read it top to bottom once, then use the later sections as a reference.

## What the tool does

You fill an Excel workbook, upload it to DAM, and the tool creates or updates one content fragment per row. It checks every row first and tells you exactly what is wrong before anything is written.

**Key terms**

| Term | Meaning |
| --- | --- |
| Content fragment (CF) | A piece of structured content in AEM, e.g. one offer, one merchant or one CTA |
| Model | The template a fragment follows: which fields it has and their types. Each sheet in the workbook is one model |
| Field | One value inside a fragment, e.g. `offerTitle`. Each column in a sheet is one field |
| Slug | The fragment's short technical name, e.g. `summer-sale`. It becomes the last part of the fragment's path and must be unique in its folder |
| Reference | A field that points to another fragment or an image by its full path, e.g. an offer pointing to its CTA |
| Action | What a row should do: `CREATE` a new fragment or `UPDATE` an existing one |
| Dry run | A test run: every row is checked, nothing is written |
| Real run | The run that actually creates and updates fragments |
| Report | An Excel file with every existing fragment, in exactly the layout the upload expects; your starting point for any change |
| DAM | AEM Assets, where the workbooks, the report and the fragments are stored |

## Before you start

You need AEM author access with rights to the folders below, and Excel.

| What | Where |
| --- | --- |
| The upload page | AEM → **Tools** (hammer icon) → **SCB Tools** → **Content Fragment Bulk Upload** |
| Folder for workbooks you upload | `/content/dam/aemcloudproject/imports` |
| Saved report (your starting workbook) | `/content/dam/aemcloudproject/reports/offer-listing-report.xlsx` |
| Where fragments are created | Under `/content/dam/aemcloudproject/cfs/offer-listing/` (see Sheets, folders and names) |

- **Permissions:** you need to upload files to the imports folder, and to create and modify content in the fragment folders. If an upload fails with "could not be written", ask the AEM team to check your rights.
- **Excel:** any recent version. Keep the file as `.xlsx`; don't save it as `.xls` or `.csv`.
- **First time?** Always do a dry run first; it never changes anything.

## Quick start

Always start from a fresh report, check with a dry run, then run for real.

1. In AEM, open **Tools → SCB Tools → Content Fragment Bulk Upload**.
2. Click **Save report to DAM**. Every existing fragment is saved as one Excel file, one sheet per model, in the right layout: **/content/dam/aemcloudproject/reports/offer-listing-report.xlsx**. The previous report is kept as a version (see its **Timeline** in Assets). Click **Open in Assets** under the button and **Download** the file.
3. Edit the workbook:
   - to change a fragment, type `UPDATE` in its **action** cell and edit the cells you want to change (emptying a cell clears that field);
   - to add a fragment, add a row at the bottom of the right sheet, type `CREATE` and fill its cells;
   - leave the action empty on rows you don't want to touch.
4. Upload the workbook to DAM, into **/content/dam/aemcloudproject/imports**.
5. On the upload page, pick the workbook, keep **Dry run** ticked and click **Run**. Nothing is written; you see what would happen to each row.
6. Fix any **Fail** rows in Excel, upload the file again and repeat the dry run until it's clean.
7. Untick **Dry run** and click **Run**. The fragments are created or updated.
8. Publish the changed fragments in AEM when you're ready. The upload never publishes anything.

## Worked example: a new offer from start to finish

Goal: add a new offer for the restaurant "Spice Garden", with its venue, a booking CTA, and the existing category `dining`. One workbook, one upload.

**Step 1: get the workbook.** Click **Save report to DAM**, then **Open in Assets** and **Download** the file. Keep all existing rows; you only add rows below them.

**Step 2: add the venue** (sheet `merchant-venue`)

| action | name | merchantVenueSlug | address | telephone |
| --- | --- | --- | --- | --- |
| CREATE | Spice Garden Orchard | spice-garden-orchard | `<p>1 Orchard Road, #02-10</p>` | +65 6000 1234 |

**Step 3: add the merchant** (sheet `merchant-details`). Leave `merchantID` empty; it is numbered automatically. Point to the venue by its full path.

| action | merchantID | merchantName | merchantSlug | merchantVenues |
| --- | --- | --- | --- | --- |
| CREATE |  | Spice Garden | spice-garden | /content/dam/aemcloudproject/cfs/offer-listing/merchants/venues/spice-garden-orchard |

**Step 4: add the CTA** (sheet `offer-cta`)

| action | label | offerCtaSlug | url |
| --- | --- | --- | --- |
| CREATE | Book a table | book-table-spice-garden | https://example.com/spice-garden/book |

**Step 5: add the offer** (sheet `offer-detail`). Leave `offerID` empty. References use full paths; several categories or merchants go on separate lines in one cell (Alt+Enter), but `offerCta` takes exactly one.

| action | offerID | offerTitle | offerSlug | offerStartDate | offerCategories | offerMerchants | offerCta |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CREATE |  | Weekend dinner deal | spice-garden-weekend-deal | 2026-11-01 | /content/dam/aemcloudproject/cfs/offer-listing/categories/dining | /content/dam/aemcloudproject/cfs/offer-listing/merchants/spice-garden | /content/dam/aemcloudproject/cfs/offer-listing/offer-cta/book-table-spice-garden |

Fill any other columns you need (summary, image, dates and so on); leave the rest empty.

**Step 6: upload and check.** Upload the file to `/content/dam/aemcloudproject/imports`. On the upload page pick it, keep **Dry run** ticked and click **Run**. You should see four green **Pass** rows, e.g. `…/merchants/spice-garden (merchantID 100003)`, and grey **Skip** lines for sheets you didn't change.

**Step 7: write it.** Untick **Dry run** and click **Run**. Check the four fragments in Assets, then publish them when you're ready.

References are stored as paths, so a row can point to a fragment created by another row of the same upload, as the offer does here. Just make sure the path matches the folder and slug exactly.

## Workbook layout

One `.xlsx` file, one sheet per model, field names in row 1 and one fragment per row below.

| Part | Rule |
| --- | --- |
| File | `.xlsx` only (not `.xls` or `.csv`), at most 10 MB |
| Sheet name | Exactly the model's name: `merchant-venue`, `merchant-details`, `category`, `offer-card`, `offer-cta`, `offer-detail`. Any other sheet name fails |
| Sheet order | Any order; the report uses venues → merchants → categories → cards → CTAs → offers |
| Row 1 | Column headers: `action` plus the model's field names, spelled exactly as in the model (case-sensitive, one word, no spaces) |
| Red headers | Required fields (see IDs and required fields) |
| Column order | Any order |
| Columns | Only `action` and the sheet's slug column are needed; leave out columns you don't use |
| Rows 2 onwards | One fragment per row; completely empty rows are ignored |
| Formulas | Allowed; the calculated value is used |
| Sheets starting with \_ | Ignored by the upload. The report's first sheet, \_Guide, is a short version of this guide with a table of every sheet's folder, slug, title, ID and required columns. Keep it or delete it; you can also add your own \_Notes sheet |

A header with a space (`Offer Title`) or a sheet without an `action` column rejects the whole workbook. Nothing is written until it's fixed.

## The action column

Each row's `action` cell says what to do with that fragment.

| action | What happens | The fragment must |
| --- | --- | --- |
| `CREATE` | A new fragment is made | Not exist yet, otherwise the row fails with "already exists" |
| `UPDATE` | An existing fragment is changed | Exist already, with the same model, otherwise the row fails with "not found" |
| empty | The row is skipped: not checked, not written, not in the results | — |

- Case doesn't matter: `create`, `Create` and `CREATE` are the same. Spaces around the word are ignored.
- Any other value (`UPDTE`, `DELETE`) rejects the whole workbook with a message naming the sheet and row.
- If no row in a sheet has an action, that sheet shows one grey **Skip** line in the results.
- The upload never deletes fragments. Delete them by hand in AEM.

## Sheets, folders and names

The sheet decides the folder; the slug column decides the fragment's name, and with it its path.

All folders below are under **/content/dam/aemcloudproject/cfs/**.

| Sheet | Folder | Name (slug) column | Title column | ID column |
| --- | --- | --- | --- | --- |
| `merchant-venue` | `offer-listing/merchants/venues` | `merchantVenueSlug` | `name` | — |
| `merchant-details` | `offer-listing/merchants` | `merchantSlug` | `merchantName` | `merchantID` |
| `category` | `offer-listing/categories` | `offerCategorySlug` | `name` | `ID` |
| `offer-card` | `offer-listing/cards` | `offerCardSlug` | `name` | `ID` |
| `offer-cta` | `offer-listing/offer-cta` | `offerCtaSlug` | `label` | — |
| `offer-detail` | `offer-listing/offers` | `offerSlug` | `offerTitle` | `offerID` |

Example: sheet `offer-cta`, slug `book-now` → `/content/dam/aemcloudproject/cfs/offer-listing/offer-cta/book-now`.

**Slug rules**

- Only lowercase letters, digits, `-` and `_`: `book-now`, `summer_sale_2026`. Not `Book Now`, `café` or `book.now`.
- The slug is never changed or tidied up for you: what you type is the path you get.
- On UPDATE, the slug finds the fragment; changing it doesn't rename the fragment, it points at a different one: "not found" if nothing is there, or that other fragment gets updated if it exists. In a saved report the slug column always shows each fragment's real name, even when its slug field in AEM is empty or was edited by hand; an UPDATE then puts the field back in line.

**Title**

- The title column sets the fragment's title in Assets.
- CREATE with an empty title uses the slug as the title.
- UPDATE with an empty title clears the title field, but the title shown in Assets stays as it is.

## Filling in cells

Type each value the way its field expects; the Field reference at the end says which type each column is.

| Field type | What to type | Example |
| --- | --- | --- |
| Text (single line) | Plain text | `Book Now` |
| Text (multi-line / rich text) | HTML, as the report shows it | `<p>Enjoy 20% off.</p>` |
| Number, whole | Digits only | `10001` |
| Number, decimal | Digits with a `.` | `1.2935` |
| Yes / no | `true` / `false`, `1` / `0` or `yes` / `no` (any case) | `true` |
| Date and time | Text in one of three formats (below) | `2026-10-01T00:00:00.000+05:30` |
| Fragment reference, one | The full path of the fragment | `/content/dam/aemcloudproject/cfs/offer-listing/categories/dining` |
| Fragment reference, several | One full path per line inside the cell (Alt+Enter, or Option+Enter on Mac, for a new line) | two paths on two lines |
| Image or file reference | The full DAM path | `/content/dam/aemcloudproject/offers/summer.jpg` |

**Dates**

- `2026-10-01T00:00:00.000+05:30`: date, time and time zone, kept exactly as written (this is what the report gives you).
- `2026-10-01T09:30`: date and time, in the AEM server's time zone.
- `2026-10-01`: date only, at midnight in the AEM server's time zone.
- Type dates as text. Format the column as **Text** in Excel first, or start the value with an apostrophe (`'2026-10-01`); otherwise Excel turns it into its own date format, which the upload can't read.

**References**

- Always the full path starting with `/content/`, never just a name like `dining`.
- Several values only in fields that allow several (the Field reference marks them). Two lines in a single-value field fail the row.
- The upload doesn't check yet that a referenced fragment or image exists, so copy paths carefully. A safe way is to copy them from a fresh report.

**Empty cells and clearing**

- CREATE: an empty cell leaves that field empty.
- UPDATE: an empty cell **clears** that field. Start from a fresh report, so the cells already hold the current values; delete a value to clear it.
- Exception: an empty ID cell on UPDATE keeps the current ID.
- A column you leave out of the sheet is never touched. To change just one field, a sheet with `action`, the slug column and that field is enough.
- `#CLEAR` is no longer used; typing it fails the row. Leave the cell empty instead.
- The results show which fields a row clears, in the dry run too: `…/book-now (cleared: deeplink)`.

## IDs and required fields

Leave the ID empty on new rows and the upload numbers them for you; required fields must be filled on CREATE.

**Automatic IDs** (`offerID`, `merchantID`, category `ID`, offer-card `ID`)

- A CREATE row with an empty ID gets the next number: the highest ID in that sheet + 1, then +1 for each further new row, top to bottom.
- The highest ID counts every row in the sheet, including rows with an empty action. That's why you keep the existing rows from the report in the sheet.
- If the sheet has no IDs at all, numbering starts at 100001 (merchants, offers) or 1001 (categories, offer cards).
- An ID you type yourself is kept as it is.
- A row that fails doesn't use up a number.
- UPDATE never changes the ID; leave it as it is.
- The new ID is shown in the results, e.g. `…/offers/summer-sale (offerID 10004)`.

IDs are worked out from the sheet only. A workbook with just new rows starts again at 100001 / 1001 and can repeat an ID that already exists. Always start from a fresh report and add new rows below the existing ones.

**Required fields** (red headers in the report)

| Sheet | Required |
| --- | --- |
| `merchant-venue` | `merchantVenueSlug` |
| `merchant-details` | `merchantID`, `merchantSlug` |
| `category` | `ID`, `offerCategorySlug` |
| `offer-card` | `offerCardSlug` |
| `offer-cta` | `offerCtaSlug` |
| `offer-detail` | `offerID`, `offerTitle`, `offerSlug` |

- CREATE: every required field needs a value, otherwise the row fails with e.g. `offerTitle: required`. An automatic ID counts as filled.
- UPDATE: a required column in the sheet can't be left empty, because an empty cell would clear it; the row fails with `offerTitle: required`. An empty ID cell keeps the current ID.
- Required fields come from the content fragment model. When a field is made required in the model, the upload enforces it from then on.

## Running an upload and reading the results

A dry run and a real run run exactly the same checks; only the real run writes.

**Dry run vs real run**

- **Dry run ticked** (the default): every row is checked, nothing is written. The results start with "Dry run: nothing was written."
- **Dry run unticked**: rows that pass are written. Click **Run** only after a clean dry run; there's no confirmation step.
- Each row is saved on its own: a failing row is skipped and never stops or undoes the other rows.
- Nothing is published. Changed fragments only go live when someone publishes them in AEM.

**The results table**

| Column | Meaning |
| --- | --- |
| Sheet | The sheet the row came from |
| Row | The Excel row number, as you see it in Excel |
| Name | The fragment's slug |
| Action | CREATE or UPDATE |
| Result | **Pass** (green): written, or would be in a dry run · **Fail** (red): not written, see Details · **Skip** (grey): the sheet had no rows with an action |
| Details | On Pass, the fragment's path (plus any new ID or cleared fields). On Fail, what's wrong, starting with the field name or "Fragment:" / "Sheet:" |

- Failures are listed first, then skipped sheets, then passes. The chips above the table count each kind.
- **Download CSV** saves the full table to share or work through in Excel.
- If the whole workbook can't be used (wrong file, a bad header, an invalid action, over a limit), you get a red message instead of the table, and nothing is checked or written.

## Common errors and how to fix them

Most failures are a typo in a header, a slug or a value; the message says which.

**Whole workbook rejected** (nothing checked or written)

| Message | Fix |
| --- | --- |
| `Select a workbook first.` | Pick a file in the path field |
| `No file found at …` / `… is not a DAM asset.` | Pick the workbook itself, not a folder |
| `… is not an .xlsx file.` / `The file is not a valid .xlsx workbook.` | Save as Excel Workbook (.xlsx) and upload again |
| `… has no original file yet. Wait for DAM processing to finish.` | Wait a minute after uploading, then run again |
| `The workbook is 14.2 MB; the limit is 10 MB.` | Remove unused sheets or split the file |
| `The workbook has 7,200 rows to process; the limit is 5,000.` | Split into smaller files (only rows with an action count) |
| `Sheet "offer-detail", column C: header "Offer Title" must be a single word.` | Use the exact field name, e.g. `offerTitle` |
| `Sheet "offer-cta": has no action column.` | Add an `action` column |
| `Sheet "offer-cta", row 4: action "UPDTE" must be CREATE or UPDATE.` | Fix the spelling, or empty the cell to skip the row |

**One sheet failed** (other sheets still run)

| Message | Fix |
| --- | --- |
| `Sheet: "venues" is not a known CF model` | Rename the sheet to the exact model name |
| `Sheet: model not found at …` | The model is missing in AEM; contact the AEM team |
| `Sheet: no row has an action (CREATE or UPDATE)` | Not an error (grey Skip): fill an action if you meant to change that sheet |

**One row failed** (other rows still run)

| Message | Fix |
| --- | --- |
| `offerSlug: "Book Now" must be lowercase letters, digits, - or _` | Use e.g. `book-now`. An empty slug, or a misspelt slug header, shows the same message: check both |
| `promoCode: not a field of model offer-detail` | Fix the header spelling (case-sensitive) or remove the column |
| `offerTitle: required` | Fill the required field on CREATE |
| `offerTnc: #CLEAR is no longer used — leave the cell empty to clear the field` | Leave the cell empty to clear the field |
| `Fragment: already exists at …` | Use UPDATE, or a new slug for a new fragment |
| `Fragment: not found at …` | Check the slug and the sheet, or use CREATE |
| `Fragment: uses model …, not …` | That path belongs to another model; check the sheet and slug |
| `offerID: "10001a" is not a whole number` | Digits only |
| `ID: "1,001" is not a whole number` | Digits only, no commas or spaces |
| `hotPromo: "maybe" must be true/false, 1/0 or yes/no` | Use one of those values |
| `offerStartDate: "01/10/2026" is not a date …` | Use `2026-10-01` (typed as text) |
| `parent: takes one value, but the cell has several lines` | Keep a single path in that cell |
| `Fragment: could not be written / saved (…)` | Usually permissions; contact the AEM team with the message |

## Limits and good practice

A real run can't be undone from the tool, so the dry run is your safety net.

**Limits**

- File size: 10 MB.
- Rows with an action: 5,000 per upload, across all sheets. Rows with an empty action don't count, so a full report with a few edits is fine.

**Do**

- Start every change from a fresh **Save report to DAM**, so existing rows and IDs are in the sheet.
- Add new rows below the existing ones in the right sheet.
- Run a dry run, get it to zero Fail and check every (cleared: …) note before unticking Dry run.
- Download the CSV of a real run and keep it as a record of what changed.
- Keep a copy of the workbook you uploaded.

**Don't**

- Rename sheets or headers, or add spaces to them.
- Put an action on rows you don't want to change; leave it empty.
- Copy a row to make a new one without changing its slug and clearing its ID.
- Use bare names (`dining`) where a full path is needed.
- Expect the upload to delete or publish anything; both are done by hand in AEM.

The tool has no undo. If a real run wrote something wrong, fix it with another upload (UPDATE with the old values) or edit the fragments in AEM.

## Checklist before a real run

Tick these off every time before you untick Dry run.

- [ ] I started from a fresh report, and the existing rows are still in the sheets.
- [ ] Every row I want to change has `CREATE` or `UPDATE`; every other row has an empty action.
- [ ] New slugs are lowercase with `-` or `_` only, and not already used in that folder.
- [ ] Required (red) columns are filled on CREATE rows; ID cells on new rows are empty.
- [ ] Dates are typed as text; references are full `/content/...` paths; `offerCta` has one path.
- [ ] On UPDATE rows, I only emptied cells I really want cleared.
- [ ] The latest dry run shows zero **Fail**, and every `(cleared: …)` note is intended.
- [ ] I uploaded the final version of the file and picked that file on the page.

## Frequently asked questions

**Does the upload publish anything?** No. It only creates and changes fragments on author. Publish them in AEM when you're ready.

**Can I delete fragments with it?** No. Delete them by hand in AEM.

**I made a mistake in a real run. Can I undo it?** The tool has no undo. Correct the values with another upload (UPDATE), or edit the fragments in AEM. The saved report keeps its previous versions (Assets → the report file → **Timeline**), so a report saved before your change shows the old values.

**Can I upload only one sheet, or only a few columns?** Yes. Sheets with no actions are skipped, and columns you delete are not touched. Keep `action` and the slug column.

**Why was my row ignored?** Its action cell is empty. Only `CREATE` and `UPDATE` rows are processed.

**Why does my date fail?** Excel turned it into its own date format. Format the column as Text, or type `'2026-10-01` with a leading apostrophe.

**Can I rename a fragment?** Not with the upload: the slug is the fragment's name and path. Create a new fragment with the new slug, then delete the old one in AEM.

**Why did my new fragment get ID 100001 when others already exist?** The sheet didn't contain the existing rows, so the tool didn't see the higher IDs. Always start from a fresh report and add rows below the existing ones.

**The page says the workbook can't be used. Which row is wrong?** The red message lists every problem with its sheet, row and column. Fix them all and upload again.

**Where do I find a fragment after the upload?** The result row shows its full path; open it in Assets.

## Field reference

Every column per sheet, as defined in the models today; **bold** = required, ★ = slug column.

**merchant-venue**

| Column | Type | Notes |
| --- | --- | --- |
| name | Text | Title |
| ★ **merchantVenueSlug** | Text |  |
| address | Multi-line (HTML) |  |
| latitude | Text | e.g. `1.2935` |
| longitude | Text | e.g. `103.8520` |
| telephone | Text |  |
| ophr | Text |  |
| fax | Text |  |

**merchant-details**

| Column | Type | Notes |
| --- | --- | --- |
| **merchantID** | Text (digits) | Automatic on CREATE if empty |
| merchantName | Text | Title |
| ★ **merchantSlug** | Text |  |
| merchantLogo | Yes / no |  |
| merchantVenues | Fragment references, several | Paths to `merchant-venue` fragments, one per line |

**category**

| Column | Type | Notes |
| --- | --- | --- |
| **ID** | Whole number | Automatic on CREATE if empty |
| name | Text | Title |
| ★ **offerCategorySlug** | Text |  |
| categoryIconPath | Content reference | Full DAM path |
| parent | Fragment reference, one | Path to a `category` fragment |

**offer-card**

| Column | Type | Notes |
| --- | --- | --- |
| ID | Whole number | Automatic on CREATE if empty |
| name | Text | Title |
| ★ **offerCardSlug** | Text |  |
| logo | Image reference | Full DAM path |
| parent | Fragment reference, one | Path to an `offer-card` fragment |

**offer-cta**

| Column | Type | Notes |
| --- | --- | --- |
| label | Text | Title |
| ★ **offerCtaSlug** | Text |  |
| url | Link | A web address or DAM path, one value |
| deeplink | Link | e.g. `app://book`, one value |

**offer-detail**

| Column | Type | Notes |
| --- | --- | --- |
| **offerID** | Whole number | Automatic on CREATE if empty |
| **offerTitle** | Text | Title |
| ★ **offerSlug** | Text |  |
| offerSummary | Multi-line (HTML) |  |
| offerDescription | Multi-line (HTML) |  |
| offerImage | Image reference | Full DAM path |
| alternateText | Text | Alt text for the image |
| hotPromo | Yes / no |  |
| offerStartDate | Date and time | Typed as text |
| offerEndDate | Date and time | Typed as text |
| offerURL | Link | A web address or DAM path |
| offerLabel | Text |  |
| offerEmail | Text |  |
| offerTnc | Multi-line (HTML) |  |
| offerType | Text |  |
| offerPromoCodes | Text | One value |
| offerBadge | Text |  |
| offerTags | Text | One value |
| offerLocation | Text |  |
| offerLocationType | Text |  |
| offerCategories | Fragment references, several | Paths to `category` fragments |
| offerMerchants | Fragment references, several | Paths to `merchant-details` fragments |
| offerCards | Fragment references, several | Paths to `offer-card` fragments |
| offerCta | Fragment reference, **one** | Path to one `offer-cta` fragment |
| offerOrigin | Text |  |
| brCode | Text |  |
| qrCode | Text |  |
| preview | Yes / no |  |
| cache | Yes / no |  |

If a model changes in AEM (a field added, renamed or made required), the upload follows it automatically; a new **Save report to DAM** shows the current columns.
