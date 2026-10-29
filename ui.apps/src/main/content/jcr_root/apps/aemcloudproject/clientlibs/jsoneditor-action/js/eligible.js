/**
 * Enables the "Edit JSON" Assets toolbar action only when exactly one asset
 * (not a folder) is selected whose MIME type is application/json or whose
 * path ends in .json.
 */
(function (window) {
    'use strict';

    var $ = window.Granite && window.Granite.$ ? window.Granite.$ : window.jQuery;

    $(window).adaptTo('foundation-registry').register('foundation.collection.action.activecondition', {
        name: 'aemcloudproject.jsoneditor.eligible',
        handler: function (name, el, config, collection, selections) {
            if (!selections || selections.length !== 1) {
                return false;
            }
            var item = selections[0];
            var meta = item.querySelector('.foundation-collection-assets-meta');
            if (meta && meta.getAttribute('data-foundation-collection-meta-type') !== 'asset') {
                return false;
            }
            var mimeType = meta ? meta.getAttribute('data-asset-mimetype') : '';
            var path = item.getAttribute('data-foundation-collection-item-id') || '';
            return mimeType === 'application/json' || /\.json$/i.test(path);
        }
    });
}(window));
