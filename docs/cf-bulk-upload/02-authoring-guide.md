# CF Bulk Upload – Authoring Guide

The CF bulk upload lets you create, change and delete many content fragments at once from one Excel workbook, instead of editing them one by one in AEM. This guide takes you from your first workbook to a finished upload; if you are new, read it top to bottom once, then use the later sections as a reference.

## What the tool does

You fill an Excel workbook, upload it to DAM, and the tool creates, updates or deletes one content fragment per row. A dry run checks every row first and tells you exactly what is wrong; only a clean dry run unlocks the final run that writes.

**Key terms**

| Term | Meaning |
| --- | --- |
| Content fragment (CF) | A piece of structured content in AEM, e.g. one offer, one merchant or one CTA |
| Model | The template a fragment follows: which fields it has and their types. Each sheet in the workbook is one model |
| Field | One value inside a fragment, e.g. `offerTitle`. Each column in a sheet is one field |
| Slug | The fragment's short technical name, e.g. `summer-sale`, in the `slug` column. It becomes the last part of the fragment's path and must be unique in its folder. It is only used for the path, not stored in the fragment |
| Reference | A field that points to another fragment or an image by its full path, e.g. an offer pointing to its CTA |
| Action | What a row should do: `CREATE` a new fragment, `UPDATE` or `DELETE` an existing one |
| Fragment folder | The DAM folder you pick on the page; each model has its own sub-folder under it |
| Dry run | A test run: every row is checked, nothing is written |
| Final run | The run that actually creates, updates and deletes fragments. Enabled only after a dry run with no failures |
| Sample sheet | An empty workbook with every sheet and column, to author in |
| Report | An Excel file with every existing fragment, in exactly the layout the upload expects; useful for UPDATE and DELETE |
| DAM | AEM Assets, where the workbooks, the reports and the fragments are stored |

## Before you start

You need AEM author access with rights to the folders below, and Excel.

| What | Where |
| --- | --- |
| The upload page | AEM → **Tools** (hammer icon) → **SCB Tools** → **Content Fragment Bulk Upload** |
| Folder for workbooks you upload | `/content/dam/aemcloudproject/imports` |
| Fragment folder (pre-filled on the page) | `/content/dam/aemcloudproject/cfs/offer-listing` (see Sheets, folders and names) |
| Saved reports and run reports | `/content/dam/aemcloudproject/reports`, one new dated file each time |

- **Permissions:** you need to upload files to the imports folder, to create, modify and delete content in the fragment folders, and to create files in the reports folder. If an upload fails with "could not be written", ask the AEM team to check your rights.
- **Excel:** any recent version. Keep the file as `.xlsx`; don't save it as `.xls` or `.csv`.
- **First time?** The dry run never changes anything, and the final run stays disabled until a dry run passes.

## Quick start

Start from the sample sheet, check with a dry run, then do the final run.

1. In AEM, open **Tools → SCB Tools → Content Fragment Bulk Upload**.
2. Click **Download sample sheet**. You get `cf-bulk-upload-sample.xlsx`: one sheet per model with all the column names and no rows.
   - To change or delete existing fragments, you can instead click **Save report to DAM**: every fragment under the fragment folder is saved as a new dated file in the reports folder. Click **Open in Assets** and **Download** it.
3. Fill the workbook:
   - to add a fragment, add a row in the right sheet, pick `CREATE` in **action** and fill its cells;
   - to change a fragment, pick `UPDATE`, put its slug in **slug** and fill the cells you want to change (an empty cell in a column you kept clears that field);
   - to delete a fragment, pick `DELETE` and put its slug in **slug**;
   - leave the action empty on rows you don't want to touch.
4. Upload the workbook to DAM, into **/content/dam/aemcloudproject/imports**.
5. On the upload page, pick the workbook, check the **Fragment folder** and click **Dry run**. Nothing is written; you see what would happen to each row.
6. Fix any **Fail** rows in Excel, upload the file again and repeat the dry run until it has no failures. **Final run** is enabled only then.
7. Click **Final run**. The fragments are created, updated or deleted, and a run report is saved in the reports folder.
8. Publish the changed fragments in AEM when you're ready. The upload never publishes anything.

## Worked example: a new offer from start to finish

Goal: add a new offer for the restaurant "Spice Garden", with its venue, a booking CTA, and the existing category `dining`. One workbook, one upload.

