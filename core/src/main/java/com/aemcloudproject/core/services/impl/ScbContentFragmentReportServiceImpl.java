package com.aemcloudproject.core.services.impl;

import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.cq.dam.cfm.ElementTemplate;
import com.adobe.cq.dam.cfm.FragmentTemplate;
import com.aemcloudproject.core.config.ScbCfBulkUploadConfiguration;
import com.aemcloudproject.core.services.ScbContentFragmentReportService;
import com.aemcloudproject.core.services.ScbModelProfile;
import com.aemcloudproject.core.utils.ScbCfBulkUploadUtil;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.AssetManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the CF bulk upload workbooks: the empty sample sheet, the report of every Content Fragment under
 * the picked folder (one sheet per model in model-profile order, values read with the Content Fragment
 * API) and the results report of a final run. Reports are saved in DAM under a new, timestamped name.
 */
@Component(service = ScbContentFragmentReportService.class, configurationPid = ScbCfBulkUploadConfiguration.PID)
public class ScbContentFragmentReportServiceImpl implements ScbContentFragmentReportService {

    private static final String ACTION = "action";
    private static final String SLUG = "slug";
    private static final String[] ACTIONS = {"CREATE", "UPDATE", "DELETE"};
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final String DEFAULT_FILE_NAME = "report";
    private static final String DEFAULT_RUN_FILE_NAME = "cf-upload-run";
    private static final String GUIDE_SHEET = "_Guide";
    private static final String TITLE_MARK = "# ";
    private static final String HEADING_MARK = "## ";

    /** Fixed text of the _Guide sheet: "# " = title, "## " = section heading, "" = empty row. */
    private static final String[] GUIDE_TEXT = {
        "# CF Bulk Upload \u2013 how to fill this workbook",
        "",
        "## How to use it",
        "1. Start from the sample sheet (Download sample sheet on the bulk upload page), fill it in and upload it to DAM, into /content/dam/aemcloudproject/imports.",
        "2. In AEM, open Tools \u2192 SCB Tools \u2192 Content Fragment Bulk Upload, pick the workbook and the fragment folder, and click Dry run.",
        "3. Fix any Fail rows, upload the file again and repeat until the dry run has no failures.",
        "4. Click Final run to write the changes. It is enabled only after a dry run without failures; a report of the run is saved in DAM.",
        "5. The upload never publishes anything. Publish the changed fragments in AEM.",
        "",
        "## Sheets and columns",
        "- Each sheet is one content fragment model. Don't rename the sheets.",
        "- Row 1 holds the column names. Keep them exactly as they are (case-sensitive, no spaces).",
        "- You may delete columns you don't need, except action and slug.",
        "- Sheets whose name starts with _ (like this one) are ignored by the upload.",
        "",
        "## The action column",
        "- Pick a value from the dropdown. An empty action means the row is ignored.",
        "- CREATE: makes a new fragment (it must not exist yet).",
        "- UPDATE: changes an existing fragment.",
        "- DELETE: deletes a fragment; only action and slug are read. A fragment that other fragments or pages point to, or that is still published, is not deleted (Warn): remove the references or unpublish it first.",
        "",
        "## The slug column",
        "- The fragment's name in its path, e.g. summer-sale for <picked folder>/offers/summer-sale. Lowercase letters, digits, - and _ only.",
        "- On UPDATE and DELETE it says which fragment to change. A different slug means a different fragment.",
        "- It is only used for the path and is not stored in the fragment.",
        "",
        "## Filling in cells",
        "- Title column: also sets the fragment's title shown in Assets.",
        "- Numbers: digits, with . for decimals. Yes/no: true/false, 1/0 or yes/no.",
        "- Dates: typed as text, e.g. 2026-10-01, 2026-10-01T09:30 or 2026-10-01T00:00:00.000+05:30. Format the column as Text, or start the value with '.",
        "- Rich text: HTML, as shown in a saved report.",
        "- References: the full path, e.g. /content/dam/aemcloudproject/cfs/offer-listing/categories/dining.",
        "- Several references: one path per line in the cell (Alt+Enter). Fields that take one reference, like offerCta, accept a single path.",
        "",
        "## Empty cells",
        "- CREATE: an empty cell leaves the field empty.",
        "- UPDATE: an empty cell clears the field. Columns you deleted are not touched.",
        "- An empty ID on UPDATE keeps the current ID.",
        "",
        "## IDs and required fields",
        "- Red headers are required: they must be filled on CREATE and can't be emptied on UPDATE.",
        "- Leave the ID empty on new rows. It's numbered automatically: the highest ID in the sheet or among the model's existing fragments + 1.",
    };
    private static final String XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final String RESULTS_SHEET = "Results";
    private static final String[] RESULT_COLUMNS = {"Sheet", "Row", "Slug", "Action", "Result", "Details"};
    private static final String[] RESULT_KEYS = {"sheet", "row", "slug", "action", "result", "details"};

