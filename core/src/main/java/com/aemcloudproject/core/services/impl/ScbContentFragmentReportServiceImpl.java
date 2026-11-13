package com.aemcloudproject.core.services.impl;

import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.cq.dam.cfm.ElementTemplate;
import com.adobe.cq.dam.cfm.FragmentTemplate;
import com.aemcloudproject.core.config.ScbCfBulkUploadConfiguration;
import com.aemcloudproject.core.config.ScbOfferListingModel;
import com.aemcloudproject.core.services.ScbContentFragmentReportService;
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
import org.osgi.service.metatype.annotations.Designate;

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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the CF bulk upload workbooks for a picked offer-listing folder: the sample sheet (one example row
 * per model), the report of every Content Fragment (one sheet per model, values read with the Content
 * Fragment API) and the results of a final run. Reports are saved in DAM under a new, timestamped name.
 */
@Component(service = ScbContentFragmentReportService.class, configurationPid = ScbCfBulkUploadConfiguration.PID)
@Designate(ocd = ScbCfBulkUploadConfiguration.class)
public class ScbContentFragmentReportServiceImpl implements ScbContentFragmentReportService {

    private static final String ACTION = "action";
    private static final String SAMPLE = "SAMPLE";
    private static final String[] ACTIONS = {"CREATE", "UPDATE", "DELETE"};
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final String GUIDE_SHEET = "_Guide";
    private static final String TITLE_MARK = "# ";
    private static final String HEADING_MARK = "## ";

