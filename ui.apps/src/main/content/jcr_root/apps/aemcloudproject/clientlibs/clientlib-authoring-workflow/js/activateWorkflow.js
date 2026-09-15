(function ($, window) {
    "use strict";

    // AEM can embed this hook before it emits the shared clientlib script tag.
    $(function () {
        var workflow = window.AemCloudProjectWorkflow;
        var ACTION_SELECTOR = ".aemcloudproject-pageinfo-activate-workflow";
        var NATIVE_ACTION_SELECTOR = ".js-editor-WorkflowStart-activator, .cq-authoring-pageinfo-startworkflow";
        var openingNativeAction = false;
        var controller = workflow.create({
            containerSelector: "coral-dialog, .coral-Dialog",
            errorMessage: "The configured workflow is not available for this page."
        });

        $(document).on("click", ACTION_SELECTOR, function (event) {
            event.preventDefault();
            event.stopPropagation();
            var nativeAction = workflow.findNativeAction($(this).closest("coral-popover, .coral-Popover"),
                NATIVE_ACTION_SELECTOR, "Start Workflow");
            controller.cancel();
            if (!nativeAction) {
                workflow.notifyError("The AEM Start Workflow action is not available.");
                return;
            }
            $(nativeAction).off("click.aemcloudprojectActivateWorkflow").on("click.aemcloudprojectActivateWorkflow", function () {
                if (!openingNativeAction) {
                    controller.cancel();
                }
            });
            controller.start(workflow.readConfig(this));
            openingNativeAction = true;
            try {
                nativeAction.click();
            } finally {
                openingNativeAction = false;
            }
        });
    });
}(Granite.$, window));
