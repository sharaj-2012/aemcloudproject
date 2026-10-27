/*
 * SCB Workflows: Preview URL dialog shown once Request for Activation has started, from any of its
 * entry points. Exposes SCB.PreviewUrls.show(paths), which returns a promise resolved when the author
 * clicks Done, so an entry point can continue with its own flow (e.g. a redirect) afterwards.
 *
 * Mirrors the Manage Publication Preview dialog (/libs/cq/gui/components/common/admin/managepublication):
 * the URLs are the Preview base URL (previewUrl.json) followed by each item's path on Preview
 * (contentPreviewUrl.json). Both endpoints are internal to AEM and undocumented, so an AEM release can
 * change them. Like Manage Publication, it does not check whether the content has been published to
 * Preview.
 */
(function ($, window, document) {
    "use strict";

    var PREVIEW_URL_URL = "/mnt/overlay/wcm/core/content/previewUrl.json";
    var CONTENT_PREVIEW_URL_URL = "/mnt/overlay/wcm/core/content/contentPreviewUrl.json";

    function toast(message, variant) {
        new Coral.Toast().set({
            content: { textContent: message },
            variant: variant || "default"
        }).show();
    }

    function getPreviewUrls(paths) {
        return $.when(
            $.ajax({
                url: Granite.HTTP.externalize(PREVIEW_URL_URL),
                dataType: "json",
                cache: false
            }),
            // Granite's jQuery prefilter adds the CSRF token. The servlet reads every parameter value
            // as a path.
            $.ajax({
                url: Granite.HTTP.externalize(CONTENT_PREVIEW_URL_URL),
                type: "POST",
                dataType: "json",
                traditional: true,
                data: { paths: paths }
            })
        ).then(function (previewUrlResult, contentPreviewUrlResult) {
            var previewUrl = previewUrlResult[0].previewUrl;
            var contentPaths = contentPreviewUrlResult[0];

            if (!previewUrl) {
                return [];
            }
            // As in Manage Publication: the base URL ends with "/", each content path starts with one,
            // and items without a path on Preview (e.g. folders) are left out.
            return paths.map(function (path) {
                return contentPaths[path];
            }).filter(Boolean).map(function (contentPath) {
                return previewUrl.slice(0, -1) + contentPath;
            });
        });
    }

    function copyText(text) {
        return Promise.resolve().then(function () {
            return navigator.clipboard.writeText(text);
        }).catch(function () {
            // The clipboard API can be denied, e.g. when AEM runs in a frame of another site. Fall back
            // to the copy command, as Manage Publication does.
            var textarea = document.createElement("textarea");
            textarea.value = text;
            textarea.style.position = "fixed";
            document.body.appendChild(textarea);
            textarea.select();
            try {
                if (!document.execCommand("copy")) {
                    throw new Error("Copy command not supported");
                }
            } finally {
                document.body.removeChild(textarea);
            }
        });
    }

    function copyAndNotify(text, message) {
        copyText(text).then(function () {
            toast(message);
        }, function () {
            toast(Granite.I18n.get("The browser blocked copying."), "error");
        });
    }

    function createRow(url) {
        var row = document.createElement("div");
        var link = document.createElement("a");
        var copyButton = new Coral.Button().set({
            icon: "copy",
            iconSize: "S",
            variant: "quietaction",
            title: Granite.I18n.get("Copy")
        });

        row.style.display = "flex";
        row.style.alignItems = "center";
        row.style.justifyContent = "space-between";
        link.href = url;
        link.textContent = url;
        link.target = "_blank";
        link.rel = "noopener noreferrer";
        link.className = "coral-Link";
        link.style.wordBreak = "break-all";
        copyButton.addEventListener("click", function () {
            copyAndNotify(url, Granite.I18n.get("Link copied!"));
        });
        row.appendChild(link);
        row.appendChild(copyButton);
        return row;
    }

    function openDialog(urls, warning, done) {
        var dialog = new Coral.Dialog().set({
            backdrop: Coral.Dialog.backdrop.STATIC,
            interaction: "off",
            variant: warning ? "warning" : "default"
        });
        var doneButton = new Coral.Button().set({
            variant: Coral.Button.variant.PRIMARY,
            label: { textContent: Granite.I18n.get("Done") }
        });

        dialog.header.textContent = warning ? Granite.I18n.get("Warning") :
            Granite.I18n.get(urls.length > 1 ? "Preview URLs" : "Preview URL");

        if (warning) {
            dialog.content.textContent = warning;
        } else {
            urls.forEach(function (url) {
                dialog.content.appendChild(createRow(url));
            });

            var copyLinksButton = new Coral.Button().set({
                label: { textContent: Granite.I18n.get("Copy Links") }
            });
            copyLinksButton.addEventListener("click", function () {
                copyAndNotify(urls.join("\n"), Granite.I18n.get("Links copied!"));
            });
            dialog.footer.appendChild(copyLinksButton);
        }

        doneButton.addEventListener("click", function () {
            dialog.hide();
        });
        dialog.footer.appendChild(doneButton);

        dialog.addEventListener("coral-overlay:close", function () {
            dialog.remove();
            done.resolve();
        });

        document.body.appendChild(dialog);
        dialog.show();
    }

    function show(paths) {
        var done = $.Deferred();

        getPreviewUrls(paths).then(function (urls) {
            openDialog(urls, urls.length ? null :
                Granite.I18n.get("Unable to generate the Preview URL. Check that the Preview service is configured."), done);
        }, function () {
            openDialog([], Granite.I18n.get("The Preview URL could not be loaded."), done);
        });

        return done.promise();
    }

    window.SCB = window.SCB || {};
    window.SCB.PreviewUrls = {
        show: show
    };
}(Granite.$, window, document));