    /** Fixed text of the _Guide sheet: "# " = title, "## " = section heading, "" = empty row. */
    private static final String[] GUIDE_TEXT = {
        "# SCB TGL Content Fragment Bulk Upload – how to fill this workbook",
        "",
        "## How to use it",
        "1. Start from the sample sheet (Download sample sheet on the SCB TGL Content Fragment Bulk Upload page), fill it in and upload it to DAM, into /content/dam/aemcloudproject/imports.",
        "2. In AEM, open Tools → SCB Commons → SCB TGL Content Fragment Bulk Upload, pick the workbook and the offer-listing folder, and click Dry run.",
        "3. Fix any Fail rows, upload the file again and repeat until the dry run has no failures.",
        "4. Click Final run to write the changes. It is enabled only after a dry run without failures; a report of the run is saved in DAM.",
        "5. The upload never publishes anything. Publish the changed fragments in AEM.",
        "",
        "## Sheets and columns",
        "- Each sheet is one content fragment model. Don't rename the sheets.",
        "- Row 1 holds the column names. Keep them exactly as they are (case-sensitive, no spaces).",
        "- You may delete columns you don't need, except action and the sheet's slug column (e.g. offerSlug).",
        "- Sheets whose name starts with _ (like this one) are ignored by the upload.",
        "- In the sample sheet, row 2 (action SAMPLE, grey) is an example copied from an existing fragment and is never uploaded. Leave it or delete it.",
        "",
        "## The action column",
        "- Pick a value from the dropdown. An empty action means the row is ignored.",
        "- CREATE: makes a new fragment (it must not exist yet).",
        "- UPDATE: changes an existing fragment.",
        "- DELETE: deletes a fragment; only action and the slug column are read. A fragment that other fragments or pages point to, or that is still published, is not deleted (Warn): remove the references or unpublish it first.",
        "",
        "## The slug column",
        "- Column B of every sheet: merchantVenueSlug, merchantSlug, offerCategorySlug, offerCardSlug, offerCtaSlug or offerSlug.",
        "- The fragment's name in its path, e.g. summer-sale for <offer-listing folder>/offers/summer-sale. Lowercase letters, digits, - and _ only.",
        "- On UPDATE and DELETE it says which fragment to change. A different slug means a different fragment.",
        "- It is only used for the path and is not stored in the fragment.",
        "- The column named just slug (English Name, on category and offer-card) is a normal field: any text, stored in the fragment.",
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

    private int maxFileSizeMb;
    private int maxRows;
    private String reportFolder;
    private String fragmentReportFileName;
    private String runResultsFileName;

    /**
     * Reads the limits, report folder and file name prefixes from the configuration.
     *
     * @param config the CF bulk upload configuration
     */
    @Activate
    @Modified
    protected void activate(ScbCfBulkUploadConfiguration config) {
        maxFileSizeMb = config.maxFileSizeMb();
        maxRows = config.maxRows();
        reportFolder = StringUtils.removeEnd(config.reportFolder(), "/");
        fragmentReportFileName = config.fragmentReportFileName();
        runResultsFileName = config.runResultsFileName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void scbWriteSample(ResourceResolver resolver, String rootPath, OutputStream out) throws IOException {
        scbWriteWorkbook(resolver, rootPath, true, out);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String scbSaveReport(ResourceResolver resolver, String rootPath) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        scbWriteWorkbook(resolver, rootPath, false, buffer);
        return scbSaveFile(resolver, fragmentReportFileName, buffer.toByteArray());
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
            CellStyle headerStyle = scbFontStyle(workbook, true, false, null);
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
        return scbSaveFile(resolver, runResultsFileName, buffer.toByteArray());
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
        } catch (Exception e) {
            resolver.revert();
            throw e instanceof IOException ? (IOException) e : new IOException(e.getMessage(), e);
        }
    }

    /**
     * Writes the upload workbook: {@code _Guide} first, then one sheet per model in model order. A report
     * has one row per fragment under the folder; a sample has one grey {@code SAMPLE} example row instead.
     *
     * @param resolver the author's resource resolver
     * @param rootPath the picked offer-listing folder
     * @param sample   {@code true} for the sample sheet, {@code false} for the report
     * @param out      the stream the .xlsx is written to
     * @throws IOException if writing to {@code out} fails
     */
    private void scbWriteWorkbook(ResourceResolver resolver, String rootPath, boolean sample, OutputStream out)
            throws IOException {
        Map<String, List<ContentFragment>> fragmentsByModel = new HashMap<>();
        Resource folder = resolver.getResource(rootPath);
        if (folder != null) {
            for (Resource asset : ScbCfBulkUploadUtil.scbFindFragments(folder)) {
                ContentFragment fragment = asset.adaptTo(ContentFragment.class);
                String modelPath = ScbCfBulkUploadUtil.scbModelOf(asset);
                if (fragment != null && modelPath != null) {
                    fragmentsByModel.computeIfAbsent(modelPath, key -> new ArrayList<>()).add(fragment);
                }
            }
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = scbFontStyle(workbook, true, false, null);
            CellStyle requiredHeaderStyle = scbFontStyle(workbook, true, false, IndexedColors.RED);
            CellStyle sampleStyle = scbFontStyle(workbook, false, true, IndexedColors.GREY_50_PERCENT);
            sampleStyle.setWrapText(true);
            CellStyle wrapStyle = workbook.createCellStyle();
            wrapStyle.setWrapText(true);

            scbWriteGuideSheet(workbook, headerStyle);
            for (ScbOfferListingModel model : ScbOfferListingModel.values()) {
                List<ContentFragment> fragments = fragmentsByModel.getOrDefault(model.getModelPath(),
                        Collections.emptyList());
                scbWriteSheet(workbook, resolver, model, fragments, sample, headerStyle, requiredHeaderStyle,
                        sample ? sampleStyle : wrapStyle);
            }
            workbook.setActiveSheet(0);
            workbook.write(out);
        }
    }

    /**
     * Creates a cell style with a bold, italic and/or coloured font.
     *
     * @param workbook the workbook the style belongs to
     * @param bold     {@code true} for bold
     * @param italic   {@code true} for italic
     * @param color    the font colour, or {@code null} for the default
     * @return the style
     */
    private CellStyle scbFontStyle(XSSFWorkbook workbook, boolean bold, boolean italic, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(bold);
        font.setItalic(italic);
        if (color != null) {
            font.setColor(color.getIndex());
        }
        style.setFont(font);
        return style;
    }

    /**
     * Writes the {@code _Guide} sheet: the fixed authoring rules, the upload limits from the
     * configuration, and a table of every model with its folder, title and ID columns and required
     * fields. The upload ignores this sheet.
     *
     * @param workbook    the workbook to add the sheet to
     * @param headerStyle bold style for headings and the table header
     */
    private void scbWriteGuideSheet(XSSFWorkbook workbook, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet(GUIDE_SHEET);
        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);

        List<String> lines = new ArrayList<>();
        Collections.addAll(lines, GUIDE_TEXT);
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

        String[] columns = {"Sheet", "Folder (under offer-listing)", "Slug column", "Title column", "ID column",
            "Required fields"};
        Row header = sheet.createRow(rowIndex++);
        for (int c = 0; c < columns.length; c++) {
            scbHeaderCell(header, c, columns[c], headerStyle);
        }
        for (ScbOfferListingModel model : ScbOfferListingModel.values()) {
            List<String> required = new ArrayList<>();
            required.add(model.getSlugColumn());
            required.addAll(model.getRequiredFields());
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(model.getModel());
            row.createCell(1).setCellValue(model.getFolder());
            row.createCell(2).setCellValue(model.getSlugColumn());
            row.createCell(3).setCellValue(model.getTitleField());
            row.createCell(4).setCellValue(StringUtils.defaultString(model.getIdField(), "–"));
            row.createCell(5).setCellValue(String.join(", ", required));
        }

        int[] widths = {20, 30, 20, 16, 14, 60};
        for (int c = 0; c < widths.length; c++) {
            sheet.setColumnWidth(c, widths[c] * 256);
        }
    }