**Step 1: get the workbook.** Click **Download sample sheet**. You only fill the sheets you need; the others stay empty and are skipped.

**Step 2: add the venue** (sheet `merchant-venue`)

| action | slug | name | address | telephone |
| --- | --- | --- | --- | --- |
| CREATE | spice-garden-orchard | Spice Garden Orchard | `<p>1 Orchard Road, #02-10</p>` | +65 6000 1234 |

**Step 3: add the merchant** (sheet `merchant-details`). Leave `merchantID` empty; it is numbered automatically. Point to the venue by its full path.

| action | slug | merchantID | merchantName | merchantVenues |
| --- | --- | --- | --- | --- |
| CREATE | spice-garden |  | Spice Garden | /content/dam/aemcloudproject/cfs/offer-listing/merchants/venues/spice-garden-orchard |

**Step 4: add the CTA** (sheet `offer-cta`)

| action | slug | label | url |
| --- | --- | --- | --- |
| CREATE | book-table-spice-garden | Book a table | https://example.com/spice-garden/book |

**Step 5: add the offer** (sheet `offer-detail`). Leave `offerID` empty. References use full paths; several categories or merchants go on separate lines in one cell (Alt+Enter), but `offerCta` takes exactly one.

| action | slug | offerID | offerTitle | offerStartDate | offerCategories | offerMerchants | offerCta |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CREATE | spice-garden-weekend-deal |  | Weekend dinner deal | 2026-11-01 | /content/dam/aemcloudproject/cfs/offer-listing/categories/dining | /content/dam/aemcloudproject/cfs/offer-listing/merchants/spice-garden | /content/dam/aemcloudproject/cfs/offer-listing/offer-cta/book-table-spice-garden |

Fill any other columns you need (summary, image, dates and so on); leave the rest empty.

**Step 6: upload and check.** Upload the file to `/content/dam/aemcloudproject/imports`. On the upload page pick it, keep the fragment folder `/content/dam/aemcloudproject/cfs/offer-listing` and click **Dry run**. You should see four green **Pass** rows, e.g. `…/merchants/spice-garden (merchantID 100003)`, and grey **Skip** lines for the empty sheets.

**Step 7: write it.** Click **Final run**. Check the four fragments in Assets, then publish them when you're ready. The run report link above the results opens the saved record of this run.

References are stored as paths, so a row can point to a fragment created by another row of the same upload, as the offer does here. Just make sure the path matches the fragment folder, the model's sub-folder and the slug exactly.

## Workbook layout

One `.xlsx` file, one sheet per model, column names in row 1 and one fragment per row below.

| Part | Rule |
| --- | --- |
| File | `.xlsx` only (not `.xls` or `.csv`), at most 10 MB |
| Sheet name | Exactly the model's name: `merchant-venue`, `merchant-details`, `category`, `offer-card`, `offer-cta`, `offer-detail`. Any other sheet name fails |
| Sheet order | Any order; the sample and report use venues → merchants → categories → cards → CTAs → offers |
| Row 1 | Column headers: `action`, `slug`, then the model's field names, spelled exactly as in the model (case-sensitive, one word, no spaces) |
| Red headers | `slug` and the required fields (see IDs and required fields) |
| Column order | Any order |
| Columns | Only `action` and `slug` are needed; leave out columns you don't use |
| Rows 2 onwards | One fragment per row; completely empty rows are ignored |
| Formulas | Allowed; the calculated value is used |
| Sheets starting with \_ | Ignored by the upload. The first sheet, \_Guide, is a short version of this guide with a table of every sheet's folder, title, ID and required columns. Keep it or delete it; you can also add your own \_Notes sheet |

A header with a space (`Offer Title`) or a sheet without an `action` column rejects the whole workbook. Nothing is written until it's fixed.

## The action column

Each row's `action` cell says what to do with that fragment. In the sample and in saved reports it is a dropdown: pick `CREATE`, `UPDATE` or `DELETE`, or leave it empty.

| action | What happens | The fragment must |
| --- | --- | --- |
| `CREATE` | A new fragment is made | Not exist yet, otherwise the row fails with "already exists" |
| `UPDATE` | An existing fragment is changed | Exist already, with the same model, otherwise the row fails with "not found" |
| `DELETE` | An existing fragment is deleted; only `action` and `slug` are read | Exist already, with the same model, and not be referenced or published (see Deleting fragments) |
| empty | The row is skipped: not checked, not written, not in the results | — |

