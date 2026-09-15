(function ($, window, document) {
    "use strict";

    var LOCKED_CLASS = "aemcloudproject-activate-workflow-locked";
    var SELECT_SELECTOR = "coral-select, select";
    var MODEL_SELECTOR = "coral-select[name='model'], select[name='model']";
    var EVENT_NAMESPACE = ".aemcloudprojectWorkflow";

    function normalize(value) {
        return (value || "").toLowerCase().replace(/[^a-z0-9]/g, "");
    }

    function readConfig(action) {
        return {
            model: action.getAttribute("data-workflow-model"),
            names: [
                action.getAttribute("data-workflow-model-title"),
                action.getAttribute("data-workflow-model-title-alias")
            ].filter(Boolean).map(normalize)
        };
    }

    function notifyError(message) {
        var ui = $(window).adaptTo("foundation-ui");
        if (ui && typeof ui.notify === "function") {
            ui.notify(Granite.I18n.get("Activate Workflow"), Granite.I18n.get(message), "error");
        } else {
            window.console.error(message);
        }
    }

    function findNativeAction(container, selector, label) {
        // Retain the scoped label fallback for AEM versions with different native action classes.
        return container.find(selector).get(0) ||
            container.find("button, a, coral-anchorlist-item, [role='menuitem']").filter(function () {
                return $(this).text().trim() === Granite.I18n.get(label);
            }).get(0);
    }

    function matchingItem(field, config) {
        var items = field.items && typeof field.items.getAll === "function" ?
            field.items.getAll() : Array.prototype.slice.call(field.querySelectorAll("coral-select-item, option"));
        var matches = items.filter(function (item) {
            // Prefer an exact model value when configured; keep existing title/alias configuration working.
            return config.model ? (item.value || item.getAttribute("value")) === config.model :
                config.names.indexOf(normalize(item.textContent)) !== -1;
        });
        return matches.length === 1 ? matches[0] : null;
    }

    function create(options) {
        var pending;
        var observer;
        var timer;
        var container;
        var lockedField;
        var originalAttributes;
        var channel = $(document);

        function stopWaiting() {
            if (observer) {
                observer.disconnect();
                observer = null;
            }
            window.clearTimeout(timer);
            timer = null;
            pending = null;
            if (options.onComplete) {
                options.onComplete();
            }
        }

        function unlock() {
            if (!lockedField) {
                return;
            }
            $(lockedField).removeClass(LOCKED_CLASS).off(EVENT_NAMESPACE);
            Object.keys(originalAttributes).forEach(function (name) {
                if (originalAttributes[name] === null) {
                    lockedField.removeAttribute(name);
                } else {
                    lockedField.setAttribute(name, originalAttributes[name]);
                }
            });
            lockedField = null;
        }

        function cancel() {
            stopWaiting();
            unlock();
            container = null;
        }

        function lock(field, value) {
            lockedField = field;
            originalAttributes = {
                "aria-disabled": field.getAttribute("aria-disabled"),
                "tabindex": field.getAttribute("tabindex")
            };
            // Keep the field enabled so AEM still submits its value.
            $(field).addClass(LOCKED_CLASS).attr({ "aria-disabled": "true", "tabindex": "-1" })
                .on("change" + EVENT_NAMESPACE, function () {
                    if (field.value !== value) {
                        field.value = value;
                    }
                }).on("keydown" + EVENT_NAMESPACE + " click" + EVENT_NAMESPACE, function (event) {
                    // Also block interaction with Coral's internal focusable control.
                    if (event.type !== "keydown" || event.key !== "Tab") {
                        event.preventDefault();
                        event.stopImmediatePropagation();
                    }
                });
        }

        function configure() {
            if (!pending) {
                return;
            }
            if (container && !document.documentElement.contains(container)) {
                cancel();
                return;
            }
            // Only inspect the active dialog/wizard, never hidden fields elsewhere in the document.
            var activeContainer = $(options.containerSelector).filter(":visible").last().get(0);
            if (!activeContainer) {
                return;
            }
            if (container !== activeContainer) {
                container = activeContainer;
                observer.disconnect();
                observer.observe(container, { childList: true, subtree: true, attributes: true });
            }
            var fields = $(container).find(MODEL_SELECTOR).filter(":visible");
            if (!fields.length) {
                // Some workflow wizards use a different field name.
                fields = $(container).find(SELECT_SELECTOR).filter(":visible");
            }
            var candidates = [];
            fields.each(function () {
                if ($(this).parents("coral-select").length) {
                    return;
                }
                var item = matchingItem(this, pending);
                if (item) {
                    candidates.push({ field: this, item: item });
                }
            });
            if (candidates.length !== 1) {
                return;
            }
            var field = candidates[0].field;
            var item = candidates[0].item;
            var value = item.value || item.getAttribute("value");
            stopWaiting();
            item.selected = true;
            field.value = value;
            $(field).trigger("change");
            lock(field, value);
        }

        function start(config) {
            cancel();
            pending = config;
            observer = new window.MutationObserver(configure);
            // Observe the document only until the dialog/wizard is available.
            observer.observe(document.body, { childList: true, subtree: true, attributes: true });
            timer = window.setTimeout(function () {
                cancel();
                notifyError(options.errorMessage);
            }, options.timeout || 10000);
            // Let the native click handler open its UI before looking for fields.
            window.setTimeout(configure, 0);
        }

        channel.on("foundation-contentloaded" + EVENT_NAMESPACE + " coral-overlay:open" + EVENT_NAMESPACE, configure);
        channel.on("coral-overlay:close" + EVENT_NAMESPACE, function (event) {
            // Closing a dropdown or the Create/Page Information popover is not dialog cancellation.
            if (event.target === container || $(event.target).is("coral-dialog, .coral-Dialog")) {
                cancel();
            }
        });
        window.addEventListener("pagehide", function () {
            // Preserve persisted Sites state during navigation to its workflow wizard.
            if (observer) {
                observer.disconnect();
            }
            window.clearTimeout(timer);
        });
        window.addEventListener("pageshow", function (event) {
            if (event.persisted) {
                cancel();
            }
        });

        return { start: start, cancel: cancel };
    }

    window.AemCloudProjectWorkflow = {
        create: create,
        readConfig: readConfig,
        notifyError: notifyError,
        findNativeAction: findNativeAction
    };
}(Granite.$, window, document));
