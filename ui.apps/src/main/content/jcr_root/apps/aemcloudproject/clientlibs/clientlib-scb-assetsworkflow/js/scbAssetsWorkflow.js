/*
 * SCB Workflows dialog in the Assets console (aemcloudproject/content/scbassetsworkflowdialog).
 *
 * The OOTB Assets workflow script submits the dialog: it asks to confirm the number of assets, then
 * posts the workflow from that confirmation and stays on the console. When the dialog was opened
 * with ?showPreview=true (Request for Activation), show the Preview URLs of the assets the workflow
 * was started for (SCB.PreviewUrls, clientlib-scb-previewurls) once that post has succeeded.
 */
(function ($, document, window) {
    "use strict";

    var DIALOG_SELECTOR = ".scb-AssetsWorkflowDialog";
    var START_SELECTOR = DIALOG_SELECTOR + " .cq-common-admin-timeline-toolbar-actions-workflow-action-ok";
    var CONFIRMATION_SELECTOR = "#workflowLargeAssetsWarning";
    var SHOW_PREVIEW_SETTING = "scbShowPreview";

    // Set when Start is clicked in a dialog that shows Preview URLs, and taken by the next workflow
    // post, which the OOTB script sends from its confirmation.
    var showPreviewPending = false;

    $(document).on("click", START_SELECTOR, function () {
        showPreviewPending = $(this).closest(DIALOG_SELECTOR).attr("data-scb-show-preview") === "true";
    });

    // Closing the confirmation without proceeding starts no workflow. Proceeding posts the workflow
    // before the confirmation closes, so by then the flag has been taken.
    $(document).on("coral-overlay:close", CONFIRMATION_SELECTOR, function () {
        showPreviewPending = false;
    });

    $(document).on("ajaxSend", function (event, jqXHR, settings) {
        if (showPreviewPending && /^post$/i.test(settings.type) && /\/workflow\/instances$/.test(settings.url)) {
            settings[SHOW_PREVIEW_SETTING] = true;
            showPreviewPending = false;
        }
    });

    $(document).on("ajaxSuccess", function (event, jqXHR, settings) {
        if (settings[SHOW_PREVIEW_SETTING]) {
            window.SCB.PreviewUrls.show(new URLSearchParams(settings.data).getAll("payload"));
        }
    });
}(Granite.$, document, window));
