/*
 * SCB CF bulk upload page: posts the selected workbook path to ScbCfBulkUploadServlet and shows
 * the reply. Uses jQuery ajax so granite.csrf.standalone adds the CSRF token.
 *
 * Flow:
 *   page loads
 *     -> scbPrefillFolder(): GET .../upload.json -> {"rootPath": "/content/dam/.../offer-listing"} fills the
 *        Fragment folder picker when it is empty
 *   author clicks "Dry run" (or presses Enter)
 *     -> browser fires "submit" on <form class="scb-cf-bulk-upload">
 *     -> scbSubmitCfBulkUpload(): checks a file and a folder are picked
 *     -> scbSendUpload(): spinner, then POST filePath=...&rootPath=...&dryRun=true to .../upload.json
 *     -> HTTP 200: scbRenderResults() = banner + "12 fail · 3 warn · 138 pass" + Download CSV
 *                  + results table in a fixed-height scroll box, failures first.
 *                  No failures -> the dry run is remembered (file, folder, fileVersion) and Final run is enabled.
 *     -> HTTP 400 / other: scbShowAlert() = red Coral alert with the problem(s); Final run stays disabled
 *   author changes the workbook or the folder
 *     -> Final run is disabled again until a new dry run passes
 *   author clicks "Final run"
 *     -> scbSendUpload(): POST ...&dryRun=false&fileVersion=... (the server refuses it if the file changed)
 *     -> HTTP 200: banner with a link to the saved run report, counts, table; Final run disabled again
 *   author clicks "Download sample sheet"
 *     -> plain link to .../report.xlsx; the browser downloads cf-bulk-upload-sample.xlsx
 *   author clicks "Save report to DAM"
 *     -> scbSaveReport(): button disabled ("Saving report…"), "[spinner] Saving the report… 12 s"
 *        under it; POSTs the picked folder to .../report.json, which builds the report and saves it in DAM;
 *        then shows "✔ Saved …/offer-listing-report-2026-10-20-14-05-33.xlsx  Open in Assets"
 *        — or a red error line
 *
 * The whole file is wrapped in a function that runs immediately, so none of these functions
 * or variables leak into the global "window" scope.
 */
