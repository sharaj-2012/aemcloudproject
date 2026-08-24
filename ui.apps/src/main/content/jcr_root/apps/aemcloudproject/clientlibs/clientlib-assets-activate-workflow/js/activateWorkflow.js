(function ($, channel, window) {
    "use strict";

    var ACTION_SELECTOR = ".aemcloudproject-assets-activate-workflow";
    var LOCKED_CLASS = "aemcloudproject-assets-activate-workflow-locked";
    var WORKFLOW_DIALOG_SELECTOR = "coral-dialog, .coral-Dialog";
    var WORKFLOW_FIELD_SELECTOR = "coral-select[name='model'], select[name='model']";
    var CONFIGURATION_TIMEOUT = 10000;
    var activationPending = false;
    var pendingAction;
    var configurationObserver;
    var configurationTimer;

    function normalize(value) {
        return (value || "").toLowerCase().replace(/[^a-z0-9]/g, "");
    }

    function getConfiguredWorkflowNames(action) {
        return [
            action.getAttribute("data-workflow-model-title"),
            action.getAttribute("data-workflow-model-title-alias")
        ].filter(Boolean).map(normalize);
    }

    function getWorkflowItems(field) {
        if (field.items && typeof field.items.getAll === "function") {
            return field.items.getAll();
        }

        return Array.prototype.slice.call(
            field.querySelectorAll("coral-select-item, option")
        );
    }

    function getItemLabel(item) {
        return (item.textContent || item.innerText || "").trim();
    }

    function stopWaiting() {
        if (configurationObserver) {
            configurationObserver.disconnect();
            configurationObserver = null;
        }

        if (configurationTimer) {
            window.clearTimeout(configurationTimer);
            configurationTimer = null;
        }
    }

    function unlockWorkflowFields(root) {
        $(root || document).find("." + LOCKED_CLASS).each(function () {
            this.classList.remove(LOCKED_CLASS);
            this.removeAttribute("aria-disabled");
            this.removeAttribute("tabindex");
            this.removeAttribute("data-aemcloudproject-locked-value");
            $(this).off("change.aemcloudprojectAssetsActivateWorkflow");
        });
    }

    function notifyError(message) {
        var ui = $(window).adaptTo("foundation-ui");

        if (ui && typeof ui.notify === "function") {
            ui.notify(
                Granite.I18n.get("Activate Workflow"),
                Granite.I18n.get(message),
                "error"
            );
        } else {
            window.console.error(message);
        }
    }

    function findWorkflowField() {
        var workflowDialog = $(WORKFLOW_DIALOG_SELECTOR).filter(function () {
            return $(this).is(":visible") &&
                $(this).text().indexOf("Create Workflow") !== -1;
        }).last();
        var fields = workflowDialog.find(WORKFLOW_FIELD_SELECTOR).filter(":visible");

        if (!fields.length) {
            fields = workflowDialog.find("coral-select, select").filter(":visible");
        }

        return fields.filter(function () {
            return getWorkflowItems(this).some(function (item) {
                return getConfiguredWorkflowNames(pendingAction || document.body)
                    .indexOf(normalize(getItemLabel(item))) !== -1;
            });
        }).get(0) || fields.get(0);
    }

    function lockWorkflowField(field, value) {
        field.classList.add(LOCKED_CLASS);
        field.setAttribute("aria-disabled", "true");
        field.setAttribute("tabindex", "-1");
        field.setAttribute("data-aemcloudproject-locked-value", value);

        $(field)
            .off("change.aemcloudprojectAssetsActivateWorkflow")
            .on("change.aemcloudprojectAssetsActivateWorkflow", function () {
                var lockedValue = this.getAttribute("data-aemcloudproject-locked-value");

                if (lockedValue && this.value !== lockedValue) {
                    this.value = lockedValue;
                }
            });
    }

    function configurePracticeWorkflow() {
        var field = findWorkflowField();
        var workflowNames;
        var workflowItem;
        var lockedValue;

        if (!activationPending || !pendingAction || !field) {
            return false;
        }

        workflowNames = getConfiguredWorkflowNames(pendingAction);
        workflowItem = getWorkflowItems(field).find(function (item) {
            return workflowNames.indexOf(normalize(getItemLabel(item))) !== -1;
        });

        if (!workflowItem) {
            return false;
        }

        lockedValue = workflowItem.value || workflowItem.getAttribute("value");
        workflowItem.selected = true;
        field.value = lockedValue;
        $(field).trigger("change");
        lockWorkflowField(field, lockedValue);

        activationPending = false;
        pendingAction = null;
        stopWaiting();
        return true;
    }

    function waitForWorkflowDialog() {
        if (configurePracticeWorkflow()) {
            return;
        }

        stopWaiting();
        configurationObserver = new window.MutationObserver(function () {
            configurePracticeWorkflow();
        });
        configurationObserver.observe(document.body, {
            childList: true,
            subtree: true
        });
        configurationTimer = window.setTimeout(function () {
            stopWaiting();
            activationPending = false;
            pendingAction = null;
            unlockWorkflowFields(document);
            notifyError("Practice Workflow is not available for the selected asset(s).");
        }, CONFIGURATION_TIMEOUT);
    }

    channel.on("click", ACTION_SELECTOR, function () {
        unlockWorkflowFields(document);
        activationPending = true;
        pendingAction = this;
        waitForWorkflowDialog();
    });

    channel.on("click", ".cq-damadmin-admin-actions-createworkflow", function () {
        if (!$(this).is(ACTION_SELECTOR)) {
            activationPending = false;
            pendingAction = null;
            stopWaiting();
            unlockWorkflowFields(document);
        }
    });

    channel.on("foundation-contentloaded", function () {
        configurePracticeWorkflow();
    });

    channel.on("coral-overlay:close", function (event) {
        unlockWorkflowFields(event.target);
    });
}(Granite.$, Granite.$(document), window));
