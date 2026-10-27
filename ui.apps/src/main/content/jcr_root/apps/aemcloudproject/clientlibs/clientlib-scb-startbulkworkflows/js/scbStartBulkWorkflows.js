/*
 * SCB Workflow wizard (aemcloudproject/content/scbstartbulkworkflows).
 *
 * The OOTB wizard script only reads the model's multi-resource support when the model select
 * changes, which enables "Keep workflow package". Our select is preselected, so replay that
 * change once, when the wizard first updates its resource list: at that point the OOTB handler
 * can run, and the list-based state it computes afterwards uses the right model setting.
 *
 * When opened with ?showPreview=true (Request for Activation), it shows the Preview URLs of the
 * selected pages (SCB.PreviewUrls, clientlib-scb-previewurls) once the workflow has started, and
 * only then lets the OOTB wizard redirect back to the console, as Manage Publication does.
 */
(function ($, document, window) {
    "use strict";

    var FORM_SELECTOR = ".cq-sites-startbulkworkflows-form";
    var SHOW_PREVIEW_PARAM = "showPreview";
    var SUCCESS_RESPONSE_UI = "foundation.form.response.ui.success";
    var REDIRECT_RESPONSE_NAME = "foundation.prompt.redirect";

    $(document).one("foundation-selections-change", ".cq-common-admin-sourcepages", function () {
        var select = document.querySelector(FORM_SELECTOR + " coral-select[name='workflowModel']");

        if (select && select.selectedItem) {
            select.trigger("change");
        }
    });

    if (new URLSearchParams(window.location.search).get(SHOW_PREVIEW_PARAM) !== "true") {
        return;
    }

    // A single page (or a model without multi-resource support): the OOTB script posts the workflow
    // itself and redirects in its success callback, so run that callback once the dialog is closed.
    $.ajaxPrefilter(function (options, originalOptions) {
        var data = originalOptions.data;
        var success = options.success;

        if (!/^post$/i.test(options.type) || !/\/workflow\/instances$/.test(options.url) ||
                !data || !Array.isArray(data.payload) || typeof success !== "function") {
            return;
        }

        options.success = function () {
            var context = this;
            var args = arguments;

            window.SCB.PreviewUrls.show(data.payload).then(function () {
                success.apply(context, args);
            });
        };
    });

    // Several pages: the form is submitted as a workflow package and the OOTB
    // foundation.prompt.redirect response handler redirects. Registered once the page has loaded,
    // after the OOTB handler, so it runs first; it hands over to the OOTB handler once the dialog is
    // closed.
    $(function () {
        var registry = $(window).adaptTo("foundation-registry");
        var ootbHandler = registry.get(SUCCESS_RESPONSE_UI).filter(function (entry) {
            return entry.name === REDIRECT_RESPONSE_NAME;
        }).pop();

        if (!ootbHandler) {
            return;
        }

        registry.register(SUCCESS_RESPONSE_UI, {
            name: REDIRECT_RESPONSE_NAME,
            handler: function (form) {
                var context = this;
                var args = arguments;

                if (!$(form).is(FORM_SELECTOR)) {
                    // Returning false lets the next handler with this name, the OOTB one, run.
                    return false;
                }

                var paths = $(form).find("input[type=hidden][name=srcPathList]").map(function () {
                    return this.value;
                }).get().filter(Boolean);

                window.SCB.PreviewUrls.show(paths).then(function () {
                    ootbHandler.handler.apply(context, args);
                });
            }
        });
    });
}(Granite.$, document, window));
