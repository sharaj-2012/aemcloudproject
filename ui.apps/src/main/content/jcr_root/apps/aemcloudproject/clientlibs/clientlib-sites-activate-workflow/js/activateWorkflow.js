(function ($, window, document) {
    "use strict";

    var workflow = window.AemCloudProjectWorkflow;
    var ACTION_SELECTOR = ".aemcloudproject-sites-activate-workflow";
    // Both Sites' and Experience Fragments' native "Workflow" action share this rel.
    var NATIVE_ACTION_SELECTOR = ".cq-siteadmin-admin-createworkflow, [rel~='cq-siteadmin-admin-createworkflow']";
    var PENDING_KEY = "aemcloudproject.sites.activateWorkflow.pending";
    var PENDING_MAX_AGE = 60000;

    function clearStoredPending() {
        try {
            window.sessionStorage.removeItem(PENDING_KEY);
        } catch (error) {
            window.console.warn("Unable to clear Activate Workflow state.", error);
        }
    }

    var controller = workflow.create({
        // startbulkworkflows.html renders its model select inside this wizard chrome.
        containerSelector: "coral-dialog, .coral-Dialog, .foundation-wizard, coral-wizard",
        timeout: 20000,
        errorMessage: "The configured workflow is not available for the selected page(s).",
        dialogTitle: "Activate Workflow",
        onComplete: clearStoredPending
    });

    function activate(customAction) {
        // The button lives in the Create popover; the native "Workflow" link is its sibling.
        var container = $(customAction).closest("coral-popover, .coral-Popover, coral-anchorlist, .coral-AnchorList");
        var nativeAction = workflow.findNativeAction(container.length ? container : $(document),
            NATIVE_ACTION_SELECTOR, "Workflow");
        controller.cancel();
        if (!nativeAction) {
            workflow.notifyError("The standard Workflow action is not available.");
            return;
        }
        var config = workflow.readConfig(customAction);
        controller.start(config);
        // This action navigates the whole page to startbulkworkflows.html; persist intent
        // through that navigation so the destination page can pick it back up.
        try {
            window.sessionStorage.setItem(PENDING_KEY, JSON.stringify({ created: Date.now(), config: config }));
        } catch (error) {
            window.console.warn("Unable to persist Activate Workflow state.", error);
        }
        nativeAction.click();
    }

    // Sites/XF console menus can consume bubbling clicks before Granite dispatches the custom
    // action; handle only our own button, in the capture phase, before that happens.
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

    // On startbulkworkflows.html itself: restore the intent stored before navigating here.
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
                stored.config) {
            controller.start(stored.config);
        } else if (stored) {
            clearStoredPending();
        }
    });
}(Granite.$, window, document));
