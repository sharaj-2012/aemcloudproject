/*
 * Goal Based Calculator
 *  - initCalculator: one per [data-gbic-form] (one calculator per tab). Each
 *    calculator renders its own form and result card, so everything it needs is
 *    looked up inside its own root.
 *  - Tab switching is left entirely to core tabs.js: it shows/hides whole tab
 *    panels, which switches a calculator's form and result card together.
 */
(function () {
    'use strict';

    var groupFormat = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 });
    var MAX_MONTHS = 1200;

    function toNumber(value) {
        var n = parseFloat(String(value).replace(/[^0-9.]/g, ''));
        return isNaN(n) ? NaN : n;
    }

    function futureValue(initial, monthly, months, monthlyRate) {
        if (monthlyRate === 0) {
            return initial + monthly * months;
        }
        var growth = Math.pow(1 + monthlyRate, months);
        // SIP paid at the start of each month (annuity due), same as the live calculator.
        return initial * growth + monthly * ((growth - 1) / monthlyRate) * (1 + monthlyRate);
    }

    function initCalculator(form) {
        if (form.getAttribute('data-gbic-ready')) {
            return;
        }
        form.setAttribute('data-gbic-ready', 'true');

        var id = form.getAttribute('data-gbic-form');
        var result = form.querySelector('[data-gbic-result="' + id + '"]');
        var fields = {};

        form.querySelectorAll('[data-field]').forEach(function (input) {
            var key = input.getAttribute('data-field');
            fields[key] = {
                input: input,
                range: form.querySelector('[data-range="' + key + '"]'),
                error: form.querySelector('[data-error-for="' + key + '"]'),
                box: form.querySelector('[data-field-wrap="' + key + '"]'),
                min: toNumber(input.getAttribute('data-min')),
                max: toNumber(input.getAttribute('data-max'))
            };
            bindField(fields[key]);
        });

        form.querySelectorAll('input[data-plan]').forEach(function (radio) {
            radio.addEventListener('change', function () {
                applyPlan();
                calculate();
            });
        });

        function valueOf(key) {
            return toNumber(fields[key].input.value);
        }

        function paintRange(field) {
            var range = field.range;
            if (!range) {
                return;
            }
            var pct = (range.value - range.min) / ((range.max - range.min) || 1) * 100;
            range.style.setProperty('--fill', pct + '%');
        }

        function validate(field) {
            var n = toNumber(field.input.value);
            var valid = !isNaN(n) && n >= field.min && n <= field.max;
            if (field.error) {
                field.error.hidden = valid;
            }
            if (field.box) {
                field.box.classList.toggle('is-invalid', !valid);
            }
            return valid;
        }

        function bindField(field) {
            field.input.value = groupFormat.format(toNumber(field.input.value) || 0);
            paintRange(field);

            field.input.addEventListener('input', function () {
                var n = toNumber(field.input.value);
                if (field.range && !isNaN(n)) {
                    field.range.value = n;
                    paintRange(field);
                }
                if (validate(field)) {
                    calculate();
                }
            });
            field.input.addEventListener('blur', function () {
                var n = toNumber(field.input.value);
                if (!isNaN(n)) {
                    field.input.value = groupFormat.format(n);
                }
            });
            if (field.range) {
                field.range.addEventListener('input', function () {
                    field.input.value = groupFormat.format(toNumber(field.range.value));
                    paintRange(field);
                    validate(field);
                    calculate();
                });
            }
        }

        function currentPlan() {
            var checked = form.querySelector('input[data-plan]:checked');
            return checked ? checked.getAttribute('data-plan') : 'sip';
        }

        function applyPlan() {
            var plan = currentPlan();
            form.querySelectorAll('input[data-plan]').forEach(function (radio) {
                radio.closest('.sc-radio-box').classList.toggle('sc-radio-box--checked', radio.checked);
            });
            fields.monthly.box.classList.toggle('hide', plan !== 'sip');
            fields.duration.box.classList.toggle('hide', plan !== 'duration');
            if (result) {
                result.querySelector('.sc-goal-based-calculator__right-wrapper-sip').classList.toggle('hide', plan !== 'sip');
                result.querySelector('.sc-goal-based-calculator__right-wrapper-duration').classList.toggle('hide', plan !== 'duration');
            }
        }

        function setText(selector, value) {
            var el = result.querySelector(selector);
            if (el) {
                el.textContent = value;
            }
        }

        function calculate() {
            if (!result) {
                return;
            }
            var plan = currentPlan();
            var needed = plan === 'sip' ? ['monthly', 'initial', 'goalAmount', 'rate'] : ['duration', 'initial', 'goalAmount', 'rate'];
            if (!needed.every(function (key) { return validate(fields[key]); })) {
                return;
            }

            var initial = valueOf('initial');
            var goal = valueOf('goalAmount');
            var monthlyRate = valueOf('rate') / 12 / 100;

            if (plan === 'sip') {
                var monthly = valueOf('monthly');
                var months = 0;
                while (months < MAX_MONTHS && futureValue(initial, monthly, months, monthlyRate) < goal) {
                    months++;
                }
                var years = Math.floor(months / 12);
                var yearsLabel = result.getAttribute('data-years-label') || 'years';
                var monthsLabel = result.getAttribute('data-months-label') || 'months';
                var timeEl = result.querySelector('.sc-goal-based-calculator__no-years');
                timeEl.textContent = '';
                [String(years), yearsLabel, String(months % 12), monthsLabel].forEach(function (part, i) {
                    if (i % 2) {
                        var label = document.createElement('span');
                        label.textContent = part;
                        timeEl.appendChild(label);
                        timeEl.appendChild(document.createTextNode(' '));
                    } else {
                        timeEl.appendChild(document.createTextNode(part + ' '));
                    }
                });
                setText('[data-total-sip]', groupFormat.format(Math.round(initial + monthly * months)));
                return;
            }

            var totalMonths = valueOf('duration') * 12;
            var initialGrown = futureValue(initial, 0, totalMonths, monthlyRate);
            var perRupee = futureValue(0, 1, totalMonths, monthlyRate);
            var sip = Math.ceil((goal - initialGrown) / perRupee);
            var surplus = sip <= 0;

            result.querySelector('.sc-goal-based-calculator__positive-result').classList.toggle('hide', surplus);
            result.querySelector('.sc-goal-based-calculator__negative-result').classList.toggle('hide', !surplus);
            setText('.sc-goal-based-calculator__result-amount', groupFormat.format(Math.max(sip, 0)));
            setText('[data-total-duration]', groupFormat.format(Math.round(initial + Math.max(sip, 0) * totalMonths)));
            setText('.sc-goal-based-calculator__surplus', groupFormat.format(surplus ? Math.round(initialGrown - goal) : 0));
        }

        applyPlan();
        calculate();
    }

    function init(scope) {
        scope.querySelectorAll('[data-gbic-form]').forEach(initCalculator);
    }

    function onReady() {
        init(document);
        // Author mode re-renders components after dialog edits; pick those up too.
        new MutationObserver(function (mutations) {
            mutations.forEach(function (m) {
                m.addedNodes.forEach(function (node) {
                    if (node.nodeType === 1) {
                        init(node.parentNode || node);
                    }
                });
            });
        }).observe(document.body, { childList: true, subtree: true });
    }

    if (document.readyState !== 'loading') {
        onReady();
    } else {
        document.addEventListener('DOMContentLoaded', onReady);
    }
}());
