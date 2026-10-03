/*
 * CF Bulk Upload page: posts the selected workbook path to ScbCfBulkUploadServlet and shows
 * the reply. Uses jQuery ajax so granite.csrf.standalone adds the CSRF token.
 */
(function (document, $) {
    "use strict";

    var FORM_SELECTOR = ".scb-cf-bulk-upload";
    var FILE_PATH_FIELD = "foundation-autocomplete[name='filePath']";
    var SUBMIT_SELECTOR = ".scb-cf-bulk-upload__submit";
    var RESULT_SELECTOR = ".scb-cf-bulk-upload__result";
    var ERROR_CLASS = "scb-cf-bulk-upload__result--error";

    function scbShowResult(form, message, isError) {
        var result = form.querySelector(RESULT_SELECTOR);
        result.textContent = message;
        result.classList.toggle(ERROR_CLASS, Boolean(isError));
    }

    function scbSetBusy(form, busy) {
        var submit = form.querySelector(SUBMIT_SELECTOR);
        submit.disabled = busy;
    }

    function scbSubmitCfBulkUpload(event) {
        event.preventDefault();
        var form = event.currentTarget;
        var field = form.querySelector(FILE_PATH_FIELD);
        var filePath = field && field.value ? field.value.trim() : "";

        if (!filePath) {
            scbShowResult(form, "Select a workbook first.", true);
            return;
        }

        scbSetBusy(form, true);
        scbShowResult(form, "Checking " + filePath + " ...", false);

        $.ajax({
            url: form.getAttribute("action"),
            type: "POST",
            data: { filePath: filePath },
            dataType: "json"
        }).done(function (data) {
            scbShowResult(form, JSON.stringify(data, null, 2), false);
        }).fail(function (xhr) {
            var data = xhr.responseJSON;
            scbShowResult(form, data && data.message ? data.message : "Request failed (HTTP " + xhr.status + ").", true);
        }).always(function () {
            scbSetBusy(form, false);
        });
    }

    $(document).on("submit", FORM_SELECTOR, scbSubmitCfBulkUpload);

})(document, Granite.$);
