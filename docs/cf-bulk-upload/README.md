# CF Bulk Upload – documentation

The SCB CF bulk upload creates and updates content fragments from one Excel workbook, with a dry
run, a results table and a versioned report saved to DAM. Entry point in AEM: **Tools → SCB Tools →
Content Fragment Bulk Upload**.

| Document | For | What it covers |
| --- | --- | --- |
| [01-input-format.md](01-input-format.md) | Developers | The spec: workbook format, actions, profiles, IDs, clearing, required fields, field reference |
| [02-authoring-guide.md](02-authoring-guide.md) | Authors | First-time guide: terms, quick start, worked example, cell formats, errors, checklist, FAQ |
| [03-test-scenarios.md](03-test-scenarios.md) | Developers, testers | Eleven scenarios with their workbook rows and verified results |
| [04-demo-brief.md](04-demo-brief.md) | Team, lead | Demo script, what's built, architecture, decisions, risks, open decisions, next steps |
| [05-decision-empty-cell-clears-field.md](05-decision-empty-cell-clears-field.md) | Lead | Decided and built: an empty cell on UPDATE clears the field (`#CLEAR` retired) |
| [06-proposal-delete-challenges.md](06-proposal-delete-challenges.md) | Lead | Open: challenges and decisions before adding a DELETE action |

PDF versions of the six documents are in [pdf/](pdf/) (A4, generated from these Markdown files; regenerate them after editing).

The workbook every report starts from also carries a short version of the authoring guide in its
first sheet, `_Guide`.

Source workbook kept in the repo: `test-data/cf-bulk-upload/cf-import-combined.xlsx`. The demo and
test workbooks described in `03-test-scenarios.md` live in DAM
(`/content/dam/aemcloudproject/imports`) and can be rebuilt from the rows in that document.