    private String modelsPath;
    private int maxFileSizeMb;
    private int maxRows;
    private String reportFolder;
    private String reportFileName;
    private String runReportFileName;

    @Reference(service = ScbModelProfile.class, cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY)
    private volatile List<ScbModelProfile> profiles;

    /**
     * Reads the models path, limits, report folder and file name prefixes from the configuration.
     *
     * @param config the CF bulk upload configuration
     */
    @Activate
    @Modified
    protected void activate(ScbCfBulkUploadConfiguration config) {
        modelsPath = StringUtils.removeEnd(config.modelsPath(), "/");
        maxFileSizeMb = config.maxFileSizeMb();
        maxRows = config.maxRows();
        reportFolder = StringUtils.removeEnd(config.reportFolder(), "/");
        reportFileName = scbCleanPrefix(config.reportFileName(), DEFAULT_FILE_NAME);
        runReportFileName = scbCleanPrefix(config.runReportFileName(), DEFAULT_RUN_FILE_NAME);
    }

    /**
     * Turns a configured file name into a safe prefix: {@code .xlsx} is dropped and characters other than
     * letters, digits, {@code .}, {@code _} and {@code -} become {@code -}.
     *
     * @param value    the configured name
     * @param fallback the prefix used when nothing is left
     * @return the prefix, e.g. {@code offer-listing-report}
     */
    private String scbCleanPrefix(String value, String fallback) {
        String name = StringUtils.strip(StringUtils.removeEndIgnoreCase(StringUtils.trimToEmpty(value), ".xlsx")
                .replaceAll("[^A-Za-z0-9._-]+", "-"), "-.");
        return name.isEmpty() ? fallback : name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void scbWriteSample(ResourceResolver resolver, OutputStream out) throws IOException {
        scbWriteWorkbook(resolver, null, out);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String scbSaveReport(ResourceResolver resolver, String rootPath) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        scbWriteWorkbook(resolver, rootPath, buffer);
        return scbSaveFile(resolver, reportFileName, buffer.toByteArray());
    }

    /**
     * {@inheritDoc}
     * The single {@code Results} sheet lists the workbook, folder, user and time, then one row per result
     * in run order, with a filter on the header.
     */
    @Override
    public String scbSaveRunReport(ResourceResolver resolver, String filePath, String rootPath,
                                   List<Map<String, Object>> results) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = scbBoldStyle(workbook);
            Sheet sheet = workbook.createSheet(RESULTS_SHEET);
            String[][] info = {
                {"Workbook", filePath},
                {"Folder", rootPath},
                {"Run by", resolver.getUserID()},
                {"Run at", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))},
            };
            int rowIndex = 0;
            for (String[] line : info) {
                Row row = sheet.createRow(rowIndex++);
                scbHeaderCell(row, 0, line[0], headerStyle);
                row.createCell(1).setCellValue(StringUtils.defaultString(line[1]));
            }
            rowIndex++;
            int headerIndex = rowIndex;
            Row header = sheet.createRow(rowIndex++);
            for (int c = 0; c < RESULT_COLUMNS.length; c++) {
                scbHeaderCell(header, c, RESULT_COLUMNS[c], headerStyle);
            }
            for (Map<String, Object> result : results) {
                Row row = sheet.createRow(rowIndex++);
                for (int c = 0; c < RESULT_KEYS.length; c++) {
                    Object value = result.get(RESULT_KEYS[c]);
                    if (value instanceof Number) {
                        row.createCell(c).setCellValue(((Number) value).doubleValue());
                    } else if (value != null) {
                        row.createCell(c).setCellValue(value.toString());
                    }
                }
            }
            sheet.setAutoFilter(new CellRangeAddress(headerIndex, Math.max(headerIndex, rowIndex - 1), 0,
                    RESULT_COLUMNS.length - 1));
            int[] widths = {18, 8, 30, 10, 10, 100};
            for (int c = 0; c < widths.length; c++) {
                sheet.setColumnWidth(c, widths[c] * 256);
            }
            workbook.write(buffer);
        }
        return scbSaveFile(resolver, runReportFileName, buffer.toByteArray());
    }

    /**
     * Saves an .xlsx in the report folder as {@code <prefix>-<yyyy-MM-dd-HH-mm-ss>.xlsx} with the DAM
     * {@link AssetManager}, adding {@code -2}, {@code -3}, ... if that name is taken. A missing report
     * folder is created.
     *
     * @param resolver the author's resource resolver
     * @param prefix   the file name prefix
     * @param content  the .xlsx bytes
     * @return the path of the saved file
     * @throws IOException if the file cannot be saved
     */
    private String scbSaveFile(ResourceResolver resolver, String prefix, byte[] content) throws IOException {
        AssetManager assetManager = resolver.adaptTo(AssetManager.class);
        if (assetManager == null) {
            throw new IOException("the DAM asset manager is not available");
        }
        String baseName = reportFolder + "/" + prefix + "-" + LocalDateTime.now().format(FILE_TIME_FORMAT);
        String assetPath = baseName + ".xlsx";
        for (int n = 2; resolver.getResource(assetPath) != null; n++) {
            assetPath = baseName + "-" + n + ".xlsx";
        }
        try {
            if (resolver.getResource(reportFolder) == null) {
                ScbCfBulkUploadUtil.scbGetOrCreateFolder(resolver, reportFolder);
                resolver.commit();
            }
            Asset saved = assetManager.createAsset(assetPath, new ByteArrayInputStream(content), XLSX_MIME_TYPE, true);
            if (saved == null) {
                throw new IOException("DAM did not save " + assetPath);
            }
            return assetPath;
        } catch (IOException e) {
            resolver.revert();
            throw e;
        } catch (Exception e) {
            resolver.revert();
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * Writes the upload workbook: {@code _Guide} first, then one sheet per model with a profile, with
     * only its header row when there are no fragments, so the sample and a report share one layout.
     *
     * @param resolver the author's resource resolver
     * @param rootPath the folder whose fragments are exported, or {@code null} for the empty sample
     * @param out      the stream the .xlsx is written to
     * @throws IOException if writing to {@code out} fails
     */
    private void scbWriteWorkbook(ResourceResolver resolver, String rootPath, OutputStream out) throws IOException {
        Map<String, List<ContentFragment>> fragmentsByModel = new LinkedHashMap<>();
        Resource folder = rootPath == null ? null : resolver.getResource(rootPath);
        if (folder != null) {
            for (Resource asset : ScbCfBulkUploadUtil.scbFindFragments(folder)) {
                ContentFragment fragment = asset.adaptTo(ContentFragment.class);
                String modelPath = asset.getChild("jcr:content/data").getValueMap().get("cq:model", String.class);
                if (fragment != null && modelPath != null) {
                    fragmentsByModel.computeIfAbsent(modelPath, key -> new ArrayList<>()).add(fragment);
                }
            }
        }
        for (ScbModelProfile profile : profiles) {
            fragmentsByModel.putIfAbsent(modelsPath + "/" + profile.getModel(), new ArrayList<>());
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = scbBoldStyle(workbook);
            CellStyle requiredHeaderStyle = workbook.createCellStyle();
            Font redBold = workbook.createFont();
            redBold.setBold(true);
            redBold.setColor(IndexedColors.RED.getIndex());
            requiredHeaderStyle.setFont(redBold);
            CellStyle wrapStyle = workbook.createCellStyle();
            wrapStyle.setWrapText(true);

            scbWriteGuideSheet(workbook, resolver, headerStyle);
            if (fragmentsByModel.isEmpty()) {
                Sheet sheet = workbook.createSheet("report");
                sheet.createRow(0).createCell(0).setCellValue("No model profiles and no content fragments found");
            }
            for (String modelPath : scbSheetOrder(fragmentsByModel.keySet())) {
                scbWriteSheet(workbook, resolver, modelPath, fragmentsByModel.get(modelPath),
                        headerStyle, requiredHeaderStyle, wrapStyle);
            }
            workbook.setActiveSheet(0);
            workbook.write(out);
        }
    }

    /**
     * Creates a bold cell style.
     *
     * @param workbook the workbook the style belongs to
     * @return the style
     */
    private CellStyle scbBoldStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font bold = workbook.createFont();
        bold.setBold(true);
        style.setFont(bold);
        return style;
    }

    /**
     * Writes the {@code _Guide} sheet: the fixed authoring rules, the upload limits from the
     * configuration, and a table of every model profile with its folder, title and ID
     * columns and the model's required fields. The upload ignores this sheet.
     *
     * @param workbook    the workbook to add the sheet to
     * @param resolver    the author's resource resolver
     * @param headerStyle bold style for headings and the table header
     */
    private void scbWriteGuideSheet(XSSFWorkbook workbook, ResourceResolver resolver, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet(GUIDE_SHEET);
        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);

        List<String> lines = new ArrayList<>();
        for (String line : GUIDE_TEXT) {
            lines.add(line);
        }
        lines.add("");
        lines.add(HEADING_MARK + "Limits");
        lines.add("- Files up to " + maxFileSizeMb + " MB, and up to " + String.format(Locale.ROOT, "%,d", maxRows)
                + " rows with an action per upload.");
        lines.add("");
        lines.add(HEADING_MARK + "Sheets in this workbook");

        int rowIndex = 0;
        for (String line : lines) {
            Row row = sheet.createRow(rowIndex++);
            if (line.isEmpty()) {
                continue;
            }
            Cell cell = row.createCell(0);
            if (line.startsWith(TITLE_MARK)) {
                cell.setCellValue(line.substring(TITLE_MARK.length()));
                cell.setCellStyle(titleStyle);
            } else if (line.startsWith(HEADING_MARK)) {
                cell.setCellValue(line.substring(HEADING_MARK.length()));
                cell.setCellStyle(headerStyle);
            } else {
                cell.setCellValue(line);
            }
        }

        String[] columns = {"Sheet", "Folder (under the picked folder)", "Title column", "ID column", "Required fields"};
        Row header = sheet.createRow(rowIndex++);
        for (int c = 0; c < columns.length; c++) {
            scbHeaderCell(header, c, columns[c], headerStyle);
        }
        List<ScbModelProfile> ordered = new ArrayList<>(profiles);
        ordered.sort(Comparator.comparingInt(ScbModelProfile::getOrder));
        for (ScbModelProfile profile : ordered) {
            Set<String> required = ScbCfBulkUploadUtil.scbRequiredFields(resolver, modelsPath + "/" + profile.getModel());
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(profile.getModel());
            row.createCell(1).setCellValue(profile.getFolder());
            row.createCell(2).setCellValue(StringUtils.defaultString(profile.getTitleField(), "\u2013"));
            row.createCell(3).setCellValue(StringUtils.defaultString(profile.getIdField(), "\u2013"));
            row.createCell(4).setCellValue(SLUG + (required.isEmpty() ? "" : ", " + String.join(", ", required)));
        }

        int[] widths = {20, 30, 16, 14, 60};
        for (int c = 0; c < widths.length; c++) {
            sheet.setColumnWidth(c, widths[c] * 256);
        }
    }

    /**
     * Orders model paths by their profile's order; models without a profile follow in the order found.
     *
     * @param modelPaths the model paths in the order found
     * @return the model paths in sheet order
     */
    private List<String> scbSheetOrder(Iterable<String> modelPaths) {
        List<String> ordered = new ArrayList<>();
        modelPaths.forEach(ordered::add);
        ordered.sort(Comparator.comparingInt(modelPath -> {
            ScbModelProfile profile = scbFindProfile(scbModelName(modelPath));
            return profile == null ? Integer.MAX_VALUE : profile.getOrder();
        }));
        return ordered;
    }

    /**
     * Finds the profile of a model.
     *
     * @param model the model name
     * @return the profile, or {@code null} if no profile is configured for the model
     */
    private ScbModelProfile scbFindProfile(String model) {
        for (ScbModelProfile profile : profiles) {
            if (profile.getModel().equals(model)) {
                return profile;
            }
        }
        return null;
    }

    /**
     * Writes one sheet named after the model: a header row with the action and slug columns and the
     * model fields (slug and mandatory fields in red bold), then one row per fragment with its node name
     * as the slug, sorted by {@link #scbSort} (no rows when the model has no fragments). The action
     * column gets a CREATE / UPDATE / DELETE dropdown for the first {@code maxRows} rows.
     *
     * @param workbook            the workbook to add the sheet to
     * @param resolver            the author's resource resolver
     * @param modelPath           the model path
     * @param fragments           the model's fragments, possibly none
     * @param headerStyle         style for normal headers
     * @param requiredHeaderStyle style for mandatory headers
     * @param wrapStyle           style for multi-line cells
     */
    private void scbWriteSheet(XSSFWorkbook workbook, ResourceResolver resolver, String modelPath,
                               List<ContentFragment> fragments, CellStyle headerStyle,
                               CellStyle requiredHeaderStyle, CellStyle wrapStyle) {
        Sheet sheet = workbook.createSheet(scbModelName(modelPath));
        List<String> fields = scbFieldNames(resolver, modelPath, fragments.isEmpty() ? null : fragments.get(0));
        Set<String> requiredFields = ScbCfBulkUploadUtil.scbRequiredFields(resolver, modelPath);

        Row header = sheet.createRow(0);
        scbHeaderCell(header, 0, ACTION, headerStyle);
        scbHeaderCell(header, 1, SLUG, requiredHeaderStyle);
        for (int f = 0; f < fields.size(); f++) {
            String field = fields.get(f);
            scbHeaderCell(header, f + 2, field, requiredFields.contains(field) ? requiredHeaderStyle : headerStyle);
        }

        int rowIndex = 1;
        for (ContentFragment fragment : scbSort(fragments, scbFindProfile(scbModelName(modelPath)))) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(1).setCellValue(fragment.getName());
            for (int f = 0; f < fields.size(); f++) {
                ContentElement element = fragment.getElement(fields.get(f));
                scbWriteCell(row.createCell(f + 2), element == null ? null : element.getValue().getValue(), wrapStyle);
            }
        }
        scbAddActionDropdown(sheet);
        sheet.createFreezePane(0, 1);
    }

    /**
     * Adds a CREATE / UPDATE / DELETE dropdown to the action column, rows 2 to {@code maxRows + 1};
     * empty cells stay allowed and any other value is rejected by Excel.
     *
     * @param sheet the model sheet
     */
    private void scbAddActionDropdown(Sheet sheet) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(ACTIONS);
        DataValidation validation = helper.createValidation(constraint, new CellRangeAddressList(1, maxRows, 0, 0));
        validation.setSuppressDropDownArrow(true);
        validation.setEmptyCellAllowed(true);
        validation.setShowErrorBox(true);
        validation.createErrorBox("Action", "Choose CREATE, UPDATE or DELETE, or leave the cell empty.");
        sheet.addValidationData(validation);
    }

    /**
     * Sorts fragments by the profile's sort field, latest or highest first with empty values last, then
     * by the ID field, smallest first, with fragments without an ID last.
     *
     * @param fragments the fragments to sort
     * @param profile   the model's profile, or {@code null}
     * @return a sorted copy, or the same list when the profile has neither a sort nor an ID field
     */
    private List<ContentFragment> scbSort(List<ContentFragment> fragments, ScbModelProfile profile) {
        String idField = profile == null ? null : profile.getIdField();
        String sortField = profile == null ? null : profile.getSortField();
        if (idField == null && sortField == null) {
            return fragments;
        }
        Comparator<ContentFragment> byId = idField == null ? (a, b) -> 0
                : Comparator.comparingLong(fragment -> scbIdValue(fragment, idField));
        Comparator<ContentFragment> order = sortField == null ? byId
                : Comparator.comparing((ContentFragment fragment) -> scbSortValue(fragment, sortField),
                        Comparator.nullsLast(Comparator.<Double>reverseOrder())).thenComparing(byId);
        List<ContentFragment> sorted = new ArrayList<>(fragments);
        sorted.sort(order);
        return sorted;
    }

    /**
     * Returns a fragment's sort value.
     *
     * @param fragment  the fragment
     * @param sortField the sort field name
     * @return the date in milliseconds or the number, or {@code null} when the field is empty or neither
     */
    private Double scbSortValue(ContentFragment fragment, String sortField) {
        ContentElement element = fragment.getElement(sortField);
        Object value = element == null ? null : element.getValue().getValue();
        if (value instanceof Calendar) {
            return (double) ((Calendar) value).getTimeInMillis();
        }
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }

    /**
     * Returns a fragment's ID as a number for sorting.
     *
     * @param fragment the fragment
     * @param idField  the ID field name
     * @return the numeric ID (a number field, or a text field holding only digits), or
     *         {@link Long#MAX_VALUE} when it is empty or not a number
     */
    private long scbIdValue(ContentFragment fragment, String idField) {
        ContentElement element = fragment.getElement(idField);
        Object value = element == null ? null : element.getValue().getValue();
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && ((String) value).trim().matches("\\d{1,18}")) {
            return Long.parseLong(((String) value).trim());
        }
        return Long.MAX_VALUE;
    }

    /**
     * Writes a stored value into a cell in the format the importer reads back: numbers as numeric
     * cells, booleans as TRUE/FALSE, dates as ISO text and multiple values as one per line.
     *
     * @param cell      the cell to write
     * @param value     the stored value, may be {@code null}
     * @param wrapStyle style applied to multi-value cells
     */
    private void scbWriteCell(Cell cell, Object value, CellStyle wrapStyle) {
        if (value == null) {
            return;
        }
        if (value instanceof String[]) {
            String[] values = (String[]) value;
            if (values.length > 0) {
                cell.setCellValue(String.join("\n", values));
                cell.setCellStyle(wrapStyle);
            }
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof Calendar) {
            cell.setCellValue(scbFormatDate((Calendar) value));
        } else {
            cell.setCellValue(value.toString());
        }
    }

    /**
     * Formats a date as {@code yyyy-MM-dd'T'HH:mm:ss.SSSXXX}, keeping its own offset.
     *
     * @param calendar the date
     * @return the formatted date, e.g. {@code 2026-10-01T00:00:00.000+05:30}
     */
    private String scbFormatDate(Calendar calendar) {
        int offsetSeconds = (calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)) / 1000;
        OffsetDateTime dateTime = calendar.toInstant().atOffset(ZoneOffset.ofTotalSeconds(offsetSeconds));
        return dateTime.format(DATE_FORMAT);
    }

    /**
     * Returns the model's field names in model order, or the sample fragment's fields
     * when the model cannot be read (none when there is no sample either).
     *
     * @param resolver  the author's resource resolver
     * @param modelPath the model path
     * @param sample    a fragment of the model, used as a fallback; may be {@code null}
     * @return the field names
     */
    private List<String> scbFieldNames(ResourceResolver resolver, String modelPath, ContentFragment sample) {
        List<String> fields = new ArrayList<>();
        Resource modelResource = resolver.getResource(modelPath);
        FragmentTemplate template = modelResource == null ? null : modelResource.adaptTo(FragmentTemplate.class);
        if (template != null) {
            Iterator<ElementTemplate> elements = template.getElements();
            while (elements.hasNext()) {
                fields.add(elements.next().getName());
            }
        } else if (sample != null) {
            Iterator<ContentElement> elements = sample.getElements();
            while (elements.hasNext()) {
                fields.add(elements.next().getName());
            }
        }
        return fields;
    }

    /**
     * Writes one header cell.
     *
     * @param header      the header row
     * @param column      the 0-based column index
     * @param text        the header text
     * @param headerStyle the cell style
     */
    private void scbHeaderCell(Row header, int column, String text, CellStyle headerStyle) {
        Cell cell = header.createCell(column);
        cell.setCellValue(text);
        cell.setCellStyle(headerStyle);
    }

    /**
     * Returns the model name, which is also the sheet name.
     *
     * @param modelPath the model path, e.g. {@code /conf/aemcloudproject/settings/dam/cfm/models/offer-cta}
     * @return the last path segment, e.g. {@code offer-cta}
     */
    private String scbModelName(String modelPath) {
        return modelPath.substring(modelPath.lastIndexOf('/') + 1);
    }
}
