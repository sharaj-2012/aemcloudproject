(function ($, workflow) {
    "use strict";

    var ACTION_SELECTOR = ".aemcloudproject-assets-activate-workflow";
    var controller = workflow.create({
        containerSelector: "coral-dialog, .coral-Dialog",
        errorMessage: "The configured workflow is not available for the selected asset(s)."
    });

    $(document).on("click", ACTION_SELECTOR, function () {
        // The button's native DAM class and foundation.dialog action open the dialog.
        controller.start(workflow.readConfig(this));
    });
    $(document).on("click", ".cq-damadmin-admin-actions-createworkflow", function () {
        if (!$(this).is(ACTION_SELECTOR)) {
            controller.cancel();
        }
    });
}(Granite.$, window.AemCloudProjectWorkflow));