    /**
     * Writes one sheet named after the model: a header row with the action and slug columns and the
     * model fields (slug and required fields in red bold), then, for a report, one row per fragment
     * sorted by {@link #scbSort}, or, for a sample, one {@code SAMPLE} row copied from the fragment with
     * the highest ID (the last fragment in folder order when the model has no ID). The action column
     * gets a CREATE / UPDATE / DELETE dropdown from the first row an author fills.
     *
     * @param workbook            the workbook to add the sheet to
     * @param resolver            the author's resource resolver
     * @param model               the model
     * @param fragments           the model's fragments in folder order, possibly none
     * @param sample              {@code true} for the sample sheet
     * @param headerStyle         style for normal headers
     * @param requiredHeaderStyle style for required headers
     * @param valueStyle          style for multi-line cells, or for every cell of the sample row
     */
    private void scbWriteSheet(XSSFWorkbook workbook, ResourceResolver resolver, ScbOfferListingModel model,
                               List<ContentFragment> fragments, boolean sample, CellStyle headerStyle,
                               CellStyle requiredHeaderStyle, CellStyle valueStyle) {
        Sheet sheet = workbook.createSheet(model.getModel());
        List<String> fields = scbFieldNames(resolver, model);

        Row header = sheet.createRow(0);
        scbHeaderCell(header, 0, ACTION, headerStyle);
        scbHeaderCell(header, 1, model.getSlugColumn(), requiredHeaderStyle);
        for (int f = 0; f < fields.size(); f++) {
            String field = fields.get(f);
            scbHeaderCell(header, f + 2, field,
                    model.getRequiredFields().contains(field) ? requiredHeaderStyle : headerStyle);
        }

        List<ContentFragment> rows = sample ? scbSampleFragment(fragments, model) : scbSort(fragments, model);
        int rowIndex = 1;
        for (ContentFragment fragment : rows) {
            Row row = sheet.createRow(rowIndex++);
            if (sample) {
                row.createCell(0).setCellValue(SAMPLE);
            }
            row.createCell(1).setCellValue(fragment.getName());
            for (int f = 0; f < fields.size(); f++) {
                ContentElement element = fragment.getElement(fields.get(f));
                scbWriteCell(row.createCell(f + 2), element == null ? null : element.getValue().getValue(), valueStyle);
            }
            if (sample) {
                row.forEach(cell -> cell.setCellStyle(valueStyle));
            }
        }
        scbAddActionDropdown(sheet, sample ? rowIndex : 1);
        sheet.createFreezePane(0, 1);
    }