(function (window, document, $) {
    "use strict";

    // CSS selectors for the elements rendered by the Granite page (.content.xml, granite:class).
    var FORM_SELECTOR = ".scb-cf-bulk-upload";                          // the <form>
    var FILE_PATH_FIELD = "foundation-autocomplete[name='filePath']";   // the workbook path browser
    var ROOT_PATH_FIELD = "foundation-autocomplete[name='rootPath']";   // the fragment folder picker
    var SUBMIT_SELECTOR = ".scb-cf-bulk-upload__submit";                // the Dry run button
    var FINAL_RUN_SELECTOR = ".scb-cf-bulk-upload__final-run";          // the Final run button
    var RESULT_SELECTOR = ".scb-cf-bulk-upload__result";                // the area under the two cards

    var REPORT_BUTTON = ".scb-cf-bulk-upload__report";                 // the Save report to DAM button
    var REPORT_STATUS = ".scb-cf-bulk-upload__report-status";           // the line under it

    // Save report to DAM button labels: idle / while the report is being saved.
    var LABEL_REPORT = "Save report to DAM";
    var LABEL_REPORT_BUSY = "Saving report…";                 // … = "…"

    // The last dry run without failures: { filePath, rootPath, fileVersion }, or null.
    // Final run is enabled only while the picked file and folder still match it.
    var lastDryRun = null;

    // Table / CSV columns: header text -> key in each result object from the servlet.
    var COLUMNS = [
        { title: "Sheet", key: "sheet" },
        { title: "Row", key: "row" },
        { title: "Slug", key: "slug" },
        { title: "Action", key: "action" },
        { title: "Result", key: "result" },
        { title: "Details", key: "details" }
    ];

    /**
     * The area under the cards where status, errors and results are shown.
     * Output: the <div class="scb-cf-bulk-upload__result"> element.
     */
    function scbResultBox() {
        return document.querySelector(RESULT_SELECTOR);
    }

    /**
     * Reads a path field of the form.
     * Input:  form, selector (FILE_PATH_FIELD or ROOT_PATH_FIELD)
     * Output: the trimmed path without a trailing "/", e.g. "/content/dam/aemcloudproject/cfs/offer-listing",
     *         or "" when nothing is picked.
     */
    function scbFieldValue(form, selector) {
        var field = form.querySelector(selector);
        return field && field.value ? field.value.trim().replace(/\/$/, "") : "";
    }

    /**
     * Enables Final run only when the last clean dry run used the workbook and folder picked now.
     * Input:  form
     * Output: nothing returned; the Final run button is enabled or disabled.
     */
    function scbUpdateFinalRun(form) {
        form.querySelector(FINAL_RUN_SELECTOR).disabled = !lastDryRun
            || lastDryRun.filePath !== scbFieldValue(form, FILE_PATH_FIELD)
            || lastDryRun.rootPath !== scbFieldValue(form, ROOT_PATH_FIELD);
    }

    /**
     * Enables or disables the buttons while a request is running.
     * Input:  form, busy (true = request running)
     * Output: nothing returned; both buttons are disabled (busy), or Dry run is enabled again and
     *         Final run follows scbUpdateFinalRun.
     */
    function scbSetBusy(form, busy) {
        form.querySelector(SUBMIT_SELECTOR).disabled = busy;
        if (busy) {
            form.querySelector(FINAL_RUN_SELECTOR).disabled = true;
        } else {
            scbUpdateFinalRun(form);
        }
    }

    /**
     * Shows a spinner with a short text in the result area (replaces whatever was there).
     * Input:  text, e.g. "Checking /content/dam/.../cf-import-combined.xlsx"
     * Output: nothing returned; the result area shows [spinner] Checking ...
     */
    function scbShowWait(text) {
        var box = scbResultBox();
        box.textContent = "";
        var wait = document.createElement("div");
        wait.className = "scb-cf-bulk-upload__wait";
        var spinner = new Coral.Wait();
        spinner.size = "S";
        wait.appendChild(spinner);
        var label = document.createElement("span");
        label.textContent = text;
        wait.appendChild(label);
        box.appendChild(wait);
    }

    /**
     * Builds a Coral alert box (AEM's coloured message box with an icon).
     * Input:  variant "error" | "warning" | "success", header text, message text (line breaks kept)
     * Output: the <coral-alert> element, e.g. red box "Couldn't read the workbook" / "Sheet "offer-cta": has no action column."
     */
    function scbAlert(variant, header, message) {
        var alert = new Coral.Alert();
        alert.variant = variant;
        alert.header.textContent = header;
        // One line per "\n" in the message, joined with <br>. Text nodes (not innerHTML), so text from
        // the server is never run as HTML. (CSS white-space: pre-wrap is not used: Coral adds its own
        // whitespace inside the alert, which would show up as blank lines.)
        alert.content.textContent = "";
        (message || "").split("\n").forEach(function (line, index) {
            if (index > 0) {
                alert.content.appendChild(document.createElement("br"));
            }
            alert.content.appendChild(document.createTextNode(line));
        });
        alert.classList.add("scb-cf-bulk-upload__alert");
        return alert;
    }

    /**
     * Shows one alert in the result area (replaces whatever was there).
     * Input:  variant, header, message — see scbAlert
     *         e.g. ("error", "Select a workbook first", "")
     *              ("error", "Fix these problems in the workbook", "Sheet \"offer-cta\": has no action column.\n...")
     * Output: nothing returned; the result area holds the alert.
     */
    function scbShowAlert(variant, header, message) {
        var box = scbResultBox();
        box.textContent = "";
        box.appendChild(scbAlert(variant, header, message));
    }

    /**
     * Shows the outcome of a run: banner, counts + Download CSV, and the results table.
     *
     * Input:  data - the servlet's HTTP 200 reply, e.g.
     *           { dryRun: true, fileVersion: "1792027440793", results: [
     *             {sheet: "category", row: 14, slug: "dining", action: "UPDATE", result: "FAIL",
     *              details: "promoCode: not a field of model category"},
     *             {sheet: "merchant-venue", row: 2, slug: "central-mall-branch", action: "CREATE", result: "PASS",
     *              details: "/content/dam/aemcloudproject/cfs/offer-listing/merchants/venues/central-mall-branch"}, ...] }
     *         a final run's reply has reportPath (or reportError) instead of fileVersion
     * Output: nothing returned; the result area now holds
     *           banner: dry run with failures   -> red    "Dry run: 12 rows failed. Nothing was written."
     *                   dry run without failures -> yellow "Dry run: nothing was written." (click Final run)
     *                   final run               -> green  "Final run done." + "Open the run report" link
     *                                              (yellow with the reason when the report could not be saved)
     *           <div class="scb-cf-bulk-upload__summary">[12 fail] [3 warn] [138 pass] 153 rows   [Download CSV]</div>
     *           <div class="scb-cf-bulk-upload__table-box"><table>...</table></div>       (scrolls inside, header sticky)
     */
    function scbRenderResults(data) {
        var results = data.results || [];
        var failed = results.filter(function (r) { return r.result === "FAIL"; }).length;
        var box = scbResultBox();
        box.textContent = "";
        if (data.dryRun && failed > 0) {
            box.appendChild(scbAlert("error", "Dry run: " + failed + (failed === 1 ? " row" : " rows")
                + " failed. Nothing was written.",
                "Fix the failed rows, upload the workbook again and run the dry run again. "
                + "Final run is enabled once the dry run has no failures."));
        } else if (data.dryRun) {
            box.appendChild(scbAlert("warning", "Dry run: nothing was written.",
                "Click Final run to make these changes. Rows marked Warn are left as they are."));
        } else if (data.reportError) {
            box.appendChild(scbAlert("warning", "Final run done.", data.reportError));
        } else {
            var done = scbAlert("success", "Final run done.", "Run report: " + data.reportPath + " ");
            var link = document.createElement("a");
            link.href = "/assetdetails.html" + data.reportPath;
            link.target = "_blank";
            link.textContent = "Open in Assets";
            done.content.appendChild(link);
            box.appendChild(done);
        }
        box.appendChild(scbRenderSummary(results));

        // The table lives in a fixed-height box that scrolls by itself, so 150 or 5,000 rows
        // never stretch the page; its header row is sticky (cf-bulk-upload.css).
        var tableBox = document.createElement("div");
        tableBox.className = "scb-cf-bulk-upload__table-box";
        tableBox.appendChild(scbRenderTable(scbFailuresFirst(results)));
        box.appendChild(tableBox);
    }

    /**
     * Puts FAIL rows first, then WARN (deletes left in place), SKIP (sheets with no action), then PASS,
     * keeping the run order inside each group.
     * Input:  [venue row 2 PASS, category SKIP, offer row 87 FAIL, cta row 4 WARN]
     * Output: [offer row 87 FAIL, cta row 4 WARN, category SKIP, venue row 2 PASS]   (a new array)
     */
    function scbFailuresFirst(results) {
        function only(result) {
            return results.filter(function (r) { return r.result === result; });
        }
        return only("FAIL").concat(only("WARN"), only("SKIP"), only("PASS"));
    }

    /**
     * Builds the line above the table: fail / warn / pass / skip chips, total, Download CSV button.
     * Input:  results
     * Output: a <div>, e.g. [12 fail] [3 warn] [138 pass] [5 skip] 158 rows ............ [⬇ Download CSV]
     *         The warn and skip chips only appear when there is such a row.
     *         Clicking the button calls scbDownloadCsv(results) (all rows, original order).
     */
    function scbRenderSummary(results) {
        function count(result) {
            return results.filter(function (r) { return r.result === result; }).length;
        }
        var skipCount = count("SKIP");
        var warnCount = count("WARN");

        var summary = document.createElement("div");
        summary.className = "scb-cf-bulk-upload__summary";
        summary.appendChild(scbChip("scb-cf-bulk-upload__chip--fail", count("FAIL") + " fail"));
        if (warnCount > 0) {
            summary.appendChild(scbChip("scb-cf-bulk-upload__chip--warn", warnCount + " warn"));
        }
        summary.appendChild(scbChip("scb-cf-bulk-upload__chip--pass", count("PASS") + " pass"));
        if (skipCount > 0) {
            summary.appendChild(scbChip("scb-cf-bulk-upload__chip--skip", skipCount + " skip"));
        }

        var total = document.createElement("span");
        total.className = "scb-cf-bulk-upload__total";
        total.textContent = results.length + " rows";
        summary.appendChild(total);

        // A Coral button looks like the rest of AEM; type "button" so it never submits the form.
        var download = new Coral.Button();
        download.type = "button";
        download.variant = "secondary";
        download.icon = "download";
        download.label.textContent = "Download CSV";
        download.classList.add("scb-cf-bulk-upload__download");
        download.addEventListener("click", function () {
            scbDownloadCsv(results);
        });
        summary.appendChild(download);
        return summary;
    }

    /**
     * One coloured count chip.
     * Input:  CSS modifier class, text — e.g. ("scb-cf-bulk-upload__chip--fail", "12 fail")
     * Output: <span class="scb-cf-bulk-upload__chip scb-cf-bulk-upload__chip--fail">12 fail</span>
     */
    function scbChip(modifier, text) {
        var chip = document.createElement("span");
        chip.className = "scb-cf-bulk-upload__chip " + modifier;
        chip.textContent = text;
        return chip;
    }

    /**
     * Builds the results table. The Result column is a coloured badge; everything else is plain text.
     *
     * Input:  results (already ordered failures first)
     * Output: a <table>:
     *   | Sheet          | Row | Slug                | Action | Result | Details                                    |
     *   | category       | 14  | dining              | UPDATE | [Fail] | promoCode: not a field of model category   |
     *   | merchant-venue | 2   | central-mall-branch | CREATE | [Pass] | /content/dam/.../venues/central-mall-branch|
     *   | venues         | –   | –                   | –      | [Fail] | Sheet: "venues" is not a known CF model    |
     *   | offer-cta      | 4   | book-now            | DELETE | [Warn] | Not deleted: referenced by /content/dam/.../offers/x (remove the references first) |
     *   | category       | –   | –                   | –      | [Skip] | Sheet: no row has an action (CREATE, UPDATE or DELETE) |
     */
    function scbRenderTable(results) {
        var table = document.createElement("table");
        table.className = "scb-cf-bulk-upload__table";

        // Header row: Sheet | Row | Slug | Action | Result | Details (sticky inside the scroll box)
        var headRow = table.createTHead().insertRow();
        COLUMNS.forEach(function (column) {
            var th = document.createElement("th");
            th.textContent = column.title;
            headRow.appendChild(th);
        });

        var body = table.createTBody();
        results.forEach(function (result) {
            var row = body.insertRow();
            COLUMNS.forEach(function (column) {
                var cell = row.insertCell();
                var value = result[column.key];
                if (column.key === "result") {
                    // PASS -> green "Pass", WARN -> amber "Warn", SKIP -> grey "Skip", FAIL -> red "Fail"
                    var kind = value === "PASS" ? "pass" : value === "WARN" ? "warn" : value === "SKIP" ? "skip" : "fail";
                    var badge = document.createElement("span");
                    badge.className = "scb-cf-bulk-upload__badge scb-cf-bulk-upload__badge--" + kind;
                    badge.textContent = kind.charAt(0).toUpperCase() + kind.slice(1);
                    cell.appendChild(badge);
                } else {
                    // null (e.g. no row number for a whole-sheet problem) is shown as "–".
                    cell.textContent = value === null || value === undefined ? "–" : String(value);
                }
            });
        });
        return table;
    }

    /**
     * Downloads all results as a CSV file, built in the browser (no request to AEM),
     * in the original sheet / row order (not failures first).
     *
     * Input:  results
     * Output: nothing returned; the browser saves upload-results-2026-10-08.csv:
     *   Sheet,Row,Slug,Action,Result,Details
     *   "offer-cta","2","book-now","CREATE","FAIL","Fragment: already exists at /content/dam/.../offer-cta/book-now"
     *   "venues","","","","FAIL","Sheet: ""venues"" is not a known CF model"
     * The file starts with a byte-order mark so Excel opens it as UTF-8.
     */
    function scbDownloadCsv(results) {
        var lines = [COLUMNS.map(function (column) { return column.title; }).join(",")];
        results.forEach(function (result) {
            lines.push(COLUMNS.map(function (column) {
                return scbCsvValue(result[column.key]);
            }).join(","));
        });
        // "\r\n" line endings are what Excel expects in CSV; "﻿" is the UTF-8 byte-order mark.
        var blob = new Blob(["﻿" + lines.join("\r\n")], { type: "text/csv;charset=utf-8" });
        var url = URL.createObjectURL(blob);

        // A temporary link with the download attribute makes the browser save the file.
        var link = document.createElement("a");
        link.href = url;
        link.download = "upload-results-" + new Date().toISOString().substring(0, 10) + ".csv";   // e.g. 2026-10-08
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);       // free the memory held by the blob
    }

    /**
     * Turns one value into a CSV field.
     *
     * Input -> Output:
     *   "book-now"                                  -> "book-now"
     *   2                                           -> "2"
     *   null                                        -> ""
     *   Sheet: "venues" is not a known CF model     -> "Sheet: ""venues"" is not a known CF model"   (quotes doubled)
     */
    function scbCsvValue(value) {
        var text = value === null || value === undefined ? "" : String(value);
        // Every field is wrapped in quotes, so commas and line breaks inside stay in one cell;
        // a quote inside the text is written twice, as CSV requires.
        return "\"" + text.replace(/"/g, "\"\"") + "\"";
    }

    /**
     * Sends the upload request and shows the outcome.
     *
     * Input:  form, filePath e.g. "/content/dam/aemcloudproject/imports/cf-import-combined.xlsx",
     *         rootPath e.g. "/content/dam/aemcloudproject/cfs/offer-listing", dryRun true / false
     * Output: nothing returned. Shows a spinner, then either
     *   - HTTP 200: scbRenderResults(data), from
     *       { "status": "ok" | "error", "dryRun": true, "path": "...", "rootPath": "...", "fileVersion": "...",
     *         "results": [ {...}, ... ] }
     *     A dry run without FAIL rows is remembered in lastDryRun (enables Final run); any other reply clears it.
     *   - HTTP 400 with "errors": red alert "Fix these problems in the workbook" + one problem per line (nothing written)
     *   - HTTP 400 with "message": red alert with that message, e.g. "No file found at /content/dam/.../abc.xlsx."
     *     or "The workbook changed after the dry run. Run the dry run again."
     *   - anything else (500, network down): red alert "Request failed (HTTP 500)."
     */
    function scbSendUpload(form, filePath, rootPath, dryRun) {
        var request = { filePath: filePath, rootPath: rootPath, dryRun: dryRun ? "true" : "false" };
        if (!dryRun) {
            request.fileVersion = lastDryRun.fileVersion;
        }
        lastDryRun = null;
        scbSetBusy(form, true);
        scbShowWait((dryRun ? "Checking " : "Running ") + filePath);

        $.ajax({
            // The form's action attribute from .content.xml:
            // /apps/aemcloudproject/cfbulkupload/content/cfbulkupload/upload.json
            url: form.getAttribute("action"),
            type: "POST",
            // Sent as form data: filePath=%2Fcontent%2F...xlsx&rootPath=%2Fcontent%2F...&dryRun=true
            data: request,
            // Parse the reply as JSON, so "data" below is an object, not a string.
            dataType: "json"
        }).done(function (data) {
            var failed = (data.results || []).some(function (r) { return r.result === "FAIL"; });
            if (data.dryRun && !failed) {
                lastDryRun = { filePath: filePath, rootPath: rootPath, fileVersion: data.fileVersion };
            }
            scbRenderResults(data);
        }).fail(function (xhr) {
            // HTTP 400 from the servlet carries {"status":"error","errors":[...]} or
            // {"status":"error","message":"..."} in responseJSON.
            var data = xhr.responseJSON;
            if (data && data.errors) {
                scbShowAlert("error", "Fix these problems in the workbook", data.errors.join("\n"));
            } else {
                scbShowAlert("error", data && data.message ? data.message : "Request failed (HTTP " + xhr.status + ").", "");
            }
        }).always(function () {
            // Runs after done or fail: re-enable Dry run; Final run only after a clean dry run.
            scbSetBusy(form, false);
        });
    }

    /**
     * Starts a dry or final run after checking that a workbook and a folder are picked.
     *
     * Input:  form, dryRun true (Dry run button / Enter) or false (Final run button)
     * Output: nothing returned.
     *   - no file picked   -> red alert "Select a workbook first"
     *   - no folder picked -> red alert "Select the fragment folder first"
     *   - both picked      -> scbSendUpload(...) straight away (no confirm dialog)
     */
    function scbRun(form, dryRun) {
        var filePath = scbFieldValue(form, FILE_PATH_FIELD);
        var rootPath = scbFieldValue(form, ROOT_PATH_FIELD);
        // Same messages as the servlet gives, without a round trip to AEM.
        if (!filePath) {
            scbShowAlert("error", "Select a workbook first", "");
            return;
        }
        if (!rootPath) {
            scbShowAlert("error", "Select the fragment folder first", "");
            return;
        }
        scbSendUpload(form, filePath, rootPath, dryRun);
    }

    /**
     * Runs when the form is submitted (Dry run clicked or Enter pressed): starts a dry run.
     * Input:  event - the browser's submit event; event.currentTarget is the <form>
     */
    function scbSubmitCfBulkUpload(event) {
        // Stop the browser's normal form submit, which would navigate away from the page.
        event.preventDefault();
        scbRun(event.currentTarget, true);
    }

    /**
     * Fills the Fragment folder picker with the configured default when it is empty.
     * GET .../upload.json -> {"rootPath": "/content/dam/aemcloudproject/cfs/offer-listing"}
     * Input:  form
     */
    function scbPrefillFolder(form) {
        $.getJSON(form.getAttribute("action")).done(function (data) {
            var field = form.querySelector(ROOT_PATH_FIELD);
            if (field && !field.value && data.rootPath) {
                field.value = data.rootPath;
            }
        });
    }

    /**
     * Writes one line into the status area under the Save report to DAM button.
     *
     * Input:  kind    - "busy" (spinner), "done" (green tick) or "error" (red)
     *         text    - e.g. "Saving the report… 12 s", "Saved /content/dam/.../offer-listing-report.xlsx"
     * Output: the <span> holding the text (scbSaveReport updates it every second for the timer).
     */
    function scbReportStatus(kind, text) {
        var status = document.querySelector(REPORT_STATUS);
        status.textContent = "";
        status.className = REPORT_STATUS.substring(1) + " " + REPORT_STATUS.substring(1) + "--" + kind;
        if (kind === "busy") {
            var spinner = new Coral.Wait();
            spinner.size = "S";
            status.appendChild(spinner);
        } else {
            var icon = new Coral.Icon();
            icon.icon = kind === "done" ? "checkCircle" : "alert";
            icon.size = "S";
            status.appendChild(icon);
        }
        var label = document.createElement("span");
        label.textContent = text;
        status.appendChild(label);
        return label;
    }

    /**
     * Runs when "Save report to DAM" is clicked: asks the server to build the report of the picked
     * folder and save it in DAM, shows progress while waiting, then where it was saved.
     *
     * Input:  button - the Save report to DAM button (its data-report-url is
     *                  /apps/aemcloudproject/cfbulkupload/content/cfbulkupload/report.json)
     * Output: nothing returned. On the page:
     *   no folder picked: line "⚠ Select the fragment folder first."
     *   while waiting: button disabled, label "Saving report…", line "[spinner] Saving the report… 12 s"
     *   HTTP 200:      line "✔ Saved /content/dam/.../offer-listing-report-2026-10-20-14-05-33.xlsx  Open in Assets"
     *   HTTP 400/500:  line "⚠ ..." (the servlet's message)
     *   always:        button enabled again, label "Save report to DAM"
     */
    function scbSaveReport(button) {
        var started = Date.now();
        var rootPath = scbFieldValue(document.querySelector(FORM_SELECTOR), ROOT_PATH_FIELD);
        if (!rootPath) {
            scbReportStatus("error", "Select the fragment folder first.");
            return;
        }

        button.disabled = true;
        button.label.textContent = LABEL_REPORT_BUSY;
        var label = scbReportStatus("busy", "Saving the report… 0 s");
        // Every second: "Saving the report… 1 s", "… 2 s", ...
        var timer = window.setInterval(function () {
            label.textContent = "Saving the report… " + Math.round((Date.now() - started) / 1000) + " s";
        }, 1000);

        // jQuery POST, so granite.csrf.standalone adds the CSRF token.
        $.ajax({
            url: button.dataset.reportUrl,
            type: "POST",
            data: { rootPath: rootPath },
            dataType: "json"
        }).done(function (data) {
            var saved = scbReportStatus("done", "Saved " + data.path);
            var link = document.createElement("a");
            link.href = "/assetdetails.html" + data.path;
            link.target = "_blank";
            link.className = "scb-cf-bulk-upload__report-link";
            link.textContent = "Open in Assets";
            saved.appendChild(document.createTextNode(" "));
            saved.appendChild(link);
        }).fail(function (xhr) {
            var data = xhr.responseJSON;
            scbReportStatus("error", data && data.message ? data.message
                : "Request failed (HTTP " + xhr.status + ").");
        }).always(function () {
            // Runs last in every case: stop the timer, give the button back.
            window.clearInterval(timer);
            button.disabled = false;
            button.label.textContent = LABEL_REPORT;
        });
    }

    // Listen for "submit" on the whole document, but only react when it comes from our form.
    // Works even if the form is rendered after this script runs.
    $(document).on("submit", FORM_SELECTOR, scbSubmitCfBulkUpload);

    // "Final run" button -> final run of the dry-run-checked workbook.
    $(document).on("click", FINAL_RUN_SELECTOR, function (event) {
        var form = event.currentTarget.closest(FORM_SELECTOR);
        if (lastDryRun) {
            scbRun(form, false);
        }
    });

    // Picking another workbook or folder disables Final run until a new dry run passes.
    $(document).on("change", FILE_PATH_FIELD + ", " + ROOT_PATH_FIELD, function (event) {
        scbUpdateFinalRun(event.currentTarget.closest(FORM_SELECTOR));
    });

    // Pre-fill the folder picker once the page (and our form) is ready.
    $(document).on("foundation-contentloaded", function () {
        var form = document.querySelector(FORM_SELECTOR);
        if (form) {
            scbPrefillFolder(form);
        }
    });

    // "Save report to DAM" button -> scbSaveReport.
    $(document).on("click", REPORT_BUTTON, function (event) {
        scbSaveReport(event.currentTarget);
    });

// Granite.$ is the jQuery that AEM's Granite UI pages load; CSRF is patched into it.
})(window, document, Granite.$);
