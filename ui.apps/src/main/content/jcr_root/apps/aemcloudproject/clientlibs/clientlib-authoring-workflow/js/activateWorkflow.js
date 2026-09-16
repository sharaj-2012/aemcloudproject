(function ($, window, document) {
    "use strict";

    // AEM can embed this hook before it emits the shared clientlib script tag.
    $(function () {
        var workflow = window.AemCloudProjectWorkflow;
        var ACTION_SELECTOR = ".aemcloudproject-pageinfo-activate-workflow";
        var DIALOG_SELECTOR = ".aemcloudproject-activate-workflow-dialog";
        var MODEL_NAME_SELECTOR = ".aemcloudproject-activate-workflow-modelname";
        var TITLE_SELECTOR = ".aemcloudproject-activate-workflow-title";
        var SUBMIT_SELECTOR = ".aemcloudproject-activate-workflow-submit";

        function modelLabel(model) {
            return model.substring(model.lastIndexOf("/") + 1);
        }

        $(document).on("click", ACTION_SELECTOR, function (event) {
            event.preventDefault();
            event.stopPropagation();
            var model = this.getAttribute("data-workflow-model");
            var dialog = document.querySelector(DIALOG_SELECTOR);
            if (!dialog || !model) {
                workflow.notifyError("The Activate Workflow dialog is not available for this page.");
                return;
            }
            dialog.querySelector("input[name='model']").value = model;
            dialog.querySelector(MODEL_NAME_SELECTOR).value = modelLabel(model);
            dialog.querySelector(TITLE_SELECTOR).value = "";
            // Coral keeps the dialog in the document; show() moves it into the overlay stack.
            if (typeof dialog.show === "function") {
                dialog.show();
            } else {
                dialog.open = true;
            }
        });

        $(document).on("submit", DIALOG_SELECTOR + " form", function (event) {
            // Post in the background so the author stays in the editor.
            event.preventDefault();
            var form = this;
            var dialog = $(form).closest(DIALOG_SELECTOR).get(0);
            var submit = form.querySelector(SUBMIT_SELECTOR);
            if (!form.querySelector("input[name='model']").value) {
                workflow.notifyError("No workflow model is configured for this action.");
                return;
            }
            submit.setAttribute("disabled", "disabled");
            // Granite's jQuery adds the CSRF token to this request.
            $.post(form.getAttribute("action"), $(form).serialize()).done(function () {
                if (typeof dialog.hide === "function") {
                    dialog.hide();
                } else {
                    dialog.open = false;
                }
                workflow.notify("Activate Workflow", "Workflow started.", "success");
            }).fail(function () {
                workflow.notifyError("The workflow could not be started.");
            }).always(function () {
                submit.removeAttribute("disabled");
            });
        });
    });
}(Granite.$, window, document));
