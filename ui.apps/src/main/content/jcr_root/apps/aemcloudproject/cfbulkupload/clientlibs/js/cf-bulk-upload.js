/*
 * SCB CF bulk upload page: posts the selected workbook path to ScbCfBulkUploadServlet and shows
 * the reply. Uses jQuery ajax so granite.csrf.standalone adds the CSRF token.
 *
 * Flow:
 *   author clicks "Run"
 *     -> browser fires "submit" on <form class="scb-cf-bulk-upload">
 *     -> scbSubmitCfBulkUpload(): checks a file is picked, reads the Dry run checkbox
 *     -> scbSendUpload(): spinner, then POST filePath=...&dryRun=true|false to .../upload.json
 *        (no confirm dialog: unticked Dry run writes straight away)
 *     -> HTTP 200: scbRenderResults() = dry-run banner (dry run only) + "12 fail · 138 pass" + Download CSV
 *                  + results table in a fixed-height scroll box, failures first
 *     -> HTTP 400 / other: scbShowAlert() = red Coral alert with the problem(s)
 *   author clicks "Generate report"
 *     -> scbGenerateReport(): button disabled ("Generating report…"), "[spinner] Building the Excel… 12 s"
 *        under it; fetches .../report.xlsx; then saves the file and shows
 *        "✔ Downloaded offer-listing-report-2026-10-08.xlsx (21 fragments)" — or a red error line
 *
 * The whole file is wrapped in a function that runs immediately, so none of these functions
 * or variables leak into the global "window" scope.
 */
