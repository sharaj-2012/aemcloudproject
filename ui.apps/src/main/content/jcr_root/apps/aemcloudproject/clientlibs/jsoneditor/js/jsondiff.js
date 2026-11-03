/**
 * Shared JSON diff helpers for the JSON editor's Compare view and the JSON changes page.
 * Exposes window.AemCloudProjectJsonDiff.
 */
(function (window) {
    'use strict';

    /**
     * Pretty-prints JSON text with two-space indentation without parsing it,
     * so number and string tokens are kept exactly as written.
     */
    function reindent(text) {
        var out = '';
        var indent = 0;
        var inString = false;
        var escaped = false;
        var i;
        var c;
        var j;

        function newline() {
            out += '\n' + new Array(indent + 1).join('  ');
        }

        for (i = 0; i < text.length; i++) {
            c = text.charAt(i);
            if (inString) {
                out += c;
                if (escaped) {
                    escaped = false;
                } else if (c === '\\') {
                    escaped = true;
                } else if (c === '"') {
                    inString = false;
                }
            } else if (c === '"') {
                inString = true;
                out += c;
            } else if (/\s/.test(c)) {
                continue;
            } else if (c === '{' || c === '[') {
                j = i + 1;
                while (j < text.length && /\s/.test(text.charAt(j))) {
                    j++;
                }
                if (text.charAt(j) === (c === '{' ? '}' : ']')) {
                    out += c + text.charAt(j);
                    i = j;
                } else {
                    out += c;
                    indent++;
                    newline();
                }
            } else if (c === '}' || c === ']') {
                indent = Math.max(0, indent - 1);
                newline();
                out += c;
            } else if (c === ',') {
                out += ',';
                newline();
            } else if (c === ':') {
                out += ': ';
            } else {
                out += c;
            }
        }
        return out;
    }

    /**
     * Renders a side-by-side diff of two texts into diffEl.
     *
     * @return a message to show instead of a diff ("No differences.", "Too many differences to display"), or ''
     */
    function render(diffEl, before, after, fileName, normalize) {
        var left = normalize ? reindent(before) : before;
        var right = normalize ? reindent(after) : after;
        diffEl.textContent = '';
        if (left === right) {
            return 'No differences.';
        }
        var patch = window.Diff.createTwoFilesPatch(fileName, fileName, left, right, '', '', {
            context: 3,
            timeout: 3000,
            maxEditLength: 20000
        });
        if (patch === undefined) {
            return 'Too many differences to display';
        }
        new window.Diff2HtmlUI(diffEl, patch, {
            outputFormat: 'side-by-side',
            drawFileList: false,
            fileContentToggle: false,
            matching: 'words',
            diffMaxChanges: 5000,
            diffMaxLineLength: 10000,
            matchingMaxComparisons: 2500,
            highlight: false
        }).draw();
        return '';
    }

    window.AemCloudProjectJsonDiff = {reindent: reindent, render: render};
}(window));