- In a workbook you build yourself, case doesn't matter: `create`, `Create` and `CREATE` are the same.
- Any other value (`UPDTE`, `REMOVE`) rejects the whole workbook with a message naming the sheet and row.
- If no row in a sheet has an action, that sheet shows one grey **Skip** line in the results.

## Deleting fragments

A DELETE row only removes a fragment that nothing else depends on.

- The fragment is **not deleted** and the row shows an amber **Warn** when:
  - another fragment under the fragment folder points to it, or a page or experience fragment uses it. The message lists where, e.g. `Not deleted: referenced by …/offers/summer-sale (remove the references first)`;
  - it is still published: `Not deleted: still published (unpublish it first)`.
- Remove the references (or unpublish it), then run the same row again.
- DELETE rows run after all CREATE and UPDATE rows, and offers first, venues last. So you can delete an offer and its CTA in one upload, or update an offer to drop a CTA and delete that CTA in the same upload.
- In the dry run, an UPDATE is not written yet, so a delete that it would unblock still shows Warn; the final run deletes it.
- Warn is not a failure: it doesn't block the final run. A deleted fragment can't be restored from the tool; keep a saved report from before the run if you may need its values again.

## Sheets, folders and names

The sheet decides the sub-folder; the slug decides the fragment's name, and with it its path.

