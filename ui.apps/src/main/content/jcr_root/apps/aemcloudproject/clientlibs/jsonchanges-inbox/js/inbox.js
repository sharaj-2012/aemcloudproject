/**
 * Inbox "View JSON changes" action: enabled only when the selected item's payload is a JSON asset,
 * and opens the JSON changes page for that asset in a new tab.
 */
(function (window) {
    'use strict';

    var $ = window.Granite && window.Granite.$ ? window.Granite.$ : window.jQuery;
    var registry = $(window).adaptTo('foundation-registry');
    var CHANGES_PAGE = '/mnt/overlay/aemcloudproject/gui/content/jsonchanges.html';
    var ASSET_PAYLOAD = /^\/assetdetails\.html(\/content\/dam\/.+\.json)$/i;

    /**
     * @return the DAM path of the item's payload when it is a .json asset, otherwise null
     */
    function jsonPayloadPath(item) {
        var match = ASSET_PAYLOAD.exec(item.getAttribute('data-payload-link') || '');
        return match ? decodeURI(match[1]) : null;
    }

    registry.register('foundation.collection.action.activecondition', {
        name: 'aemcloudproject.inbox.jsonpayload',
        handler: function (name, el, config, collection, selections) {
            return !!selections && selections.length === 1 && jsonPayloadPath(selections[0]) !== null;
        }
    });

    registry.register('foundation.collection.action.action', {
        name: 'aemcloudproject.inbox.viewjsonchanges',
        handler: function (name, el, config, collection, selections) {
            var path = selections && selections.length === 1 ? jsonPayloadPath(selections[0]) : null;
            if (path) {
                window.open(CHANGES_PAGE + path.split('/').map(encodeURIComponent).join('/'), '_blank', 'noopener');
            }
        }
    });
}(window));
