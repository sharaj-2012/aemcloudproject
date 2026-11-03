/**
 * JSON changes page: loads GET <asset>.jsonchanges.json and shows a side-by-side diff
 * of the asset's most recent version against its current content.
 */
(function (window, document) {
    'use strict';

    var data = null;

    function el(id) {
        return document.getElementById(id);
    }

    function setText(id, text) {
        var node = el(id);
        if (node) {
            node.textContent = text || '';
            node.hidden = !text;
        }
    }

    function assetPath() {
        return decodeURIComponent(window.location.pathname.split('/jsonchanges.html')[1] || '');
    }

    /**
     * Only same-origin DAM paths are accepted, so the suffix can never point the request at another host.
     */
    function isValidAssetPath(path) {
        return /^\/content\/dam\/[^/]/.test(path) && path.indexOf('//') === -1 && !/(^|\/)\.\.?(\/|$)/.test(path);
    }

    function assetUrl(suffix) {
        return assetPath().split('/').map(encodeURIComponent).join('/') + (suffix || '');
    }

    function formatDate(millis) {
        return millis ? new Date(millis).toLocaleString() : 'an unknown time';
    }

    function lf(text) {
        return (text || '').replace(/\r\n?/g, '\n');
    }

    function render() {
        var name = assetPath().substring(assetPath().lastIndexOf('/') + 1);
        setText('jsonchanges-message', window.AemCloudProjectJsonDiff.render(
            el('jsonchanges-diff'), lf(data.previous.content), lf(data.current.content), name, el('jsonchanges-normalize').checked));
    }

    function show(body) {
        var current = body.current || {};
        var previous = body.previous;
        if (!previous) {
            setText('jsonchanges-summary', 'Last changed by ' + (current.user || 'an unknown user') + ' on ' + formatDate(current.date) + '.');
            setText('jsonchanges-error', 'No earlier version to compare with.');
            return;
        }
        data = body;
        setText('jsonchanges-summary', 'Last changed by ' + (current.user || 'an unknown user') + ' on ' + formatDate(current.date)
            + '. Left: version ' + (previous.label || '(unlabelled)') + ' saved ' + formatDate(previous.date) + '. Right: current content.');
        el('jsonchanges-view').hidden = false;
        render();
    }

    function init() {
        if (!el('jsonchanges-diff')) {
            return;
        }
        var path = assetPath();
        setText('jsonchanges-path', path);
        el('jsonchanges-close').addEventListener('click', function () {
            window.close();
        });
        if (!isValidAssetPath(path)) {
            setText('jsonchanges-error', 'Not a DAM asset path.');
            return;
        }
        el('jsonchanges-normalize').addEventListener('change', function () {
            if (data) {
                render();
            }
        });
        window.fetch(assetUrl('.jsonchanges.json'), {cache: 'no-store', credentials: 'same-origin'}).then(function (response) {
            return response.json().catch(function () {
                throw new Error('Unexpected response (HTTP ' + response.status + '). Your session may have expired; sign in again and reload.');
            }).then(function (body) {
                if (body.status === 'not_json') {
                    throw new Error('This asset is not a JSON file.');
                }
                if (!response.ok) {
                    throw new Error('Could not load the changes (HTTP ' + response.status + ').');
                }
                return body;
            });
        }).then(show).catch(function (error) {
            setText('jsonchanges-error', error.message);
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
}(window, document));