(function (window, document, $) {
    "use strict";

    // CSS selectors for the elements rendered by the Granite page (.content.xml, granite:class).
    var FORM_SELECTOR = ".scb-cf-bulk-upload";                          // the <form>
    var FILE_PATH_FIELD = "foundation-autocomplete[name='filePath']";   // the path browser element
    var DRY_RUN_FIELD = "coral-checkbox[name='dryRun']";                // the "Dry run" checkbox
    var SUBMIT_SELECTOR = ".scb-cf-bulk-upload__submit";                // the Run button
    var RESULT_SELECTOR = ".scb-cf-bulk-upload__result";                // the area under the two cards

    var REPORT_BUTTON = ".scb-cf-bulk-upload__report";                 // the Generate report button
    var REPORT_STATUS = ".scb-cf-bulk-upload__report-status";           // the line under it

    // Generate report button labels: idle / while the report is being built.
    var LABEL_REPORT = "Generate report";
    var LABEL_REPORT_BUSY = "Generating report…";                 // … = "…"

    // Table / CSV columns: header text -> key in each result object from the servlet.
    var COLUMNS = [
        { title: "Sheet", key: "sheet" },
        { title: "Row", key: "row" },
        { title: "Name", key: "name" },
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
     * Is the "Dry run" checkbox ticked?
     * Input:  form
     * Output: true (ticked, the default) or false. No checkbox found -> true, to be safe.
     */
    function scbIsDryRun(form) {
        var box = form.querySelector(DRY_RUN_FIELD);
        return box ? box.checked : true;
    }

    /**
     * Enables or disables the button while a request is running.
     * Input:  form, busy (true = request running)
     * Output: nothing returned; the button is disabled (busy) or enabled again.
     */
    function scbSetBusy(form, busy) {
        form.querySelector(SUBMIT_SELECTOR).disabled = busy;
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
     * Shows the outcome of a run: dry-run banner, counts + Download CSV, and the results table.
     *
     * Input:  results - the "results" array of the servlet's HTTP 200 reply, e.g.
     *           [{sheet: "category", row: 14, name: "dining", action: "UPDATE", result: "FAIL",
     *             details: "promoCode: not a field of model category"},
     *            {sheet: "merchant-venue", row: 2, name: "central-mall-branch", action: "CREATE", result: "PASS",
     *             details: "/content/dam/aemcloudproject/cfs/offer-listing/merchants/venues/central-mall-branch"}, ...]
     *         dryRun  - the reply's "dryRun" flag
     * Output: nothing returned; the result area now holds
     *           <coral-alert variant="warning">Dry run: nothing was written.</coral-alert>   (dry run only)
     *           <div class="scb-cf-bulk-upload__summary">[12 fail] [138 pass] 150 rows   [Download CSV]</div>
     *           <div class="scb-cf-bulk-upload__table-box"><table>...</table></div>       (scrolls inside, header sticky)
     */
    function scbRenderResults(results, dryRun) {
        var box = scbResultBox();
        box.textContent = "";
        if (dryRun) {
            box.appendChild(scbAlert("warning", "Dry run: nothing was written.",
                "Untick Dry run and click Run to create or update these fragments."));
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
     * Puts FAIL rows before PASS rows, keeping sheet / row order inside each group.
     * Input:  [venue row 2 PASS, venue row 3 PASS, category row 14 FAIL, offer row 87 FAIL]
     * Output: [category row 14 FAIL, offer row 87 FAIL, venue row 2 PASS, venue row 3 PASS]   (a new array)
     */
    function scbFailuresFirst(results) {
        var failed = results.filter(function (r) { return r.result !== "PASS"; });
        var passed = results.filter(function (r) { return r.result === "PASS"; });
        return failed.concat(passed);
    }

    /**
     * Builds the line above the table: fail / pass chips, total, Download CSV button.
     * Input:  results
     * Output: a <div>, e.g. [✖ 12 fail] [✔ 138 pass] 150 rows ............ [⬇ Download CSV]
     *         Clicking the button calls scbDownloadCsv(results) (all rows, original order).
     */
    function scbRenderSummary(results) {
        var passCount = results.filter(function (r) { return r.result === "PASS"; }).length;
        var failCount = results.length - passCount;

        var summary = document.createElement("div");
        summary.className = "scb-cf-bulk-upload__summary";
        summary.appendChild(scbChip("scb-cf-bulk-upload__chip--fail", failCount + " fail"));
        summary.appendChild(scbChip("scb-cf-bulk-upload__chip--pass", passCount + " pass"));

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
     *   | Sheet          | Row | Name                | Action | Result | Details                                    |
     *   | category       | 14  | dining              | UPDATE | [Fail] | promoCode: not a field of model category   |
     *   | merchant-venue | 2   | central-mall-branch | CREATE | [Pass] | /content/dam/.../venues/central-mall-branch|
     *   | venues         | –   | –                   | –      | [Fail] | Sheet: "venues" is not a known CF model    |
     */
    function scbRenderTable(results) {
        var table = document.createElement("table");
        table.className = "scb-cf-bulk-upload__table";

        // Header row: Sheet | Row | Name | Action | Result | Details (sticky inside the scroll box)
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
                    // PASS -> green "Pass" badge, FAIL -> red "Fail" badge
                    var pass = value === "PASS";
                    var badge = document.createElement("span");
                    badge.className = "scb-cf-bulk-upload__badge scb-cf-bulk-upload__badge--" + (pass ? "pass" : "fail");
                    badge.textContent = pass ? "Pass" : "Fail";
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
     *   Sheet,Row,Name,Action,Result,Details
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
     * Input:  form, filePath e.g. "/content/dam/aemcloudproject/imports/cf-import-combined.xlsx", dryRun true / false
     * Output: nothing returned. Shows a spinner, then either
     *   - HTTP 200: scbRenderResults(results, dryRun), from
     *       { "status": "ok" | "error", "dryRun": true, "path": "...", "results": [ {...}, ... ] }
     *   - HTTP 400 with "errors": red alert "Fix these problems in the workbook" + one problem per line (nothing written)
     *   - HTTP 400 with "message": red alert with that message, e.g. "No file found at /content/dam/.../abc.xlsx."
     *   - anything else (500, network down): red alert "Request failed (HTTP 500)."
     */
    function scbSendUpload(form, filePath, dryRun) {
        scbSetBusy(form, true);
        scbShowWait((dryRun ? "Checking " : "Running ") + filePath);

        $.ajax({
            // The form's action attribute from .content.xml:
            // /apps/aemcloudproject/cfbulkupload/content/cfbulkupload/upload.json
            url: form.getAttribute("action"),
            type: "POST",
            // Sent as form data: filePath=%2Fcontent%2Fdam%2F...%2Fcf-import-combined.xlsx&dryRun=true
            data: { filePath: filePath, dryRun: dryRun ? "true" : "false" },
            // Parse the reply as JSON, so "data" below is an object, not a string.
            dataType: "json"
        }).done(function (data) {
            scbRenderResults(data.results || [], data.dryRun);
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
            // Runs after done or fail: re-enable the button.
            scbSetBusy(form, false);
        });
    }

    /**
     * Runs when the form is submitted (button clicked or Enter pressed).
     *
     * Input:  event - the browser's submit event; event.currentTarget is the <form>
     * Output: nothing returned.
     *   - no file picked          -> red alert "Select a workbook first"
     *   - file picked             -> scbSendUpload(..., dryRun) straight away; dryRun = the checkbox
     *                                (ticked: check only, unticked: create / update)
     */
    function scbSubmitCfBulkUpload(event) {
        // Stop the browser's normal form submit, which would navigate away from the page.
        event.preventDefault();
        var form = event.currentTarget;
        // The Coral path browser element; its .value is the picked path, e.g.
        // "/content/dam/aemcloudproject/imports/cf-import-combined.xlsx", or "" when nothing is picked.
        var field = form.querySelector(FILE_PATH_FIELD);
        var filePath = field && field.value ? field.value.trim() : "";
        var dryRun = scbIsDryRun(form);

        if (!filePath) {
            // Same message as the servlet gives, without a round trip to AEM.
            scbShowAlert("error", "Select a workbook first", "");
            return;
        }
        scbSendUpload(form, filePath, dryRun);
    }

    /**
     * Writes one line into the status area under the Generate report button.
     *
     * Input:  kind    - "busy" (spinner), "done" (green tick) or "error" (red)
     *         text    - e.g. "Building the Excel… 12 s", "Downloaded offer-listing-report-2026-10-08.xlsx (21 fragments)"
     * Output: the <span> holding the text (scbGenerateReport updates it every second for the timer).
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
     * Reads the file name the servlet chose from its Content-Disposition header.
     * Input:  'attachment; filename="offer-listing-report-2026-10-08.xlsx"'
     * Output: "offer-listing-report-2026-10-08.xlsx"   (fallback "offer-listing-report.xlsx" if missing)
     */
    function scbFileName(contentDisposition) {
        var match = /filename="([^"]+)"/.exec(contentDisposition || "");
        return match ? match[1] : "offer-listing-report.xlsx";
    }

    /**
     * Runs when "Generate report" is clicked: fetches the report in the background, shows progress
     * while waiting, then saves the file.
     *
     * Input:  button - the Generate report button (its data-report-url is
     *                  /apps/aemcloudproject/cfbulkupload/content/cfbulkupload/report.xlsx)
     * Output: nothing returned. On the page:
     *   while waiting: button disabled, label "Generating report…", line "[spinner] Building the Excel… 12 s"
     *                  (the seconds count up, so the author can see it's still working)
     *   HTTP 200:      the browser saves offer-listing-report-2026-10-08.xlsx;
     *                  line "✔ Downloaded offer-listing-report-2026-10-08.xlsx (21 fragments)"
     *                  (file name from Content-Disposition, count from X-Fragment-Count)
     *   HTTP 500:      line "⚠ Could not build the report: ..." (the servlet's message)
     *   network error: line "⚠ Request failed: ..."
     *   always:        button enabled again, label "Generate report"
     */
    function scbGenerateReport(button) {
        var url = button.dataset.reportUrl;
        var started = Date.now();

        button.disabled = true;
        button.label.textContent = LABEL_REPORT_BUSY;
        var label = scbReportStatus("busy", "Building the Excel… 0 s");
        // Every second: "Building the Excel… 1 s", "… 2 s", ...
        var timer = window.setInterval(function () {
            label.textContent = "Building the Excel… " + Math.round((Date.now() - started) / 1000) + " s";
        }, 1000);

        var fileName;
        var count;
        // fetch sends the author's login cookie (same origin); a GET needs no CSRF token.
        fetch(url, { credentials: "same-origin" })
            .then(function (response) {
                if (!response.ok) {
                    // HTTP 500: the servlet's text, e.g. "Could not build the report: ..."
                    return response.text().then(function (text) {
                        throw new Error(text || "Request failed (HTTP " + response.status + ").");
                    });
                }
                fileName = scbFileName(response.headers.get("Content-Disposition"));
                count = response.headers.get("X-Fragment-Count");
                return response.blob();         // the whole .xlsx, in the browser's memory
            })
            .then(function (blob) {
                // Same trick as the CSV download: a temporary link with the download attribute.
                var objectUrl = URL.createObjectURL(blob);
                var link = document.createElement("a");
                link.href = objectUrl;
                link.download = fileName;
                document.body.appendChild(link);
                link.click();
                document.body.removeChild(link);
                URL.revokeObjectURL(objectUrl);
                scbReportStatus("done", "Downloaded " + fileName + (count !== null ? " (" + count + " fragments)" : ""));
            })
            .catch(function (error) {
                // Both "Could not build the report: ..." from the servlet and network failures land here.
                var message = error.message || String(error);
                scbReportStatus("error", message.indexOf("Could not") === 0 ? message : "Request failed: " + message);
            })
            .then(function () {
                // Runs last in every case: stop the timer, give the button back.
                window.clearInterval(timer);
                button.disabled = false;
                button.label.textContent = LABEL_REPORT;
            });
    }

    // Listen for "submit" on the whole document, but only react when it comes from our form.
    // Works even if the form is rendered after this script runs.
    $(document).on("submit", FORM_SELECTOR, scbSubmitCfBulkUpload);

    // "Generate report" button -> scbGenerateReport.
    $(document).on("click", REPORT_BUTTON, function (event) {
        scbGenerateReport(event.currentTarget);
    });

// Granite.$ is the jQuery that AEM's Granite UI pages load; CSRF is patched into it.
})(window, document, Granite.$);
