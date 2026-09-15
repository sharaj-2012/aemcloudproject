(function ($, window) {
    "use strict";

    // AEM can embed this hook before it emits the shared clientlib script tag.
    $(function () {
        var workflow = window.AemCloudProjectWorkflow;
        var ACTION_SELECTOR = ".aemcloudproject-sites-activate-workflow";
        var NATIVE_ACTION_SELECTOR = ".cq-siteadmin-admin-createworkflow, [rel~='cq-siteadmin-admin-createworkflow'], " +
            ".cq-siteadmin-admin-actions-createworkflow, " +
            "[rel~='cq-siteadmin-admin-actions-createworkflow']";
        var PENDING_KEY = "aemcloudproject.sites.activateWorkflow.pending";
        var PENDING_MAX_AGE = 60000;
        var openingNativeAction = false;
        var controller = workflow.create({
            containerSelector: "coral-dialog, .coral-Dialog, .foundation-wizard, coral-wizard",
            timeout: 20000,
            errorMessage: "The configured workflow is not available for the selected page(s).",
            onComplete: clearStoredPending
        });

        function clearStoredPending() {
            try {
                window.sessionStorage.removeItem(PENDING_KEY);
            } catch (error) {
                window.console.warn("Unable to clear Activate Workflow state.", error);
            }
        }

        function activate(customAction) {
            var container = $(customAction).closest("coral-popover, .coral-Popover, coral-anchorlist, .coral-AnchorList");
            var nativeAction = workflow.findNativeAction(container.length ? container : $(document),
                NATIVE_ACTION_SELECTOR, "Workflow");
            controller.cancel();
            if (!nativeAction) {
                workflow.notifyError("The standard Sites Workflow action is not available.");
                return;
            }
            $(nativeAction).off("click.aemcloudprojectActivateWorkflow").on("click.aemcloudprojectActivateWorkflow", function () {
                if (!openingNativeAction) {
                    controller.cancel();
                }
            });
            var config = workflow.readConfig(customAction);
            controller.start(config);
            // Sites can navigate to a separate wizard document; that document restores this intent.
            try {
                window.sessionStorage.setItem(PENDING_KEY, JSON.stringify({ created: Date.now(), config: config }));
            } catch (error) {
                window.console.warn("Unable to persist Activate Workflow state.", error);
            }
            openingNativeAction = true;
            try {
                nativeAction.click();
            } finally {
                openingNativeAction = false;
            }
        }

        // Sites menus can consume bubbling clicks before Granite dispatches the custom action.
        // Handle only our button in capture, then let this event finish before clicking the native action.
        document.addEventListener("click", function (event) {
            var customAction = $(event.target).closest(ACTION_SELECTOR).get(0);
            if (!customAction) {
                return;
            }
            event.preventDefault();
            event.stopImmediatePropagation();
            window.setTimeout(function () {
                activate(customAction);
            }, 0);
        }, true);
        $(document).on("click", ".foundation-wizard-control", function () {
            if ($(this).attr("data-foundation-wizard-control-action") === "cancel") {
                controller.cancel();
            }
        });
        $(function () {
            var stored;
            try {
                stored = JSON.parse(window.sessionStorage.getItem(PENDING_KEY));
            } catch (error) {
                clearStoredPending();
                return;
            }
            if (stored && typeof stored.created === "number" &&
                    Date.now() - stored.created >= 0 && Date.now() - stored.created < PENDING_MAX_AGE &&
                    stored.config && Array.isArray(stored.config.names) &&
                    stored.config.names.every(function (name) { return typeof name === "string"; })) {
                controller.start(stored.config);
            } else {
                clearStoredPending();
            }
        });
    });
}(Granite.$, window));
