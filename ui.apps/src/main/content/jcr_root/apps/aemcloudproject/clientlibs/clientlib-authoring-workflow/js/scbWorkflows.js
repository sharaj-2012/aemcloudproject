/*
 * SCB Workflows: opens the Start Workflow dialog rendered by
 * aemcloudproject/components/authoring/scbstartworkflow for the header bar action that was
 * clicked, and submits it the same way the OOTB page editor dialog does.
 *
 * For actions flagged with data-show-preview (Request for Activation), it then shows the page's
 * Preview URL (SCB.PreviewUrls, clientlib-scb-previewurls) as soon as the workflow has started.
 */
(function ($, ns, channel, window) {
    "use strict";

    var ACTION_SELECTOR = ".scb-workflow-action";
    var DIALOG_SELECTOR = ".scb-WorkflowStart";
    var TITLE_SELECTOR = ".scb-WorkflowStart-title";
    var SUBMIT_SELECTOR = ".scb-WorkflowStart-submit";

    var SHOW_PREVIEW_DATA = "scbShowPreview";

    function notify(message, type) {
        ns.ui.helpers.notify({
            content: message,
            type: type
        });
    }

    function findDialog(model) {
        // Compared as attribute values, so model paths never need CSS selector escaping.
        return $(DIALOG_SELECTOR).filter(function () {
            return this.getAttribute("data-scb-workflow-model") === model;
        }).get(0);
    }

    channel.on("click", ACTION_SELECTOR, function (event) {
        event.preventDefault();

        var popover = $(this).closest("coral-popover").get(0);
        if (popover) {
            popover.open = false;
        }

        var dialog = findDialog(this.getAttribute("data-workflow-model"));
        if (!dialog) {
            notify(Granite.I18n.get("This workflow is not available. Check that the workflow model exists and that you can read it."),
                ns.ui.helpers.NOTIFICATION_TYPES.ERROR);
            return;
        }

        // Like the OOTB dialog: reset the title and target the page currently in the editor,
        // which can differ from the page the editor was opened with.
        dialog.querySelector(TITLE_SELECTOR).value = "";
        dialog.querySelector("input[name=payload]").value =
            Granite.HTTP.internalize(ns.ContentFrame.getContentPath());
        dialog.querySelector(SUBMIT_SELECTOR).disabled = false;
        $(dialog).data(SHOW_PREVIEW_DATA, this.getAttribute("data-show-preview") === "true");

        dialog.show();
    });

    channel.on("click", DIALOG_SELECTOR + " " + SUBMIT_SELECTOR, function (event) {
        var button = event.currentTarget;
        var dialog = $(button).closest(DIALOG_SELECTOR).get(0);
        var $form = $(button).closest("form");

        button.disabled = true;

        // Granite's jQuery prefilter adds the CSRF token.
        $.ajax({
            url: $form.attr("action"),
            type: "POST",
            dataType: "html",
            data: $form.serialize()
        }).done(function () {
            var showPreview = $(dialog).data(SHOW_PREVIEW_DATA);
            var payload = $form.find("input[name=payload]").val();

            dialog.hide();
            channel.one("cq-editor-loaded.scb-workflow-started", function () {
                notify(Granite.I18n.get("Workflow Started"), ns.ui.helpers.NOTIFICATION_TYPES.INFO);
                if (showPreview) {
                    window.SCB.PreviewUrls.show([payload]);
                }
            });
            ns.ContentFrame.reload();
        }).fail(function (jqXHR) {
            var errorMsg = jqXHR.status === 403 ?
                Granite.I18n.get("Your CSRF token may have expired. Refresh the page or login again.") :
                $("<div>").html(jqXHR.responseText).find("#Message").text();

            notify(Granite.I18n.get("The workflow failed to start. ") + errorMsg,
                ns.ui.helpers.NOTIFICATION_TYPES.ERROR);
            button.disabled = false;
        });
    });
}(jQuery, Granite.author, jQuery(document), window));