All folders below are under the **Fragment folder** you pick on the page, by default **/content/dam/aemcloudproject/cfs/offer-listing/**.

| Sheet | Folder | Title column | ID column |
| --- | --- | --- | --- |
| `merchant-venue` | `merchants/venues` | `name` | — |
| `merchant-details` | `merchants` | `merchantName` | `merchantID` |
| `category` | `categories` | `name` | `ID` |
| `offer-card` | `cards` | `name` | `ID` |
| `offer-cta` | `offer-cta` | `label` | — |
| `offer-detail` | `offers` | `offerTitle` | `offerID` |

Example: sheet `offer-cta`, slug `book-now` → `/content/dam/aemcloudproject/cfs/offer-listing/offer-cta/book-now`.

**Slug rules**

- Only lowercase letters, digits, `-` and `_`: `book-now`, `summer_sale_2026`. Not `Book Now`, `café` or `book.now`.
- The slug is never changed or tidied up for you: what you type is the path you get.
- The slug is only used for the path. It is not stored in the fragment, and the models have no slug field.
- On UPDATE and DELETE, the slug finds the fragment; a different slug points at a different fragment: "not found" if nothing is there, or that other fragment gets changed if it exists. In a saved report the slug column always shows each fragment's real name.
- If you pick a different fragment folder, the same slug points to a fragment in that folder instead.

**Title**

- The title column sets the fragment's title in Assets.
- CREATE with an empty title uses the slug as the title.
- UPDATE with an empty title clears the title field, but the title shown in Assets stays as it is.

## Filling in cells

Type each value the way its field expects; the Field reference at the end says which type each column is.

| Field type | What to type | Example |
| --- | --- | --- |
| Text (single line) | Plain text | `Book Now` |
| Text (multi-line / rich text) | HTML, as a saved report shows it | `<p>Enjoy 20% off.</p>` |
| Number, whole | Digits only | `10001` |
| Number, decimal | Digits with a `.` | `1.2935` |
| Yes / no | `true` / `false`, `1` / `0` or `yes` / `no` (any case) | `true` |
| Date and time | Text in one of three formats (below) | `2026-10-01T00:00:00.000+05:30` |
| Fragment reference, one | The full path of the fragment | `/content/dam/aemcloudproject/cfs/offer-listing/categories/dining` |
| Fragment reference, several | One full path per line inside the cell (Alt+Enter, or Option+Enter on Mac, for a new line) | two paths on two lines |
| Image or file reference | The full DAM path | `/content/dam/aemcloudproject/offers/summer.jpg` |

**Dates**

- `2026-10-01T00:00:00.000+05:30`: date, time and time zone, kept exactly as written (this is what a saved report gives you).
- `2026-10-01T09:30`: date and time, in the AEM server's time zone.
- `2026-10-01`: date only, at midnight in the AEM server's time zone.
- Type dates as text. Format the column as **Text** in Excel first, or start the value with an apostrophe (`'2026-10-01`); otherwise Excel turns it into its own date format, which the upload can't read.

**References**

- Always the full path starting with `/content/`, never just a name like `dining`.
- Several values only in fields that allow several (the Field reference marks them). Two lines in a single-value field fail the row.
- The upload doesn't check yet that a referenced fragment or image exists, so copy paths carefully. A safe way is to copy them from a fresh saved report.

**Empty cells and clearing**

- CREATE: an empty cell leaves that field empty.
- UPDATE: an empty cell **clears** that field. Either keep only the columns you change, or start from a fresh saved report so the cells already hold the current values; delete a value to clear it.
- Exception: an empty ID cell on UPDATE keeps the current ID.
- A column you leave out of the sheet is never touched. To change just one field, a sheet with `action`, `slug` and that field is enough.
- `#CLEAR` is no longer used; typing it fails the row. Leave the cell empty instead.
- The results show which fields a row clears, in the dry run too: `…/book-now (cleared: deeplink)`.

## IDs and required fields

Leave the ID empty on new rows and the upload numbers them for you; required fields must be filled on CREATE.

**Automatic IDs** (`offerID`, `merchantID`, category `ID`, offer-card `ID`)

- A CREATE row with an empty ID gets the next number: the highest ID so far + 1, then +1 for each further new row, top to bottom.
- "Highest ID so far" counts every row in the sheet (including rows with an empty action) **and** every existing fragment of that model in its folder in AEM. So a workbook with only new rows, like the sample, continues the numbering correctly.
- If there are no IDs at all yet, numbering starts at 100001 (merchants, offers) or 1001 (categories, offer cards).
- An ID you type yourself is kept as it is.
- A row that fails doesn't use up a number.
- UPDATE never changes the ID; leave it as it is.
- The new ID is shown in the results, e.g. `…/offers/summer-sale (offerID 100004)`.

**Required fields** (red headers in the sample and reports)

| Sheet | Required |
| --- | --- |
| every sheet | `slug` |
| `merchant-details` | `merchantID` |
| `category` | `ID` |
| `offer-detail` | `offerID`, `offerTitle` |

- CREATE: every required field needs a value, otherwise the row fails with e.g. `offerTitle: required`. An automatic ID counts as filled.
- UPDATE: a required column in the sheet can't be left empty, because an empty cell would clear it; the row fails with `offerTitle: required`. An empty ID cell keeps the current ID.
- Required fields come from the content fragment model. When a field is made required in the model, the upload enforces it from then on.

## Running an upload and reading the results

A dry run and a final run run exactly the same checks; only the final run writes.

**Dry run and final run**

- **Dry run**: every row is checked, nothing is written. The results start with "Dry run: nothing was written." (yellow) or, when rows failed, "Dry run: 3 rows failed. Nothing was written." (red).
- **Final run** is enabled only after a dry run of the same workbook and fragment folder with no **Fail** rows. Picking another workbook or folder disables it again. Rows marked **Warn** don't block it.
- If you upload a new version of the workbook after the dry run, the final run is refused with "The workbook changed after the dry run. Run the dry run again."
- The final run writes straight away; there's no confirmation step. Each row is saved on its own: a failing row is skipped and never stops or undoes the other rows.
- After a final run, a run report (`cf-upload-run-<date>-<time>.xlsx`) is saved in the reports folder with the workbook, folder, your user, the time and every result. The green banner links to it. Final run is then disabled until the next dry run.
- Nothing is published. Changed fragments only go live when someone publishes them in AEM.

**The results table**

| Column | Meaning |
| --- | --- |
| Sheet | The sheet the row came from |
| Row | The Excel row number, as you see it in Excel |
| Slug | The fragment's slug |
| Action | CREATE, UPDATE or DELETE |
| Result | **Pass** (green): written, or would be in a dry run · **Fail** (red): not written, see Details · **Warn** (amber): DELETE left in place, see Details · **Skip** (grey): the sheet had no rows with an action |
| Details | On Pass, the fragment's path (plus any new ID, cleared fields, or "(to be deleted)" / "(deleted)"). On Fail or Warn, what's wrong, starting with the field name, "Fragment:", "Sheet:" or "Not deleted:" |

- Failures are listed first, then warnings, then skipped sheets, then passes. The chips above the table count each kind.
- **Download CSV** saves the full table to share or work through in Excel.
- If the whole workbook can't be used (wrong file, no folder, a bad header, an invalid action, over a limit), you get a red message instead of the table, and nothing is checked or written.

## Saving a report

**Save report to DAM** writes every fragment under the fragment folder to a new file, `offer-listing-report-<date>-<time>.xlsx`, in the reports folder. Each save is a separate file, so older reports are never overwritten.

- One sheet per model, in the upload layout, with each fragment's slug filled in. Offers are sorted by end date, latest first (offers without an end date last); the other sheets by ID.
- Use it to see what exists, as a starting point for UPDATE or DELETE rows, and as a record of the values before a big change.

## Common errors and how to fix them

Most failures are a typo in a header, a slug or a value; the message says which.

**Whole workbook rejected** (nothing checked or written)

| Message | Fix |
| --- | --- |
| `Select a workbook first.` | Pick a file in the Workbook field |
| `Select the fragment folder first.` / `No folder found at …` / `… is not a DAM folder.` | Pick an existing folder under `/content/dam` in the Fragment folder field |
| `No file found at …` / `… is not a DAM asset.` | Pick the workbook itself, not a folder |
| `… is not an .xlsx file.` / `The file is not a valid .xlsx workbook.` | Save as Excel Workbook (.xlsx) and upload again |
| `… has no original file yet. Wait for DAM processing to finish.` | Wait a minute after uploading, then run again |
| `The workbook changed after the dry run. Run the dry run again.` | Run the dry run on the new version, then the final run |
| `The workbook is 14.2 MB; the limit is 10 MB.` | Remove unused sheets or split the file |
| `The workbook has 7,200 rows to process; the limit is 5,000.` | Split into smaller files (only rows with an action count) |
| `Sheet "offer-detail", column C: header "Offer Title" must be a single word.` | Use the exact field name, e.g. `offerTitle` |
| `Sheet "offer-cta": has no action column.` | Add an `action` column |
| `Sheet "offer-cta", row 4: action "UPDTE" must be CREATE, UPDATE or DELETE.` | Pick a value from the dropdown, or empty the cell to skip the row |

**One sheet failed** (other sheets still run)

| Message | Fix |
| --- | --- |
| `Sheet: "venues" is not a known CF model` | Rename the sheet to the exact model name |
| `Sheet: model not found at …` | The model is missing in AEM; contact the AEM team |
| `Sheet: no row has an action (CREATE, UPDATE or DELETE)` | Not an error (grey Skip): fill an action if you meant to change that sheet |

**One row failed or warned** (other rows still run)

| Message | Fix |
| --- | --- |
| `slug: "Book Now" must be lowercase letters, digits, - or _` | Use e.g. `book-now`. An empty slug, or a missing `slug` column, shows the same message: check both |
| `promoCode: not a field of model offer-detail` | Fix the header spelling (case-sensitive) or remove the column |
| `offerTitle: required` | Fill the required field on CREATE |
| `offerTnc: #CLEAR is no longer used — leave the cell empty to clear the field` | Leave the cell empty to clear the field |
| `Fragment: already exists at …` | Use UPDATE, or a new slug for a new fragment |
| `Fragment: not found at …` | Check the slug, the sheet and the fragment folder, or use CREATE |
| `Fragment: uses model …, not …` | That path belongs to another model; check the sheet and slug |
| `Not deleted: referenced by … (remove the references first)` | Remove the listed references, then run the DELETE again |
| `Not deleted: still published (unpublish it first)` | Unpublish the fragment in AEM, then run the DELETE again |
| `offerID: "10001a" is not a whole number` | Digits only |
| `ID: "1,001" is not a whole number` | Digits only, no commas or spaces |
| `hotPromo: "maybe" must be true/false, 1/0 or yes/no` | Use one of those values |
| `offerStartDate: "01/10/2026" is not a date …` | Use `2026-10-01` (typed as text) |
| `parent: takes one value, but the cell has several lines` | Keep a single path in that cell |
| `Fragment: could not be written / saved / deleted (…)` | Usually permissions; contact the AEM team with the message |

## Limits and good practice

A final run can't be undone from the tool, so the dry run is your safety net.

**Limits**

- File size: 10 MB.
- Rows with an action: 5,000 per upload, across all sheets. Rows with an empty action don't count, so a full report with a few edits is fine.

**Do**

- Start new content from the sample sheet; start changes and deletes from a fresh saved report, or type the slugs carefully.
- Save a report before a big change, so the old values are on record.
- Get the dry run to zero Fail and check every (cleared: …), (to be deleted) and Warn note before the final run.
- Keep a copy of the workbook you uploaded. The run report records what happened.

**Don't**

- Rename sheets or headers, or add spaces to them.
- Put an action on rows you don't want to change; leave it empty.
- Copy a row to make a new one without changing its slug and clearing its ID.
- Use bare names (`dining`) where a full path is needed.
- Expect the upload to publish or unpublish anything; that is done by hand in AEM.

The tool has no undo. If a final run wrote something wrong, fix it with another upload (UPDATE with the old values from a saved report) or edit the fragments in AEM.

## Checklist before a final run

Tick these off every time before you click Final run.

- [ ] Every row I want to change has `CREATE`, `UPDATE` or `DELETE`; every other row has an empty action.
- [ ] New slugs are lowercase with `-` or `_` only, and not already used in that folder; UPDATE and DELETE slugs match existing fragments.
- [ ] The fragment folder on the page is the one I mean.
- [ ] Required (red) columns are filled on CREATE rows; ID cells on new rows are empty.
- [ ] Dates are typed as text; references are full `/content/...` paths; `offerCta` has one path.
- [ ] On UPDATE rows, I only emptied cells I really want cleared.
- [ ] The latest dry run shows zero **Fail**, and every `(cleared: …)`, `(to be deleted)` and **Warn** is intended.
- [ ] I uploaded the final version of the file and picked that file on the page.

## Frequently asked questions

**Does the upload publish anything?** No. It only creates, changes and deletes fragments on author. Publish them in AEM when you're ready.

**Can I delete fragments with it?** Yes, with `DELETE`, as long as nothing references the fragment and it isn't published. Otherwise the row shows Warn and the fragment stays.

**Why is Final run greyed out?** It needs a dry run of the same workbook and fragment folder with no Fail rows. Run the dry run (again) after fixing the failures or changing the file or folder.

**I made a mistake in a final run. Can I undo it?** The tool has no undo. Correct the values with another upload (UPDATE), or edit the fragments in AEM. A report you saved before the change shows the old values; the run report shows exactly which rows were written.

**Can I upload only one sheet, or only a few columns?** Yes. Sheets with no actions are skipped, and columns you delete are not touched. Keep `action` and `slug`.

**Why was my row ignored?** Its action cell is empty. Only `CREATE`, `UPDATE` and `DELETE` rows are processed.

**Why does my date fail?** Excel turned it into its own date format. Format the column as Text, or type `'2026-10-01` with a leading apostrophe.

**Can I rename a fragment?** Not with the upload: the slug is the fragment's name and path. Create a new fragment with the new slug, then delete the old one.

**Where do I find a fragment after the upload?** The result row shows its full path; open it in Assets.

## Field reference

Every column per sheet, as defined in the models today; **bold** = required. Every sheet also starts with `action` and **`slug`**.

**merchant-venue**

| Column | Type | Notes |
| --- | --- | --- |
| name | Text | Title |
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
| merchantLogo | Yes / no |  |
| merchantVenues | Fragment references, several | Paths to `merchant-venue` fragments, one per line |

**category**

| Column | Type | Notes |
| --- | --- | --- |
| **ID** | Whole number | Automatic on CREATE if empty |
| name | Text | Title |
| categoryIconPath | Content reference | Full DAM path |
| parent | Fragment reference, one | Path to a `category` fragment |

**offer-card**

| Column | Type | Notes |
| --- | --- | --- |
| ID | Whole number | Automatic on CREATE if empty |
| name | Text | Title |
| logo | Image reference | Full DAM path |
| parent | Fragment reference, one | Path to an `offer-card` fragment |

**offer-cta**

| Column | Type | Notes |
| --- | --- | --- |
| label | Text | Title |
| url | Link | A web address or DAM path, one value |
| deeplink | Link | e.g. `app://book`, one value |

**offer-detail**

| Column | Type | Notes |
| --- | --- | --- |
| **offerID** | Whole number | Automatic on CREATE if empty |
| **offerTitle** | Text | Title |
| offerSummary | Multi-line (HTML) |  |
| offerDescription | Multi-line (HTML) |  |
| offerImage | Image reference | Full DAM path |
| alternateText | Text | Alt text for the image |
| hotPromo | Yes / no |  |
| offerStartDate | Date and time | Typed as text |
| offerEndDate | Date and time | Typed as text; saved reports sort offers by it, latest first |
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

If a model changes in AEM (a field added, renamed or made required), the upload follows it automatically; a new **Download sample sheet** shows the current columns.
