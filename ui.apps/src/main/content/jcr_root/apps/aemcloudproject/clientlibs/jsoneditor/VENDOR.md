# Vendored libraries — DAM JSON editor

Downloaded with `npm pack` from registry.npmjs.org on 2026-10-29. Upgrade by replacing the versioned folder, updating `js.txt`/`css.txt`/`editor.js` paths and this file.

| Library | Version | Source | Licence | File | SHA-256 |
|---|---|---|---|---|---|
| vanilla-jsoneditor | 3.13.0 | https://registry.npmjs.org/vanilla-jsoneditor/-/vanilla-jsoneditor-3.13.0.tgz | ISC | resources/vendor/vanilla-jsoneditor-3.13.0/standalone.js | 2345b95c5756bd7a1183bc6e31d9fca539f5adc70a44c9eb3f1f0082661dd160 |
| vanilla-jsoneditor | 3.13.0 | same | ISC | resources/vendor/vanilla-jsoneditor-3.13.0/LICENSE.md | fd50f5abb7eeb614c8d1293b9f239a5238d7536e3a9a9a8929a082c82d12b3fd |
| diff (jsdiff) | 9.0.0 | https://registry.npmjs.org/diff/-/diff-9.0.0.tgz | BSD-3-Clause | vendor/jsdiff-9.0.0/diff.min.js | b51a9d2885f2c090dc97b981027395f7e7e6558a46c75ae3747db267913a89ab |
| diff (jsdiff) | 9.0.0 | same | BSD-3-Clause | vendor/jsdiff-9.0.0/LICENSE | eda81a415869e67809f6a9f3fd6056062cb83a462118e07eec5e92b2e0c40ce9 |
| diff2html | 3.4.56 | https://registry.npmjs.org/diff2html/-/diff2html-3.4.56.tgz | MIT | vendor/diff2html-3.4.56/diff2html-ui-slim.min.js | 7bf8f0b288986bce9f8a8f5d4608eadd02a715ff95135a2fa28d1a10fcad2310 |
| diff2html | 3.4.56 | same | MIT | vendor/diff2html-3.4.56/diff2html.min.css | d3ecc0e9b2b1e5c8466c19de29bed052fd0863475d25829ecc858446efded372 |
| diff2html | 3.4.56 | same | MIT | vendor/diff2html-3.4.56/LICENSE.md | 6de794566a8feb4233594420299cec2dbfba51ae57201763541a5e7d627cb245 |

## Notes
- `standalone.js` is an ES module (exports `createJSONEditor`); it is served from `resources/` and loaded with `import()`, never concatenated. It injects its own `<style>` tag, so no stylesheet is listed in `css.txt`.
- `diff.min.js` and `diff2html-ui-slim.min.js` are UMD bundles that set `window.Diff` and `window.Diff2HtmlUI` when no AMD loader is on the page.
- The clientlib disables AEM's JS/CSS processors because the vendored files are already minified and AEM's minifier can break `import()`.
