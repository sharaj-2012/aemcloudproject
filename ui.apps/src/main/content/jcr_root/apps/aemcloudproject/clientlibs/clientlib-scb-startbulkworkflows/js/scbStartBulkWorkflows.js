/*
 * SCB Workflow wizard (aemcloudproject/content/scbstartbulkworkflows).
 *
 * The OOTB wizard script only reads the model's multi-resource support when the model select
 * changes, which enables "Keep workflow package". Our select is preselected, so replay that
 * change once, when the wizard first updates its resource list: at that point the OOTB handler
 * can run, and the list-based state it computes afterwards uses the right model setting.
 */
(function ($, document) {
    "use strict";

    $(document).one("foundation-selections-change", ".cq-common-admin-sourcepages", function () {
        var select = document.querySelector(".cq-sites-startbulkworkflows-form coral-select[name='workflowModel']");

        if (select && select.selectedItem) {
            select.trigger("change");
        }
    });
}(Granite.$, document));
