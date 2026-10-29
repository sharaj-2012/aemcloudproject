/**
 * Enables the "Edit JSON" Assets toolbar action only when exactly one item
 * whose path ends in .json is selected.
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
            var path = selections[0].getAttribute('data-foundation-collection-item-id') || '';
            return /\.json$/i.test(path);
        }
    });
}(window));
