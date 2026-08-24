(function ($, ns, channel, window) {
    "use strict";

    var ACTION_SELECTOR = ".aemcloudproject-pageinfo-activate-workflow";
    var START_WORKFLOW_ACTION_SELECTOR = ".cq-authoring-pageinfo-startworkflow";
    var LOCKED_CLASS = "aemcloudproject-activate-workflow-locked";
    var WORKFLOW_FIELD_SELECTOR = "coral-select[name='model'], select[name='model']";
    var CONFIGURATION_TIMEOUT = 10000;
    var activationPending = false;
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

    function setSelectedWorkflow(field, item) {
        var value = item.value || item.getAttribute("value");

        item.selected = true;
        field.value = value;
        $(field).trigger("change");
        return value;
    }

    function lockWorkflowField(field, value) {
        field.classList.add(LOCKED_CLASS);
        field.setAttribute("aria-disabled", "true");
        field.setAttribute("tabindex", "-1");
        field.setAttribute("data-aemcloudproject-locked-value", value);

        $(field)
            .off("change.aemcloudprojectActivateWorkflow")
            .on("change.aemcloudprojectActivateWorkflow", function () {
                var lockedValue = this.getAttribute("data-aemcloudproject-locked-value");

                if (lockedValue && this.value !== lockedValue) {
                    this.value = lockedValue;
                }
            });
    }

    function unlockWorkflowFields(root) {
        $(root || document).find("." + LOCKED_CLASS).each(function () {
            this.classList.remove(LOCKED_CLASS);
            this.removeAttribute("aria-disabled");
            this.removeAttribute("tabindex");
            this.removeAttribute("data-aemcloudproject-locked-value");
            $(this).off("change.aemcloudprojectActivateWorkflow");
        });
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

    function notifyError(message) {
        if (ns.ui && ns.ui.helpers && typeof ns.ui.helpers.notify === "function") {
            ns.ui.helpers.notify(
                Granite.I18n.get("Activate Workflow"),
                Granite.I18n.get(message),
                "error"
            );
        } else {
            window.console.error(message);
        }
    }

    function findWorkflowField() {
        var fields = Array.prototype.slice.call(
            document.querySelectorAll(WORKFLOW_FIELD_SELECTOR)
        );
        var visibleFields = fields.filter(function (field) {
            return $(field).is(":visible");
        });

        return visibleFields[visibleFields.length - 1] || fields[fields.length - 1];
    }

    function configurePracticeWorkflow(action) {
        var field = findWorkflowField();
        var workflowNames;
        var workflowItem;
        var lockedValue;

        if (!activationPending || !field) {
            return false;
        }

        workflowNames = getConfiguredWorkflowNames(action);
        workflowItem = getWorkflowItems(field).find(function (item) {
            return workflowNames.indexOf(normalize(getItemLabel(item))) !== -1;
        });

        if (!workflowItem) {
            return false;
        }

        lockedValue = setSelectedWorkflow(field, workflowItem);
        lockWorkflowField(field, lockedValue);
        activationPending = false;
        stopWaiting();
        return true;
    }

    function waitForWorkflowDialog(action) {
        if (configurePracticeWorkflow(action)) {
            return;
        }

        stopWaiting();
        configurationObserver = new window.MutationObserver(function () {
            configurePracticeWorkflow(action);
        });
        configurationObserver.observe(document.body, {
            childList: true,
            subtree: true
        });
        configurationTimer = window.setTimeout(function () {
            stopWaiting();
            activationPending = false;
            unlockWorkflowFields(document);
            notifyError("Practice Workflow is not available for this page.");
        }, CONFIGURATION_TIMEOUT);
    }

    function isStartWorkflowAction(element) {
        var action = $(element).closest("button, a");

        return action.is(START_WORKFLOW_ACTION_SELECTOR) ||
            action.text().trim() === "Start Workflow";
    }

    function findStartWorkflowAction(action) {
        var popover = $(action).closest("coral-popover, .coral-Popover");
        var actions = popover.find(START_WORKFLOW_ACTION_SELECTOR + ", button, a").filter(function () {
            return isStartWorkflowAction(this);
        });

        return actions.get(0);
    }

    channel.on("click", ACTION_SELECTOR, function (event) {
        var action = this;
        var startWorkflowAction = findStartWorkflowAction(action);

        event.preventDefault();
        event.stopPropagation();
        unlockWorkflowFields(document);

        if (!startWorkflowAction) {
            notifyError("The AEM Start Workflow action is not available.");
            return;
        }

        activationPending = true;
        startWorkflowAction.click();
        waitForWorkflowDialog(action);
    });

    channel.on("click", "button, a", function () {
        if (!activationPending && isStartWorkflowAction(this)) {
            unlockWorkflowFields(document);
        }
    });

    channel.on("coral-overlay:close", function (event) {
        unlockWorkflowFields(event.target);
    });
}(Granite.$, Granite.author, Granite.$(document), window));
