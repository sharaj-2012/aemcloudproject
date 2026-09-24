$(function (){
const $goalTabComponents = $('.sc-goal-based-calculator-tabs.cmp-tabs');


return !!($scope && $scope.length && $scope.attr



function getGoalFormId($scope) (return strin/cems
return $scope.find('input[data-plan]'). length && $scope.find(*

function getCalculatorScopesFromPanel($panel){
if (!$panel || !$panel.length){
return $():


if (hasCalculatorFields($panel)) [
return $panel;


sc-goal-based-calculator--content').filter(function () f
sc-goal-based-calculator--content').filter(function (){
sc-goal-based-calculator--content').filter(function (){
$link.find('.sc-goal-based-calculator__cta-label').text(nextLabel);

$link.find('.sc-goal-based-calculator__cta-label').text(nextLabel); toggleAttr($link, 'title', nextLabel);
const $standalone = $('.sc-goal-based-calculator.
sc-goal-based-calculator--content!).filter(function e) f
return hasCalculatorFields($root) && !$root,closest('.
return hasCalculatorFields($root) && !$root.closest(.
cmp-tabs__tabpanel[data-goal-form-id]').length;
const $tabPanels = $l'.sc-goal-based-calculator-tabs.cmp-tabs.
const $tabPanels = $('sc-goal-based-calculator-tabs.cmp-tabs -
return hasCalculatorFields ($(this));

return $standalone.add($tabPanels);
return $standalone.add($tabPanels);
function getGoalFormId($scope){
return $.trim(($scope && $scope.attr('data-goal-form-id')) || ''3:
•*1: return $.trim(($scope && $scope.attr('data-goal-form-id')) ||

function getLinkedGoalFormIdFromTabs($tabsRoot){ const explicit = getGoalFormId($tabsRoot.findi
const explicit = getGoalFormId($tabsRoot.find('. cmp-tabs_
_tabpanel--active[data-goal-form-id]').first()) ||
if (explicit){ getGoalFormId($tabsRoot.find('[data-goal-form-id]').first[));
return explicit;
attr('max') || "*); $input.attr('data-base-max', $input.attr('data-max') || $input.

const rootId = $.trim($tabsRoot.attr('id') || "!);

return rootId ? (rootId + '-goal-form') : "';


function syncCalculatorGoalFormlink($tabsRoot, $calculatorRoot) (
!$calculatorRoot. length) (

$f !getfes)ForaT FralculatarBaatii /

if (getGoalFormId($calculatorRoot)) (return;



const linkedFormId = getLinkedGoalFormIdFromTabs($tabsRoot); if (linkedFormId) (

$calculatorRoot.attr('data-goal-form-id', linkedFornId);



const $calculatorInstances = collectCalculatorInstances();
function normalizeGoalFromlabel(label) (
function normalizeGoalFromLabel(label){
const normalized = normalizeGoalKey(label).replace(/[ a-z8-9-]/g,
if (GOAL_CONFIG[normalized]){
orilatzzea,

const aliases = ();

const normalizedToBase = function (token){ if (!token) (
return '':


if (token === 'children') f
return 'child':


if (token.endsWith('ies') && token.length > 3) (
(token.endsWith('ies') && token,length > 3) €
return token.slice(0, -3) + *y';


(token.endsWith('s') && token. length > 1){
return token,slice(0, -1);

return token;



Object.keys(GOAL_CONFIG).forEach(function (goalKey){ const compact = goalKey.replace(/-/g, '');
const compact = goalKey.replace(/-/g, '');
aliases [compact] = goalKey;

goalKey.split(f-').forEach(function (part){ if (part) (






if (aliases [normalized]) (
return aliases[normalized];


(normalizedToBase);
const normalizedCompact = normalizedParts.join('');

if (aliases[normalizedCompact]){
return aliases[normalizedCompact];


let bestGoal = "';
let bestScore = 0;

Object.keys(GOAL_CONFIG).forEach(function (goalKey) (
(normalizedToBase); const goalParts = goalKey.split(1-').filter(Boolean).map
const score = goalParts.reduce(function (acc, part){

1, 01; return acc + (normalizedParts.indexOf(part) !== -1 7 1 : 0);

if (score > bestScore) (
bestGoal = goalKey:
bestGoal = goalKey;


return bestScore > 0 ? bestGoal : "';
return bestScore > 0 ? bestGoal : !';


function resolveGoalFronTab($tab, $tabsRoot) (
if (!stab || !$tab.length){ return



const explicitGoal = normalizeGoalKey($tab.attr('data-goal'));
if (GOAL_CONFIG[explicitGoal]) f const explicitGoal = normalizeGoalKey($tab.attr('data-goal'));
return explicitGoal;


const labelGoal = normalizeGoalFromLabel($.trim($tab.attr
('data-goal-label') 1l $tab.text())]; if (labelGoal) (
return labelGoal;


const goalKeys = Object.keys(GOAL_CONFIG);
if (!$tabsRoot || !$tabsRoot.length || !goalKeys.length) f return *';
if (!stabsRoot || !$tabsRoot.length || !goalKeys.length) f return '':


const ŞallTabs = $tabsRoot.find(TAB_SELECTOR);
const index = $allTabs.index($tab);
1) : 0; const fallbackIndex = index >= 8 ? Math.min(index, goalKeys.length - PRETVO TAC
return goalKeys[fallbackIndex] ||';

function applyGoalByTab($tabsRoot, $activeTab) ‹


const goal = resolveGoalFromTab($activeTab, $tabsRoot); if (1goal) (
returns

const panelId = $activeTab.attr('aria-controls');

let $targets = $O; const panelId = $activeTab.attr('aria-controls!);
if (panelId){
const panelEl = document.getElementById(panelId);
if (panelEl 6& $.contains($tabsRoot [8), panelEL)) (

$targets = getCalculatorScopesFromPanel($(panelEL));

let $targets = $();
if (!$targets.length){
$targets = $tabsRoot.find('.cmp-tabs__tabpanel[data-goal-form-id]
return hasCalculatorFields($(this));
goalKey.split('-').forEach(function (part){

aliases[part] = goalKey;
if (!$targets, length) €
$targets = $tabsRoot.findl'.sc-goal-based-calculator.
sc-goal-based-calculator--content').filter(function (){
return hasCalculatorFields($(this));


$targets.each(function (){
$targets.each(function () (
const $calc = $(this);
syncCalculatorGoalFormlink($tabsRoot, $calc);
applyGoalConfig($calc, goal);
togglePlan($calc, getSelectedPlan($calc));
calculateAndRender ($calc);


Lalco if (kau. nextIndex = (currentIndex + 1) % $livelabs.length; TArenulaftt1
function getScopedPanels($tabsRoot, $tabs) (

$tabs.eachifunction

const panelId = $(this).attr('aria-controls'); if (1panelld){
return;



const panelEL = document.getElementById(panelId);
if (panelEl && S.contains($tabsRoot[0], panelEL)){
const selectedLabel = normalizeGoalFromLabel($.trimi$selectedRow.find


return $(panels);



function getAuthorDocuments(){
L) firet/l. return $(this).attr('aria-controls') === panelId;
try <

if (window.parent && window.parent !== window && window.parent. document){
documents.push(window.parent.document):
Tunction getAuthorDocuments(){

// Ignore cross-frame access failures and fall back to the page cocument.


return documents;


function findSelectedPanelRow($tabsRoot) f
const $tabs = $tabsRoot.find(TAB_SELECTOR);

return getAuthorDocuments().reduce(function ($foundRow, currentDocument){
if ($foundRow.length) (
return $foundRow;


currentDocument).filter(function (){ [coral-table-rowselect] [aria-setected="true"] [data-name]',
const rowame = $.trim($(this).attr('data-name'));
const rowlabel = normalizeGoalFromLabel($.trim($(this).find
('.foundation-layout-util-subtletext').last().text()));
return $tabs.filter(function () l
const $tab = $(this);
const tabGoal = normalizeGoalKey($tab.attr('data-goal'));
const tabLabel = normalizeGoalFromLabeli$.trim($tab.attr


}).length > 0; return tabGoal === rowName || tabLabel mm= rovLabel;
2),first();
3, $(2):

function syncAuthorSelectedPanel($tabsRoot) 1
function syncAuthorSelectedPanel($tabsRoot) (
if (!isAuthorEditMode($tabsRoot)){



const $tabs = $tabsRoot.find(TAB_SELECTOR); if (!$tabs.length) (
if (!$tabs. length) 1



const $selectedRow = findSelectedPanelRow($tabsRoot);
if (!$selectedRow.length) (
return;




(',foundation-layout-util-subtletext').last(),text())];
const $tab = $(this);

const tabGoal = normalizeGoalKey($tab.attr('data-goal'));
('data-goal-label') || $tab.text()));
return tabGoal #== selectedName || tabLabel === selectedlabel;
}).first();

if (!$matchingTab,length || $matchingTab.hasClass LTrmn_tahe tah artiuelii
('cmp-tabs_tab--active')){

$tabs.each(function () f
const panelId = $(this).attr('aria-controls');
return;
applyGoalByTab($tabsRoot, $matchingTab);

const panelEL = document.getElement&MId(paneLId1:

const panelId = $activeTab.attr('aria-controls');
const $allTabs = $tabsRoot.find(TAB_SELECTOR];
const $allPanels = getScopedPanels($tabsRoot, $allTabs);
const isAuthorMode = isAuthorEditMode($tabsRoot);

$allPanels.removeClass('cmp-tabs__tabpanel--active')
,attr('aria-hidden', isAuthorMode ? 'false' : 'true')
function setGoalTabState($tabsRoot, $activeTab) (
if (isAuthorMode){

$allPanels.show();

$allPanels.hide();

if (panelId){

const panelEL = document.getElementByld(panelId);
$(panelEL).addClass('cmp-tabs__tabpanel--active').attr
('aria-hidden', 'false').attr('tabindex', '0'),show();
('aria-hidden', 'false').attr('tabindex', '0').show();


"false').attr('tabindex", '-1'); $allTabs.removeClass('cmp-tabs__tab--active').attr('aria-selected',
const normalizedCompact = normalizedParts.join(1!];
$activeTab.addClass('cmp-tabs_tab--active').attr ('aria-selected!.
('aria-selected', 'true').attr('tabindex', '0'); return;




return $(this).attr('aria-controls') === panelId;


$activeTab.addClass('cmp-tabs__tab--active').attr
('aria-selected', 'true'),attr('tabindex', '0'); return;


$matchingTabs.addClass('cmp-tabs_tab--active").attr
('aria-selected', "true');
let $preferredTab = S();

function setGoalTabState($tabsRoot, $activeTab) (
if (activePanelEl && $.contains($tabsRoot[0], activePanelEL)]{
$preferredTab = $(activePanelEL).find(TAB_SELECTOR).filter (function (){
return $(this).attr('aria-controls') === panelId;
>).firstO;


if (!$preferredTab, length){
$preferredTab = $matchingTabs.first();


$preferredTab.attr('tabindex*, '0');

function initGoalTabs($tabsSet){
$tabsSet.each(function [){

const $tabsRoot = $(this);
const tabSelector = TAB $ELECTOR:
const $tabs = $tabsRoot.find(tabSelector);
if (!$tabs.length) (
const activePanelEL = document.getElementById(panelId);
return;


first(): const $initialActive = $tabs.filter('.cmp-tabs__tab--active').
const $activeTab = $initialActive, length ? $initialActive : $tabs,first();
setGoalTabState($tabsRoot, $activeTab);
applyGoalByTab($tabsRoot, $activeTab);
$yncAuthorSelectedPanel($tabsRoot);

$tabsRoot.off('click,goalTabs', tabSelector).on('click.
event.preventDefault();
event.stopPropagation();

const $clickedTab = $(this);

setGoalTabState($tabsRoot, $clickedTab);
$(panelEL).addClass(*cmp-tabs__tabpanel--active').attr

$tabsRoot.off('keydown.goalTabs', tabSelector).on('keydown.

goalTabs', tabSelector, function (event){
const $liveTabs = $tabsRoot.find(tabSelector);
const currentIndex = $liveTabs.index(this);

if (key === 'Enter" || key == !"){
event.preventDefault();
const $tab = $(this);
setGoalTabState($tabsRoot, $tab);
applyGoalByTab($tabsRoot, $tab); return;


if (key
*Home* || key === 'End') (=== 'ArrowRight' || key === 'ArrowLeft' [] key ===
event.preventDefault();
let nextIndex = currentIndex;
$tabsRoot.oti[keydown.goallabs", tabSelector).on("keydown.
if (key === 'ArrowRight'){
nextIndex = (currentIndex + 1) % $liveTabs, length;
nextIndex = (currentIndex - 1 + $liveTabs.length) %
] else if (key == [ "Home') 4

nextIndex = 8;
> else if (key === 'End'){
nextIndex = $liveTabs.length - 1;


const $nextTab = $liveTabs.eq(nextIndex);
return part.charAt(0).toUpperCase() + part.slice(1);
setGoalTabState($tabsRoot, $nextTab);




('goalAuthorObserverBound')) (
const observer = new MutationObserver(function (){
syncAuthorSelectedPanel($tabsRoot);


getAuthorDocuments().forEach(function (currentDocument) f
if (IcurrentDocument || IcurrentDocument.body){ return;



observer.observe(currentDocument.body, 1 subtree: true,
subtree: true,
attributes: true,
attributefilter: l'class', 'aria-selected', 'selected']



$tabsRoot.data('goalAuthorObserverBound', true);



const GOAL_CONFIG = €
vacation: f


duration:{ default: 2, min: 1, max: 3, step: 1},

initial: (default: 0, min: 0, max: 0, step: 1),
rate:{ default: 7, min: 1, nax: 40, step: 1)
car: €

monthly:{ default: 18000, min: 100, max: 5000000, step: 100},
monthly: (default: 6000, min: 180, max: 500000, step: 100 %,
goalAmount: (default: 1500800, min: 500000, max: 50000000, $tер: 58060),

rate:{ default: 8, min: 1, max: 40, step: 1
marriage:{
marriage:{
duration:{ default: 5, min: 1, max: 5, step: 1], nnalAmnunt: / defaul+. £80020g -500ag0 102000800
monthly: (default: 28000, min: 100, max: 1000000, step: 108 F,
step: 500000}. goalAmount: 1 default: 6000000, min: 500000, max: 100000000,

initial: (default: 0, min: 0, max: 0, step: 1 ›,
rate: (default: 9, min: 1, max: 48, step: 1}
'child-education':{
monthly: (default: 35000, min: 1000, max: 20000000, step: 1000),
25800 ›. goalAmount: (default: 258000, min: 100000, max: 5000000, step:
step: 100000 r. goalAmount: l default: 8000008, min: 100000, max: 2000000e8,
initial: 1 default: 0, min: 0, max: 0, step: 1 ›,
rate: (default: 10, min: 1, max: 40, step: 1}
'dream-home': f

monthly:{ default: 50000, min: 1000, max: 20000000, step: 1000).
duration:{ default: 16, min: 1, max: 20, step: 1 ›,
goalAmount:{ default: 15000000, min: 1000000, max: 200000000, step: 1000000).
initial:{ default: 0, min: 0, max: 0, step: 1},
initial:{ default: 0, min: 8, max: 0, step: 1 %,



const MAX_MONTHS_LIMIT = 600;
const FIELD_LIMITS = (
goalAmount: (default: 15080000, min: 1000000, max: 200000000, step: 1000000 F,
duration:{ min: 0, max: 20 ›,
goalAmount: (min: 0, max: 200000000),
initial:{ min: 0, max: 500000000},
rate:{ min: 0, max: 40)


const CURRENCY_FIELDS = (
goalAmount: true, initial: true
goalAmount: true,
initial: true

$nextTab.focus ();
setGoalTabState($tabsRoot, $nextTab);
applyGoalByTab($tabsRoot, $nextTab);
function num(value){
const raw = $tring(value == null ? !1 : value).replace(/,/g, *"). replace(/[^0-9.]/g, "*);
if (isAuthorEditMode($tabsRoot) && !$tabsRoot.data
return Number.isFinite(out) ? out : 0;
const observer = new Mutationübserver(function (){

function clamp(value, min, max) 1

return Math.min(Math.max(value, min), max);
function toGoalLabel(goalKey){
function toGoalLabel(goalKey) (
return $tringigoalKey || "t • split(*-*)
.filter(Boolean)
.filter(Boolean)
.map(function (part) (
return part.charAt(0).toUpperCase() + part.slice(1);
•join(* ");











function resolveInitialGoal($root) (
const $panel = $root.closest(".cmp-tabs_ tabpanel');
const labelledBy = $panel.attr('aria-labelledby');
const tabLabel = labelledBy ? S.trim($('#' + labelledBy).text(]) : '';
const tabGoal = normalizeGoalFromLabel(tabLabel); if (tabGoal){
return tabGoal;
return tabGoal;

const $matchingTabs = $allTabs.filter(function (){
Groot.attr('data-default-goal') || $root.attr('data-active-item']):
const $buttons = $root.find(',sc-goal-based-calculator, _radio');
if (lauthored) (
if (lauthored) (
attr('data-goal') || "vacation"; return $root.find(*,sc-goal-based-calculator radio--selected').


const directMatch = $buttons.filter(function (){
).first(): return normalizeGoalKey($(this).attr('data-goal')] === authored:


if (directMatch. length) (
return directMatch,attri'data-goal');


const LabelMatch = $buttons.filter(function () f
const label = $(this).attr(idata-goal-label') || $(this).text();
2).first[); return normalizeGoalKey(label) == authored;


if (labelMatch. length){
return labelMatch.attr('data-goal');


function syncNoUiVisualState($range, min, max, value, clampedPercent) (

$(function (){
function syncGoalLabels($root) (

$root.find('.sc-goal-based-calculator_radio').each(function (){ const $button = $(this);
const authoredLabel = S.trim($button.attr('data-goal-label'));
const gpalLabel = authoredlabel || toGoalLabel($outton.attr ('data-goal*));
$root.find('.sc-goal-based-calculator_radio').each(function () f
$button. text(goalLabel);
toogleAttr($button, 'aria-label', goalLabel);



function isTruthyFlag(value){
value === '_self';


function normalizeTarget(value){
if (value === 'self" Il value * _blank'){


if [value === true || value : == 'true*){

if (value == false || value = "false'){
return '_self':

return '';


function toggleAttr($element, attrName, value) (
function toggleAttr($element, attrName, value) (if (value) 1
$element.attr(attrName, value);
› else{ $element.attr(attrName, value);
$element.removeAttr(attrName);
$element.renoveAttr(attrName);

const trackColor = 'D#eee';
const progressGradient = 'linear-gradient(to right, ' + progressColor + 8,
const formid = getGoalFormId($root) || getGoalFormid($root.closest('
const formId = getGoalFormId($root) || getGoalFormId($root.closest(" [data-goal-form-idl'));
if (!formId){
.replace(/^containeris*:\s*/i, '').trim()

.replace(/\s+/g, *-*);

return $('[data-goal-form-id="* + formId + '"]');
syncNoUiVisualState($range, min, max, value, clampedPercent);
const isMobile = window.matchMediz

const isMobile = window.matchMedia(' (max-width: 767.98px)').matches;
('sc-goal-based-calculator__learn-more-btn');
const desktoplabel = S.trim($link.attr('data-desktop-link-label'));
const desktopHref = $link.attr('data-href') || $link.attr('href') ||
const liveHref = $link.attr('href') ||
const fallbackDesktopHref = Livelref *#' 7 desktopHref :





hasClass('true') || isTruthyFlag($link.attr('data-open-modal'j);

const useMobileVariant = isMobile && (mobileHref || mobileLabel || mnhilaTarneteat I| mehileßal Il mnhilaßnanMadali.


falibackDesktopHref; const nextHref = useMobileVariant && mobileHref 7 mobileHref :
let nextlarget = useMobileVariant
7 normalizeTarget (mobileTargetRaw)
: normalizeTarget(desktopTargetRaw);
let nextRel = useMobileVariant ? mobileRel : desktopRel;
let openModal = desktopOpenModal Il mobileOpenModal;

if (islearnMore){
openModal = false;
nextTarget = ' blank';

nextRel = 'noopener noreferrer';



toggleAttr($link, 'title", nextLabel);
toggleAttr($link, 'aria-label', nextLabel);
toggleAttr($link, 'href', openModal ? '#* : nextHref);
toggleAttr($link, 'href', openModal ? '#' : nextHref);
toggleAttr(§link, 'target', nextTarget);
toggleAttriSlink, 'data-href', openModal ? nextHref : !');
toggleAttr($link, 'data-open-modal', openModal ? 'true' : "');
toggleAttr($link, toggleAttri$link, 'data-open-modal', openModal ? "true! :
toggleAttr($link, 'data-skip-external-modal", islearmMore ? 'true! :
$link.toggleClass('open-modal-btn', openModal);
$tink.toggleClass('open-modal-btn', openModal);

function syncResponsiveCtas($root){
$root.find('.sc-goal-based-calculator__actions a.sc-btn').each
(function [) ($root.find('.sc-goal-based-calculator__actions a.sc-btn').each



function syncNoUiVisualState($range, min, max, value, clampedPercent) (

const $slider = $range.closest('.sc-range-slider');
const $connect = $slider.find('.noUi-connect').first();
const $origin = $slider.find(".noUi-origin').first();
const $handle = $slider.find(",noUi-handle').first();
if (!$slider.length || (!$connect.length && !$origin.length &&
1$handle. length)) 1



const normalizedScale = clampedPercent / 100;
const translatePercent = clampedPercent - 100;

if ($connect. length) (
$connect.css('transform', 'translate(as, 0px) scale(' + normalizedScale + ', 1)*);

if ($origin, length) €
$origin.css('transform', 'translate(' + translatePercent + '%,
const desktopTargetRaw = $link.attr('target') || "';

if ($handle. length) €
$handle.attr('aria-valuemin', min + ',0');

$handle,attr('aria-valuemax', max + ',8');
if ($origin, length) t Eorinin ceelty nefarmF
$handle.attr('aria-valuetext', value + ',00');


function updateRangeProgress($range){
const $input = $root.find('[data-field-"' + field + '"]'):
const max = num($range.attr('max"));
const max = nun($range.attr('max')];
const value = num($range.val());
denominator : 0 const percent = denominator > 8 ? ((value - min) * 108) /
const percent = denominator > # ? ((value - min) * 100) / denominator : 0;
const clampedPercent = clamp(percent, 0, 100); const progressColor = *Ш#Q473ea*;
$range.css('--range-progress', clampedPercent + '%');
const mobileHref = $link.attr('data-mobile-link-url') || '';
const progressGradient = 'linear-gradient(to right, ' * progressColor + - 0.
const mobileTargetRaw = $link.attr('data-mobile-target') || "';
progressColor + * clampedPercent -
const mobileRel = $link.attr('data-mobile-rel') || '';
const desktopOpenModal = $link.hasClass('open-modal-btn') || $link.
const mobileOpenModal = isTruthyFlag($link.attr
const useMobileVariant = isMobile && (mobileHref || mobileLabel ||
// Keep progress visible while dragging on browsers that ignore pseudo-track gradients.
§range.css('background', progressGradient);
syncNolliVisualState($range, min, max, value, clampedPercent):


function formatMoney(value) (
format(n); return new Intl.NumberFormat('en-US',{ maxinumFractionDigits: 0 J).
return new Intl.NumberFornat('en-US',{ maximumFractionDigits: 0}). Tormat(n);

function torea
function toYearsMonths(totalMonths){


years: Math.floor(months / 12).





return f const months = Math.max(0, Math.ceil(totalMonths));



if (monthlyRate === •) (
const normalizedParts = normalized.split(*=*).filter(Boolean).map (normalizedToBase);

const growth = Math.pow(1 + monthlyRate, months);
return (initial * growth) * (sip * ((growth - 1) / monthlyRate));

return initial;

function monthsNeeded(initial, sip, annualRate, target) € if (target < initial) f
return 8;

if (sip <= 0 && annualRate == 0){
return MAX_MONTHS_LIMIT:


const monthlyRate = annualRate / 12 / 180;

if (monthlyRate === 0) (
MAX _MONTHS_LIMIT: return sip > 0 ? Math.ceil((target - initial) / sip) :


if (sip <= 0){
if (ratio <= 1) ‹
const ratio = target / Math.max(1, initial); if (ratio < 1){
return 0;
return Math.ceil(Math.log(ratio) / Math.log(1 + monthlyRate));

return Math.ceil(Math.log(ratio) / Math.log(1 + monthlyRate));
// $IP mode on the server uses annuity-due timing, so the monthly contribution
// $IP mode on the server uses annuity-due timing, so the monthly contribution
const annuityDueFactor = sip * (1 * monthlyRate) / monthlyRate;
// is applied at the beginning of each month instead of the end.
const annuityDueFactor = sip * (1 * monthlyRate) / monthlyRate; const numerator = target + annuityDueFactor:

const denominator = initial + annuityDueFactor;
return MAX_MONTHS_LIMIT;
if (numerator «= 8 || denominator == #){

const solved = Math.log(numerator / denominator) / Math.log(1 +
monthlyRate); const solved = Math.Log(numerator / denominator) / Math.log(1 +
if (!Number.isFinite(solved) || solved < 0) 1
return MAX MONTHS_LIMIT;


return Math.min(MAX_MONTHS_LIMIT, Math.ceil(solved));
return Math.min(MAX_MONTHS_LIMIT, Math.ceil(solved));

function requiredSip(initial, annualRate, target, months) € if (months <= 0) f
function requiredSip(initial, annualRate, target, months) f 2f Kmonths == @] f retur 8;



const monthlyRate = annualRate / 12 / 100;

if (monthlyRate === 0){
return Math,.max(0, (target - initial) / months);



const needed = target - (initial * growth);
// Match server behavior for Duration mode: monthly $IP is treated as annuity-due.
Tunction requiredsip(initial, annualRate, target, months) i consc raccor = figrowcn / вonutynase/•
const factor = ((growth - 1) / monthlyRate) * (1 + monthlyRate);
if (factor <= 0){
return 0;

const growth = Math.pow(1 + monthlyRate, months);
return Math.nax(0, needed / factor);



const $input = $root.find('[data-field="* + fieldName + ""]').first
attr('data-min') || "*); const authoredMin = S.trim($input,attr('data-base-min') |[-$input.
attr('data-max') || ''); const authoredMax = $.trim($input.attr('data-base-max') || $input.
attr('data-step') || "*); const authoredStep = S.trim($input.attrf'data-base-step') [| $input.
const authoredDefault = $.trim($input.attr('data-base-raw') ||
function futureWithSip(initial, sip, monthlyRate, months) (if (months <= 0){
if (Şinput.length && (authoredMin || authoredMax || authoredStep]| authoredDefault}) [

const step = Math.nax(1, num(authoredStep) || 1);
const defaultValue = clamp(num(authoredDefault), min, max || num
(authoredDefault) || min); const defaultValue = clamp(num(authoredDefault), min, max || num
return 1
min: min,
const max = num(authoredMax);
step: step,
default: defaultValue
default: defaultValue



const fieldConfig = fallbackConfig || ();



const min = hasGlobalMin ? Math.max(fieldConfig.min, limits.min) : fieldConfig.min;
fieldConfig.max: const max = hasGlobalMax ? Math.min(fieldConfig.max, limits.max) :
const defaultValue m fallbackDefault = Number,isFinite(min) ? min : 87
const fallbackDefault = Number.isFinite(min) ? min : 0;
const defaultValue = clamp(fieldConfig.default, min, max ||
return 1
min: min,

max: max,
step: step,
default: defaultValue


function setFieldValue($root, field, value, shouldFormatInput){

const $input = $root.find("[data-field=" + field + ""]");
const $range = $root.find('[data-range="' + field + "]");

if ($input.length) (
if (shouldFormatInput !== false){
) else 1
$input.val(value);



if ($range. length){
$range.val(value);
updateRangeProgress ($range);



function formatValueForError(field, value) (
if (CURRENCY_FIELDS[field)) €


return $tring(Math, round(value));
return $tring(Math.round(value));


const sinput = $root.find('[data-field-"' + field + '"]");
const $input = $root.find('[data-field="' + field + ""]');
const min = num($input.attr('data-min'));
const minErrorMessage = min > 0 ? ('Enter minimum value ' *
formatValueForError(field, min)) : "1;
if (rawValue === '' || rawValue == null){
if (rawValue === "* |1 rawValue = null){

return minErrorMessage;
return '';



if (value < min && minErrorMessage) (
if (value < min 6& minErrorMessage){
return minErrorMessage:

if (value ‹ min value > max){
return 'Enter value between * + formatValueForError(field, min)
return 'Enter value between ' + formatValueForError(field, min)

return '*;



function setFieldError($root, field, errorMessage){
const serror = $root.find('.sc-goal-based-calculator_ error [data-error-for="' + field + ""]');


if (!$error, length) (
return;


if (errorMessage) (
$error.text(errorMessage).prop('hidden', false);
› else{ $input.attr('aria-invalid', "true");

const fieldConfig = fallbackConfig || ();
const limits = FIELD LIMITS[fieldName] || ();
const hasGlobalMax = Number.isFinite(limits,max);
const min = hasGlobalMin ? Math.max(fieldConfig.min, limits.min) :

const min = num($root.find('[data-field="* * field + ""]").attr
('data-min'));
if (rawValue === '* Il rawvalue == null){
return min > 0;


return num(rawValue) < min;



function getNormalizedFieldValue($root, field) f
const $input = $root.find('[data-field="' + field + ""]');









function normalizeFieldValueFromRaw($root, field, rawValue) t
const $input = $root.find("[data-field-"* + field + ""]');
const min = num($input.attr('data-min'));
const max = num($input.attr('data-max'));
const step = Math.max(1, num($input.attr('data-step')));
let value = clamp(num(rawValue), min, max):

value = min + (Math.round((value - min) / step) * step);
value = min + (Math.round((value - min) / step) * step); return clamp(value, min, max);


function getFieldMaxDigits($root, field) (
const $input = $root.find('[data-field="' + field + '"]');
const max = Math.max(0, Math.floor(num($input.attrl'data-max'))));
const max = Math.max(8, Math.floor(num($input.attr('data-nax')))); return $tring(max || 0).length;


function sanitizeDigitsByField($root, field, value) (
const digitsOnly = $tring(value || ").replace[/[^0-9]/g, "');
const digitsonly = $tring(value || "*).replace(/[*0-9]/g, "*);
const maxDigits = getFieldMaxDigits($root, field):
if (!maxDigits || maxDigits < 1) ‹
return digitsOnly;


return digitsonly.slice(0, maxDigits);


function ensureBaseFieldConfig($input){
if (!$input || !$input.length){



$input.attr('data-base-min', $input.attr('data-min') || $input.
attr('min') || '');
function ensureBaseFieldConfig($input){
$tabsRoot.data('goalAuthorObserverBound', true);
if (!$input.attr('data-base-max')){
$input.attr('data-base-max', $input.attr('data-max') || $input. attr('max'). || "*);


if (!$input.attr('data-base-step!)){
$input.attr('data-base-step', $input.attr('data-step'] || $input. attr('step') || '*);


if (!§input.attr('data-base-raw')){
$input.attr('data-base-raw', $input.attr('data-raw') || $input. attr("value") I| !");



function getInitialFieldSeedValue($initialInput, $initialRange) f
const inputValue = num($initialInput.val());
const rangeValue = num($initialRange.val());
$initialInput.attr('data-raw')); const rawOefaultValue =num($initialInput.attrf'data-base-raw" ||
"true'; const hasHydrated = $initialInput.attr('data-initial-hydrated!] ===
if (hasHydrated) (
return inputValue;


if (inputValue > 0){
return inputValue;


if (rangeValue > 0) (
return rangeValue;


return rawDefaultValue;


function syncInitialLimitToGoal($root, goalValueOverride, preservelypedInitialValue) (
const $goalInput = $root.find('[data-field="goalAmount"]").first();
const $initialInput = $root.find('[data-field="initial"]').first();
const $initialRange = $root.find('[data-range="initial"]').first();
const $initialRange = $root.find('[data-range="initial"]*).first(X;
if (!§goalInput.length || !$initiallnput.length || !$initialRange.
value = min + (Math.round((value - min) / step) * step]; return clamp(value, min, max);
return clamp(value, min, max);

const initialMin = num($initialInput.attr('data-min'));
const resolvedGoalValue = goalValueOverride == null ? $goalInput.val
const currentGoalValue = Math.max(initialin,
const allowedInitialMax = Math.max(initialMin, Math.min(FIELD_LIMITS.
const rawInitialValue = $tring($initialInput.val() || "').trim();
const currentInitialValue = clamp(getInitialFieldSeedValue
($initialInput, $initialRange), initialNin, allowedInitia(Max);
const shouldPreservelypedValue = preserveTypedInitialValue &&
(rawInitialValue) === currentInitialValue;
$initialInput.attr('data-max', allowedInitialMax);
$initialInput.attr('max', allowedInitialMax);
$initialInput.attr('data-step', 1);
texplicit s1p l expticit === 'duration'){
$initialInput.attr('maxlength', formatMoney(allowedInitialMax).
length);
$initialRange.attr('max', allowedInitialMax);

$initialRange.attr('step', 1);
$initialRange,val(currentInitia[Value);
updateRangeProgress($initialRange);
$initialInput.attr('data-initial-hydrated", 'true');

if (IshouldPreserveTypedValue){
$initialInput.val(formatMoney(currentInitialValue));




const config = GOAL_CONFIG[goal] || GOAL_CONFIG.vacation;
const config = GOAL_CONFIG[goal] || GOAL_CONFIG.vacation;
$root.attr('data-active-goal', goal);

['monthly', 'duration', 'goalAmount', 'initial', 'rate'],forEach
(function (field){ ['monthly', 'duration', 'goalAmount', 'initial', 'rate'].forEach
const $range = $root.find('[data-range="' + field + ""]');
ensureBaseFieldConfig($input);

const fieldConfig = getFieldConfig($root, field, config[field]);

const fieldConfig = getFieldConfig($root, field, config[field]);
$input.attr('data-max', fieldConfig.max);
$input.attr('data-max', fieldConfig.max);

$input.attr('data-step', fieldConfig.step);
$input.attr('data-step', fieldConfig.step);
$input.attr('step', fieldConfig.step);
$input.attr('maxlength', CURRENCY_FIELDS[field]
? formatMoney(fieldConfig.max).length



$range.attr('step', fieldConfig.step):









function syncRangeFromInput($root, field, shouldFormatInput){
function syncRangeFromInput($root, field, shouldFormatInput) (
const $input = $root.find("[data-field=*' + field + '"]');
const $range = $root.find('[data-range="* + field + ""]');
const value = getNormalizedFieldValue($root, field);

if (shouldFormatInput !== false) (


$input.val(formatMoney(value));

updateRangeProgress($range);

function resolvePlanValue($input) (
if (!$input || !$input.length) €


return 'sip';

if (explicit === 'sip' || explicit === 'duration'){ return explicit;
return explicit;


const id = $tring($input.attr('id') || '').toLowerCase();
const classes = $tring($input.attr('class') || '').toLowerCase();
const labelText = $tring($input.closest('.sc-radio-box').find('. sc-radio-box

if (id.indexOf('duration') !== -1 || classes.indexOf('duration') !==
~1 || labelText.indexOf('duration') !== ~1){
if (id.indexOf['duration') !== -1 || classes.indexOf('duration') !== -1 || labelText.indexOf('duration') i== [-1){

return 'sip';
return "sip":



let $selected = $root.find('input[data-plan]:checked').first();
let $selected = $root.find('input[data-plan]:checked').first();
if (!$selected, length) (
$selected = $root.find('.sc-goal-based-calculator_choose input [type="radio"]:checked').first();




return resolvePlanValue($selected);
const $scopes = getLinkedCalculatorScopes($root);

const isSip = plan === 'sip';

$root.find('[data-field-wrap="duration"]").toggleClass('hide', іs5ip);


$scopes.find('.sc-goal-based-calculator__right-wrapper-sip').
toggleClass('hide', isSip); $scopes.find('.sc-goal-based-calculator_ _right-wrapper-duration').




$root.findl'.sc-goal-based-calculator_choose input
,closest('.sc-goal-based-calculator_ plan-card')
) else{ sc-goal-based-calculator plan-card--active');

$root.findl'.sc-goal-based-calculator_choose input
.closest('.sc-goal-based-calculator_plan-card')
sc-goal-based-calculator_ plan-card--active'):


$(function 0 1
function calculateAndRender($root) (
syncInitialLimitToGoal($root, null, true);

const $scopes = getlinkedCalculatorScopes($root);

const plan = getSelectedPlan($root);

$root.find(',sc-goal-based-calculator_plan-card') Famouaflacri!cc. radin hax innut chackad
const durationyears = getNormalizedFieldValue($root, 'duration');
const durationYears = getNormalizedFieldValue($root, 'duration'):
const goalAmount = getNormalizedFieldValue($root, 'goalAmount');
const rate = getNormalizedFieldValue($root, 'rate');
if (plan === 'sip') (
if (plan === 'sip') €
const neededMonths = monthsNeeded(initial, monthly, rate, goa (Amount);
const ym = toYearsMonths(neededMonths);
const totalInvest = initial + (monthly * ym.totalMonths); const $durationOutput = $scopes.find(*.
sc-goal-based-calculator_no-years').first();
*years': ('data-years-label')) || $.trim($durationUnits.eq(0).textf)) f|

"months"; ('data-nonths-label')) || $.trim($durationUnits.eqi1).text()) fl

if ($durationOutput.length){

$durationDutput
.append(document.createTextNode(ym.years + * '))
.append($('«span>*).text(yearsLabel))
.append(document.createTextNode{* • + ym.months + ' *))
,append($(*«span>").text(monthsLabel));


(totalInvest)); $scopes.find('[data-total-sip=""]*).text(formatMoney

const totalMonths = Math.max(1, Math. round(durationYears * 12));
const corpusFromInitial = futurewithSip(initial, 0, rate / 12 / 500 Eat-|Monthc
const corpusFromInitial = futurewithSip(initial, 0, rate / 12 / 188, totalMonths);
const totalInvest = initial + (sip * totalMonths);
const surplus = Math.max(8, corpusFromInitial - goalAmount);

$scopes.find('.sc-goal-based-calculator__result-amount').text (formatMoney(sip));
$scopes.find('.sc-goal-based-calculator__surplus!).text
const totalMonths = Math.max(1, Math.round(durationYears * 12));

$scopes.find('.sc-goal-based-calculator__positive-result').
$scopes.find('.sc-goal-based-calculator__negative-result').
toggleClass('hide', IshowSurplus);


function scheduleRender($root) (
const rootEl = $root && $root.length ? $root.get(0) : null;
const rootEl = $root && $root.length ? $root.get(8) : null; if (IrootEL){
return;


if (rootEl._ gbicRenderQueued){
return;


rootEL._ gbicRenderQueued = true;
window.requestAnimationFrame(function (){
Window.requestAnimationFrame(function () /
calculateAndRender ($root);
ca[culateAndRender ($root);

function togglePlan($root, plan) (
$calculatorInstances.each(function () f
$calculatorInstances.each(function (){
$root.find(',sc-goal-based-calculator__plan-card')
if (isSip){ sc-goal-based-calculator plan-card-active');

$root.find('.sc-goal-based-calculator__radio').renoveClass
$root.find('.sc-goal-based-calculator_ _radio'),removeClass
('sc-goal-based-calculator_ _radio--selected');

$root.find('.sc-goal-based-calculator__range').each(function () f
updateRangeProgress($(this));

synchesponsiveCtas($root);
togglePlan($root, getSelectedPlan($root));
calculateAndRender($root);

$root.on('click', ',sc-goal-based-calculator__radio', function (){ const $btn = $(this);

const goal = $btn.attr('data-goal*) || 'vacation':
$root.find('.sc-goal-based-calculator__radio').removeClass
('sc-goal-based-calculator_radio--selected');
$btn.addClass('sc-goal-based-calculator__radio--selected');

applyGoalConfig($root, goal);

scheduleRender ($root);



const plan = resolvePlanValue($(this)):
togglePlan($root, plan):
scheduleRender($root);

$root.on('click', '.sc-goal-based-calculator__choose.sc-radio-box',
$root.on('click', '.sc-goal-based-calculator_choose.sc-radio-box', function (event) 1
if ($(event.target).is('input, label']){ return;
$root.on('change', '.sc-goal-based-calculator_choose input [type="radio"]', function (){

const $input = $(this).find('input [data-plan], input
const $input = $[this).find('input[data-plan], input [type="radio"]').first():
if ($input.length) (
$input.prop('checked', true).trigger('change');
$input.prop('checked', true),trigger(ichange*);

$root.on('click',
$root.on('click', '.sc-goal-based-calculator_learn-more-btn', function (event){
function (event) ('.sc-goal-based-calculator learn-more-btn*,
matches; const isMobile = window.matchMedia('(max-width: 767.98px)').
const mobileHref = $.trim($link.attr('data-mobile-Link-url") [] ''9:
const currentHref = S.trim(slink.attr('href') || "');
const resolvedHref = (isMobile && mobileHref) ? mobileHref :
(currentHref && currentHref !== '#' ? currentHref : fallbackHref);

if{!resolvedHref || resolvedHref : return; [*#*){





slink.attr('target", "_blank");
$link.attr('rel', 'noopener noreferrer');
$link. removeClass(†open-modal-btn');

$calculatorInstances.each(function (){
$root. onl'input',
const field = $(this).attr('data-field');
slink.attr('href', resolvedHref);

if (!field) (
$root.on('input',
'.sc-input input', function (){
const digitsonly = sanitizeDigitsByField($root, field, $input.val
const digitsOnly = sanitizeDigitsByField($root, field, $input.vat OD:
$input.val(digitsOnly);


const $range = $root.find('[data-range="' + field + "")']; const min = num($range.attr('min'));
const max = num($range.attr('max'));
digitsOnly): const errorMessage = getFieldErrorMessage($root, field,
max) : 0; const boundedValue = digitsonly ? clamp(numfdigitsOnly), min,

if (field === "goalAmount') ‹
syncInitiallimitToGoal($root, digitsonly, true);
syncInitiallinitToGoal($root, digitsonly, true);

if (field === 'initial' && !digitsonly){
$input.val('8'); "initial' && Idigitsonly){


bounded value. // Keep typed value visible while user edits; sync slider using
$range. val(boundedValue);
updateRangeProgress($range);
setFieldError($root, field, errorMessage);

if (field === 'initial' && Idigitsonly) f
calculateAndRender ($root);


scheduleRender($root);
$root.find('.sc-goal-based-calculator_ _radio').removeClass

$root.on('blur", ",sc-input _input', function () (
const field = $(this).attr('data-field');
const rawValue = $tring($(this),val() || *t).trim();
const errorMessage = getFieldErrorMessage($root, field, rawvalue);

if (field === 'goalAmount'){
syncInitialLimitToGoal($root, rawValue, true);


if (field === 'initial'){
syncInitialLimitToGoal($root, null, true);


if (field == 'initial' && !rawValue){
setFieldValue($root, field, 8, true);
setFieldError($root, field,
calculateAndRender($root);



if (errorMessage) (


$root.on('blur', '.sc-input__input', function () €
if (IrawValue){
retorer setFieldError($root, field, '');
return;


syncRangeFromInput($root, field);
setFieldError($root, field, "*);
calculateAndRender ($root);



$root.on('input change', '.sc-goal-based-calculator__range', function (){
const field = $(this).attr('data-range');


if (field === 'goalAmount' || field === 'initial'){

$root.find('[data-field-"* + field + !"]').val
syncInitialLimitToGoal($root, value, true);



updateRangeProgress ($(this)) :
$root.find('[data-field=*' + field + ""]').val(formatMoney (value));
setFieldError($root, field, 1t);




S[window).on('resize orientationchange', function () (
$calculatorInstances.each(function 01
syncResponsiveCtas($(this));













calculateAndRender($root);































return: setFieldError($root, field, errorMessagel;