    /**
     * Picks the fragment copied into the sample sheet.
     *
     * @param fragments the model's fragments in folder order
     * @param model     the model
     * @return the fragment with the highest ID, or the last one when the model has no ID field; empty when
     *         there are no fragments
     */
    private List<ContentFragment> scbSampleFragment(List<ContentFragment> fragments, ScbOfferListingModel model) {
        if (fragments.isEmpty()) {
            return Collections.emptyList();
        }
        if (model.getIdField() == null) {
            return Collections.singletonList(fragments.get(fragments.size() - 1));
        }
        return Collections.singletonList(Collections.max(fragments,
                Comparator.comparingLong(fragment -> scbIdValue(fragment, model.getIdField(), Long.MIN_VALUE))));
    }

    /**
     * Adds a CREATE / UPDATE / DELETE dropdown to the action column, from the given row to row
     * {@code maxRows + 1}; empty cells stay allowed and any other value is rejected by Excel.
     *
     * @param sheet    the model sheet
     * @param firstRow the 0-based first row with the dropdown
     */
    private void scbAddActionDropdown(Sheet sheet, int firstRow) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(ACTIONS);
        DataValidation validation = helper.createValidation(constraint,
                new CellRangeAddressList(firstRow, maxRows + firstRow - 1, 0, 0));
        validation.setSuppressDropDownArrow(true);
        validation.setEmptyCellAllowed(true);
        validation.setShowErrorBox(true);
        validation.createErrorBox("Action", "Choose CREATE, UPDATE or DELETE, or leave the cell empty.");
        sheet.addValidationData(validation);
    }

    /**
     * Sorts fragments by the model's sort field, latest or highest first with empty values last, then
     * by the ID field, smallest first, with fragments without an ID last.
     *
     * @param fragments the fragments to sort
     * @param model     the model
     * @return a sorted copy, or the same list when the model has neither a sort nor an ID field
     */
    private List<ContentFragment> scbSort(List<ContentFragment> fragments, ScbOfferListingModel model) {
        String idField = model.getIdField();
        String sortField = model.getSortField();
        if (idField == null && sortField == null) {
            return fragments;
        }
        Comparator<ContentFragment> byId = idField == null ? (a, b) -> 0
                : Comparator.comparingLong(fragment -> scbIdValue(fragment, idField, Long.MAX_VALUE));
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
     * Returns a fragment's ID as a number.
     *
     * @param fragment the fragment
     * @param idField  the ID field name
     * @param missing  the value used when the ID is empty or not a whole number
     * @return the ID, or {@code missing}
     */
    private long scbIdValue(ContentFragment fragment, String idField, long missing) {
        ContentElement element = fragment.getElement(idField);
        Long id = element == null ? null : ScbCfBulkUploadUtil.scbParseId(element.getValue().getValue());
        return id == null ? missing : id;
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
     * Returns the model's field names in model order.
     *
     * @param resolver the author's resource resolver
     * @param model    the model
     * @return the field names; empty when the model cannot be read
     */
    private List<String> scbFieldNames(ResourceResolver resolver, ScbOfferListingModel model) {
        List<String> fields = new ArrayList<>();
        Resource modelResource = resolver.getResource(model.getModelPath());
        FragmentTemplate template = modelResource == null ? null : modelResource.adaptTo(FragmentTemplate.class);
        if (template != null) {
            Iterator<ElementTemplate> elements = template.getElements();
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
}
