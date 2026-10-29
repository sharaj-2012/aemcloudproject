/**
 * DAM JSON editor: loads the asset's JSON, validates and previews it while editing,
 * shows a side-by-side compare against the loaded version, and saves through
 * POST <asset>.jsoneditor.json.
 */
(function (window, document) {
    'use strict';

    var MAX_BYTES = 2097152;
    var PREVIEW_DELAY_MS = 300;
    var EDITOR_MODULE_URL = '/etc.clientlibs/aemcloudproject/clientlibs/jsoneditor/resources/vendor/vanilla-jsoneditor-3.13.0/standalone.js';
    var CSRF_URL = '/libs/granite/csrf/token.json';
    var TOO_LARGE = 'File too large to edit';

    var state = {
        baseline: '',
        current: '',
        valid: true,
        saving: false,
        comparing: false,
        left: null,
        preview: null,
        previewTimer: null
    };

    function el(id) {
        return document.getElementById(id);
    }

    function assetPath() {
        return decodeURIComponent(window.location.pathname.split('/jsoneditor.html')[1] || '');
    }

    function setText(id, text) {
        var node = el(id);
        if (node) {
            node.textContent = text || '';
            node.hidden = !text;
        }
    }

    function showError(text) {
        setText('jsoneditor-error', text);
    }

    function showStatus(text) {
        setText('jsoneditor-status', text);
    }

    function setLabel(button, text) {
        if (button && button.label) {
            button.label.textContent = text;
        } else if (button) {
            button.textContent = text;
        }
    }

    function isDirty() {
        return state.current !== state.baseline;
    }

    function updateButtons() {
        var save = el('jsoneditor-save');
        if (save) {
            save.disabled = state.saving || !state.valid || !isDirty();
        }
        var format = el('jsoneditor-format');
        if (format) {
            format.disabled = !state.valid || state.comparing;
        }
    }

    function describeParseError(parseError) {
        if (parseError.line === undefined) {
            return parseError.message;
        }
        return 'Line ' + (parseError.line + 1) + ', column ' + (parseError.column + 1) + ': ' + parseError.message;
    }

    function load() {
        return window.fetch(assetPath(), {cache: 'no-store', credentials: 'same-origin'}).then(function (response) {
            if (!response.ok) {
                throw new Error('Could not load file (HTTP ' + response.status + ')');
            }
            if (Number(response.headers.get('Content-Length')) > MAX_BYTES) {
                throw new Error(TOO_LARGE);
            }
            return response.text();
        }).then(function (text) {
            if (new window.Blob([text]).size > MAX_BYTES) {
                throw new Error(TOO_LARGE);
            }
            return text.charAt(0) === '﻿' ? text.slice(1) : text;
        });
    }

    function schedulePreview() {
        window.clearTimeout(state.previewTimer);
        state.previewTimer = window.setTimeout(function () {
            if (state.valid && state.preview) {
                state.preview.set({text: state.current});
            }
        }, PREVIEW_DELAY_MS);
    }

    function applyErrors(contentErrors) {
        var parseError = contentErrors && contentErrors.parseError;
        state.valid = !parseError;
        showError(parseError ? describeParseError(parseError) : '');
        var stale = el('jsoneditor-stale');
        if (stale) {
            stale.hidden = state.valid;
        }
        if (state.valid) {
            schedulePreview();
        }
        updateButtons();
    }

    function onChange(content, previousContent, status) {
        state.current = content.text !== undefined ? content.text : JSON.stringify(content.json, null, 2);
        showStatus('');
        applyErrors(status && status.contentErrors);
    }

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

    function format() {
        if (state.valid && state.left) {
            var formatted = reindent(state.current);
            state.left.update({text: formatted});
            state.current = formatted;
            applyErrors(state.left.validate());
        }
    }

    function renderCompare() {
        var diffEl = el('jsoneditor-diff');
        var normalize = el('jsoneditor-normalize');
        var before = state.baseline;
        var after = state.current;
        if (normalize && normalize.checked) {
            before = reindent(before);
            after = reindent(after);
        }
        diffEl.textContent = '';
        setText('jsoneditor-compare-message', '');
        if (before === after) {
            setText('jsoneditor-compare-message', 'No differences.');
            return;
        }
        var name = assetPath().substring(assetPath().lastIndexOf('/') + 1);
        var patch = window.Diff.createTwoFilesPatch(name, name, before, after, 'original', 'modified', {
            context: 3,
            timeout: 3000,
            maxEditLength: 20000
        });
        if (patch === undefined) {
            setText('jsoneditor-compare-message', 'Too many differences to display');
            return;
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
    }

    function toggleCompare() {
        state.comparing = !state.comparing;
        el('jsoneditor-panes').hidden = state.comparing;
        el('jsoneditor-compare-view').hidden = !state.comparing;
        setLabel(el('jsoneditor-compare'), state.comparing ? 'Back to editor' : 'Compare');
        if (state.comparing) {
            renderCompare();
        }
        updateButtons();
    }

    function describeSaveFailure(body) {
        var text = body.message || 'Save failed.';
        if (body.line !== undefined) {
            text = 'Line ' + body.line + ', column ' + body.column + ': ' + text;
        }
        return text;
    }

    function readJson(response) {
        return response.json().catch(function () {
            throw new Error('Unexpected response (HTTP ' + response.status + '). Your session may have expired; sign in again in another tab and retry.');
        });
    }

    function save() {
        if (state.saving || !state.valid || !isDirty()) {
            return;
        }
        var submitted = state.current;
        state.saving = true;
        updateButtons();
        showError('');
        showStatus('Saving…');
        window.fetch(CSRF_URL, {credentials: 'same-origin'}).then(function (response) {
            if (!response.ok) {
                throw new Error('Could not get a security token (HTTP ' + response.status + ').');
            }
            return readJson(response);
        }).then(function (csrf) {
            return window.fetch(assetPath() + '.jsoneditor.json', {
                method: 'POST',
                credentials: 'same-origin',
                headers: {'Content-Type': 'application/json', 'CSRF-Token': csrf.token},
                body: submitted
            });
        }).then(readJson).then(function (body) {
            if (body.status === 'saved') {
                state.baseline = submitted;
                showStatus('Saved — version ' + body.versionLabel);
            } else if (body.status === 'unchanged') {
                state.baseline = submitted;
                showStatus('Nothing to save.');
            } else {
                showStatus('');
                showError(describeSaveFailure(body));
            }
        }).catch(function (error) {
            showStatus('');
            showError(error.message);
        }).then(function () {
            state.saving = false;
            updateButtons();
        });
    }

    function close() {
        var path = assetPath();
        window.location.href = '/assets.html' + path.substring(0, path.lastIndexOf('/'));
    }

    function bind(id, handler) {
        var node = el(id);
        if (node) {
            node.addEventListener('click', handler);
        }
    }

    function init() {
        if (!el('jsoneditor-left')) {
            return;
        }
        setText('jsoneditor-path', assetPath());
        bind('jsoneditor-save', save);
        bind('jsoneditor-format', format);
        bind('jsoneditor-compare', toggleCompare);
        bind('jsoneditor-close', close);
        el('jsoneditor-normalize').addEventListener('change', renderCompare);
        window.addEventListener('beforeunload', function (event) {
            if (isDirty()) {
                event.preventDefault();
                event.returnValue = '';
            }
        });
        updateButtons();

        Promise.all([load(), import(EDITOR_MODULE_URL)]).then(function (results) {
            var text = results[0];
            var createJSONEditor = results[1].createJSONEditor;
            state.baseline = text;
            state.current = text;
            state.left = createJSONEditor({
                target: el('jsoneditor-left'),
                props: {
                    content: {text: text},
                    mode: 'text',
                    readOnly: !el('jsoneditor-save'),
                    mainMenuBar: false,
                    navigationBar: false,
                    statusBar: true,
                    onChange: onChange
                }
            });
            var errors = state.left.validate();
            state.preview = createJSONEditor({
                target: el('jsoneditor-right'),
                props: {
                    content: {text: errors && errors.parseError ? '' : text},
                    mode: 'tree',
                    readOnly: true,
                    mainMenuBar: false,
                    navigationBar: false
                }
            });
            applyErrors(errors);
        }).catch(function (error) {
            showError(error.message);
            var save = el('jsoneditor-save');
            if (save) {
                save.disabled = true;
            }
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
}(window, document));
