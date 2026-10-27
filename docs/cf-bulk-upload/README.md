# CF Bulk Upload – documentation

The SCB CF bulk upload creates, updates and deletes content fragments from one Excel workbook, with a
dry run, a final run that is enabled only after a clean dry run, a results table, a dated run report
and a dated fragment report saved to DAM. Entry point in AEM: **Tools → SCB Tools → Content Fragment
Bulk Upload**.

| Document | For | What it covers |
| --- | --- | --- |
| [01-input-format.md](01-input-format.md) | Developers | The spec: workbook format, actions (incl. DELETE), slug, profiles, IDs, clearing, required fields, field reference |
| [02-authoring-guide.md](02-authoring-guide.md) | Authors | First-time guide: terms, quick start, worked example, cell formats, errors, checklist, FAQ |
| [03-test-scenarios.md](03-test-scenarios.md) | Developers, testers | Fifteen scenarios with their workbook rows, expected results and when each was last verified |
| [04-demo-brief.md](04-demo-brief.md) | Team, lead | The 15 Oct demo, the feedback from it and what changed, architecture, risks, open decisions, next steps |
| [05-decision-empty-cell-clears-field.md](05-decision-empty-cell-clears-field.md) | Lead | Decided and built: an empty cell on UPDATE clears the field (`#CLEAR` retired) |
| [06-proposal-delete-challenges.md](06-proposal-delete-challenges.md) | Lead | Decided and built: DELETE with its safeguards; the original challenges |

PDF versions of the six documents are in [pdf/](pdf/) (A4, generated from these Markdown files; regenerate them after editing).

The sample sheet and every saved report also carry a short version of the authoring guide in their
first sheet, `_Guide`.

Source workbook kept in the repo: `test-data/cf-bulk-upload/cf-import-combined.xlsx`. The demo and
test workbooks described in `03-test-scenarios.md` are rebuilt from the rows in that document,
starting from **Download sample sheet**, and uploaded to `/content/dam/aemcloudproject/imports`.
