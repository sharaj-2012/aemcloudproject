/*
 * CF Bulk Upload page: posts the selected workbook path to ScbCfBulkUploadServlet and shows
 * the reply. Uses jQuery ajax so granite.csrf.standalone adds the CSRF token.
 *
 * Flow:
 *   author clicks Submit
 *     -> browser fires "submit" on <form class="scb-cf-bulk-upload">
 *     -> scbSubmitCfBulkUpload() stops the normal page reload and POSTs
 *        filePath=/content/dam/aemcloudproject/imports/cf-import-combined.xlsx
 *        to /apps/aemcloudproject/cfbulkupload/content/cfbulkupload/upload.json
 *     -> scbShowResult() prints the servlet's JSON (or error message) under the button
 *
 * The whole file is wrapped in a function that runs immediately, so none of these functions
 * or variables leak into the global "window" scope.
 */
(function (document, $) {
    "use strict";

    // CSS selectors for the elements rendered by the Granite page (.content.xml, granite:class).
    var FORM_SELECTOR = ".scb-cf-bulk-upload";                          // the <form>
    var FILE_PATH_FIELD = "foundation-autocomplete[name='filePath']";   // the path browser element
    var SUBMIT_SELECTOR = ".scb-cf-bulk-upload__submit";                // the Submit button
    var RESULT_SELECTOR = ".scb-cf-bulk-upload__result";                // the box under the button
    var ERROR_CLASS = "scb-cf-bulk-upload__result--error";              // makes the result text red

    /**
     * Writes a message into the result box.
     *
     * Input:  form    - the <form> element
     *         message - e.g. "Select a workbook first." or the JSON text of the reply
     *         isError - true -> red text, false -> normal text
     * Output: nothing returned; the result box now shows the message.
     *
     * Example: scbShowResult(form, "Select a workbook first.", true)
     *          -> <div class="scb-cf-bulk-upload__result scb-cf-bulk-upload__result--error">Select a workbook first.</div>
     */
    function scbShowResult(form, message, isError) {
        var result = form.querySelector(RESULT_SELECTOR);
        // textContent (not innerHTML), so text from the server is never run as HTML.
        result.textContent = message;
        // Adds the error class when isError is true, removes it otherwise.
        result.classList.toggle(ERROR_CLASS, Boolean(isError));
    }

    /**
     * Enables or disables the Submit button while a request is running.
     *
     * Input:  form, busy (true = request running)
     * Output: nothing returned; the button is disabled (busy) or enabled again.
     */
    function scbSetBusy(form, busy) {
        var submit = form.querySelector(SUBMIT_SELECTOR);
        submit.disabled = busy;
    }

    /**
     * Runs when the form is submitted (Submit clicked or Enter pressed).
     *
     * Input:  event - the browser's submit event; event.currentTarget is the <form>
     * Output: nothing returned. Sends the request and, when it finishes, shows either
     *   - HTTP 200: the headers and rows of every sheet, e.g.
     *       { "status": "ok", "path": ".../cf-import-combined.xlsx",
     *         "sheets": { "offer-cta": { "headers": ["action", "offerCtaSlug", ...], "rows": [{ "action": "CREATE", ... }] }, ... } }
     *   - HTTP 400 with "errors": each bad header on its own line, in red
     *   - HTTP 400 with "message": the message only, in red, e.g. "No file found at /content/dam/.../abc.xlsx."
     *   - anything else (500, network down): "Request failed (HTTP 500)."
     */
    function scbSubmitCfBulkUpload(event) {
        // Stop the browser's normal form submit, which would navigate away from the page.
        event.preventDefault();
        var form = event.currentTarget;
        // The Coral path browser element; its .value is the picked path, e.g.
        // "/content/dam/aemcloudproject/imports/cf-import-combined.xlsx", or "" when nothing is picked.
        var field = form.querySelector(FILE_PATH_FIELD);
        var filePath = field && field.value ? field.value.trim() : "";

        if (!filePath) {
            // Same message as the servlet gives, without a round trip to AEM.
            scbShowResult(form, "Select a workbook first.", true);
            return;
        }

        scbSetBusy(form, true);
        scbShowResult(form, "Checking " + filePath + " ...", false);

        $.ajax({
            // The form's action attribute from .content.xml:
            // /apps/aemcloudproject/cfbulkupload/content/cfbulkupload/upload.json
            url: form.getAttribute("action"),
            type: "POST",
            // Sent as form data: filePath=%2Fcontent%2Fdam%2F...%2Fcf-import-combined.xlsx
            data: { filePath: filePath },
            // Parse the reply as JSON, so "data" below is an object, not a string.
            dataType: "json"
        }).done(function (data) {
            // HTTP 200: pretty-print the summary with 2-space indentation.
            scbShowResult(form, JSON.stringify(data, null, 2), false);
        }).fail(function (xhr) {
            // HTTP 400 from the servlet carries {"status":"error","errors":[...]} or
            // {"status":"error","message":"..."} in responseJSON.
            var data = xhr.responseJSON;
            if (data && data.errors) {
                scbShowResult(form, data.errors.join("\n"), true);
            } else {
                scbShowResult(form, data && data.message ? data.message : "Request failed (HTTP " + xhr.status + ").", true);
            }
        }).always(function () {
            // Runs after done or fail: re-enable Submit.
            scbSetBusy(form, false);
        });
    }

    // Listen for "submit" on the whole document, but only react when it comes from our form.
    // Works even if the form is rendered after this script runs.
    $(document).on("submit", FORM_SELECTOR, scbSubmitCfBulkUpload);

// Granite.$ is the jQuery that AEM's Granite UI pages load; CSRF is patched into it.
})(document, Granite.$);
