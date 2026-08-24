(function ($, channel, window, document) {
    "use strict";

    var ACTION_SELECTOR = ".aemcloudproject-sites-activate-workflow";
    var NATIVE_ACTION_SELECTOR =
        ".cq-siteadmin-admin-actions-createworkflow, " +
        "[rel~='cq-siteadmin-admin-actions-createworkflow']";
    var LOCKED_CLASS = "aemcloudproject-sites-activate-workflow-locked";
    var PENDING_KEY = "aemcloudproject.sites.activateWorkflow.pending";
    var CONFIGURATION_TIMEOUT = 20000;
    var PENDING_MAX_AGE = 60000;
    var activationPending = false;
    var workflowNames = [];
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

    function isVisible(element) {
        return $(element).is(":visible");
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

    function setStoredPending(names) {
        try {
            window.sessionStorage.setItem(PENDING_KEY, JSON.stringify({
                created: Date.now(),
                workflowNames: names
            }));
        } catch (error) {
            window.console.warn("Unable to persist Activate Workflow state.", error);
        }
    }

    function readStoredPending() {
        var value;

        try {
            value = JSON.parse(window.sessionStorage.getItem(PENDING_KEY));
        } catch (error) {
            return null;
        }

        if (!value || !value.created ||
                Date.now() - value.created > PENDING_MAX_AGE) {
            clearStoredPending();
            return null;
        }

        return value;
    }

    function clearStoredPending() {
        try {
            window.sessionStorage.removeItem(PENDING_KEY);
        } catch (error) {
            window.console.warn("Unable to clear Activate Workflow state.", error);
        }
    }

    function restorePendingState() {
        var stored = readStoredPending();

        if (!stored) {
            return false;
        }

        activationPending = true;
        workflowNames = stored.workflowNames || [];
        return true;
    }

    function clearPendingState() {
        activationPending = false;
        workflowNames = [];
        clearStoredPending();
        stopWaiting();
    }

    function unlockWorkflowFields(root) {
        $(root || document).find("." + LOCKED_CLASS).each(function () {
            this.classList.remove(LOCKED_CLASS);
            this.removeAttribute("aria-disabled");
            this.removeAttribute("tabindex");
            this.removeAttribute("data-aemcloudproject-locked-value");
            $(this).off("change.aemcloudprojectSitesActivateWorkflow");
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

    function findNativeWorkflowAction(customAction) {
        var container = $(customAction).closest(
            "coral-popover, .coral-Popover, coral-anchorlist, .coral-AnchorList"
        );
        var nativeAction;

        if (!container.length) {
            container = $(document);
        }

        nativeAction = container.find(NATIVE_ACTION_SELECTOR).filter(function () {
            return !$(this).is(ACTION_SELECTOR);
        }).get(0);

        if (nativeAction) {
            return nativeAction;
        }

        return container.find("coral-anchorlist-item, [role='menuitem'], a, button")
            .filter(function () {
                return !$(this).is(ACTION_SELECTOR) &&
                    $(this).text().trim() === "Workflow";
            }).get(0);
    }

    function findWorkflowField() {
        var fields = $("coral-select, select").filter(function () {
            return isVisible(this) && getWorkflowItems(this).some(function (item) {
                return workflowNames.indexOf(normalize(getItemLabel(item))) !== -1;
            });
        });

        return fields.last().get(0);
    }

    function lockWorkflowField(field, value) {
        field.classList.add(LOCKED_CLASS);
        field.setAttribute("aria-disabled", "true");
        field.setAttribute("tabindex", "-1");
        field.setAttribute("data-aemcloudproject-locked-value", value);

        $(field)
            .off("change.aemcloudprojectSitesActivateWorkflow")
            .on("change.aemcloudprojectSitesActivateWorkflow", function () {
                var lockedValue = this.getAttribute("data-aemcloudproject-locked-value");

                if (lockedValue && this.value !== lockedValue) {
                    this.value = lockedValue;
                }
            });
    }

    function configurePracticeWorkflow() {
        var field;
        var workflowItem;
        var lockedValue;

        if (!activationPending && !restorePendingState()) {
            return false;
        }

        field = findWorkflowField();
        if (!field) {
            return false;
        }

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
        workflowNames = [];
        clearStoredPending();
        stopWaiting();
        return true;
    }

    function waitForWorkflowWizard() {
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
            unlockWorkflowFields(document);
            clearPendingState();
            notifyError("Practice Workflow is not available for the selected page(s).");
        }, CONFIGURATION_TIMEOUT);
    }

    function activateWorkflow(customAction) {
        var nativeAction = findNativeWorkflowAction(customAction);

        if (!nativeAction) {
            notifyError("The standard Sites Workflow action is not available.");
            return;
        }

        unlockWorkflowFields(document);
        activationPending = true;
        workflowNames = getConfiguredWorkflowNames(customAction);
        setStoredPending(workflowNames);
        waitForWorkflowWizard();
        nativeAction.click();
    }

    document.addEventListener("click", function (event) {
        var customAction = $(event.target).closest(ACTION_SELECTOR).get(0);

        if (!customAction) {
            return;
        }

        event.preventDefault();
        event.stopImmediatePropagation();
        window.setTimeout(function () {
            activateWorkflow(customAction);
        }, 0);
    }, true);

    channel.on("click", NATIVE_ACTION_SELECTOR, function () {
        if (!activationPending) {
            clearPendingState();
            unlockWorkflowFields(document);
        }
    });

    channel.on("foundation-contentloaded", function () {
        if (restorePendingState()) {
            waitForWorkflowWizard();
        }
    });

    channel.on("coral-overlay:close", function (event) {
        unlockWorkflowFields(event.target);
    });

    $(function () {
        if (restorePendingState()) {
            waitForWorkflowWizard();
        }
    });
}(Granite.$, Granite.$(document), window, document));
