# CF Bulk Upload – Empty Cell Clears Field

Proposal from the lead: on UPDATE, an empty cell empties the field, replacing the `#CLEAR` token. It suits the report round-trip, but it changes how IDs, required fields and missing columns behave.

**Update, 14 Oct 2026:** decided and built with all six suggestions below (`#CLEAR` now fails the row; the Assets title is kept).

## The change

Only the empty-cell row changes; CREATE is unaffected, because an empty cell there already means an empty field.

| Cell on an UPDATE row | Today | Proposed |
| --- | --- | --- |
| Has a value | Field set to the value | Same |
| Empty (column is in the sheet) | Field kept as it is | **Field emptied** |
| `#CLEAR` | Field emptied | Dropped (decision 4) |
| Column not in the sheet | Field kept | Field kept (decision 1) |

It fits the report workflow: the report already holds every current value, so deleting a value in Excel means "remove it".

## Impact

The main risk is clearing data by accident; the rest are edge cases the new rule has to define.

| # | Area | What happens under the new rule | Suggested handling |
| --- | --- | --- | --- |
| 1 | Columns left out of the sheet | If missing columns also cleared fields, a small sheet (e.g. only `label`) would wipe every other field | Not in the sheet = not touched |
| 2 | ID field | An empty `offerID` on UPDATE would clear the ID; `offer-card.id` isn't required, so it would really be removed | Empty ID on UPDATE = keep the ID |
| 3 | Required fields | An empty required field on UPDATE now means "clear it" | Row fails: `offerTitle: required` |
| 4 | Old `#CLEAR` cells | If simply dropped, `#CLEAR` would be written into the field as text | Reject with "#CLEAR is no longer used — leave the cell empty" (or treat as empty) |
| 5 | Fragment title | An empty title field (e.g. `offerTitle`) clears the field; the title shown in Assets could follow or stay | Keep the fragment's current title |
| 6 | Accidental clearing | Deleting a value by mistake is now easy and silent | List cleared fields in the results, dry run included: `…/book-now (cleared: deeplink)` |
| 7 | Columns not in the model | Empty cells now count, so a wrong column fails its rows even when empty | Accept: stricter and safer |

Code impact is small: only the writer changes (it walks the sheet's headers instead of the filled cells), plus the spec and the page's help text.

## Decisions needed

Our suggestion is in brackets; numbers refer to the Impact table.

- [ ] Column not in the sheet = field kept? (yes) — 1
- [ ] Empty ID on UPDATE = ID kept? (yes) — 2
- [ ] Empty required field on UPDATE = row fails? (yes) — 3
- [ ] Old `#CLEAR`: reject with a message, or treat as empty? (reject) — 4
- [ ] Fragment title: keep, or follow the field? (keep) — 5
- [ ] Show cleared fields in the results? (yes) — 6
