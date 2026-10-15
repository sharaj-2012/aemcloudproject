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
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
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
 * Exports every Content Fragment under the report path to an .xlsx, one sheet per model in
 * model-profile order, reading the stored values with the Content Fragment API.
 */
@Component(service = ScbContentFragmentReportService.class, configurationPid = ScbCfBulkUploadConfiguration.PID)
public class ScbContentFragmentReportServiceImpl implements ScbContentFragmentReportService {

    private static final String ACTION = "action";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final String FOLDER = "sling:Folder";
    private static final String ORDERED_FOLDER = "sling:OrderedFolder";
    private static final String ASSET = "dam:Asset";
    private static final String DEFAULT_FILE_NAME = "report";
    private static final String GUIDE_SHEET = "_Guide";
    private static final String TITLE_MARK = "# ";
    private static final String HEADING_MARK = "## ";

    /** Fixed text of the _Guide sheet: "# " = title, "## " = section heading, "" = empty row. */
    private static final String[] GUIDE_TEXT = {
        "# CF Bulk Upload \u2013 how to fill this workbook",
        "",
        "## How to use it",
        "1. Edit this workbook in Excel and upload it to DAM, into /content/dam/aemcloudproject/imports.",
        "2. In AEM, open Tools \u2192 SCB Tools \u2192 Content Fragment Bulk Upload, pick the file, keep Dry run ticked and click Run.",
        "3. Fix any Fail rows, upload the file again and repeat until the dry run is clean.",
        "4. Untick Dry run and click Run to write the changes.",
        "5. The upload never publishes or deletes anything. Do that in AEM.",
        "",
        "## Sheets and columns",
        "- Each sheet is one content fragment model. Don't rename the sheets.",
        "- Row 1 holds the field names. Keep them exactly as they are (case-sensitive, no spaces).",
        "- You may delete columns you don't need, except action and the slug column.",
        "- Sheets whose name starts with _ (like this one) are ignored by the upload.",
        "",
        "## The action column",
        "- CREATE: makes a new fragment (it must not exist yet).",
        "- UPDATE: changes an existing fragment.",
        "- Empty: the row is ignored.",
        "- Upper or lower case doesn't matter. Any other value stops the whole upload.",
        "",
        "## Filling in cells",
        "- Slug: lowercase letters, digits, - and _ only, e.g. summer-sale. It becomes the fragment's name and path.",
        "- Title column: also sets the fragment's title shown in Assets.",
        "- Numbers: digits, with . for decimals. Yes/no: true/false, 1/0 or yes/no.",
        "- Dates: typed as text, e.g. 2026-10-01, 2026-10-01T09:30 or 2026-10-01T00:00:00.000+05:30. Format the column as Text, or start the value with '.",
        "- Rich text: HTML, as shown in this report.",
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
        "- Leave the ID empty on new rows. It's numbered automatically: the highest ID in the sheet + 1.",
        "- Keep the existing rows in the sheet so the numbering continues correctly.",
    };
    private static final String XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    /** Version label pattern; JCR labels can't contain {@code :}, so the time uses dots. */
    private static final DateTimeFormatter VERSION_LABEL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss");

    private String reportPath;
    private String modelsPath;
    private String fragmentRootPath;
    private int maxFileSizeMb;
    private int maxRows;
    private String reportFolder;
    private String reportFileName;

    @Reference(service = ScbModelProfile.class, cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY)
    private volatile List<ScbModelProfile> profiles;

    /**
     * Reads the report path, folder and file name from the configuration. In the file name, characters
     * other than letters, digits, {@code .}, {@code _} and {@code -} are replaced by {@code -}; an empty
     * name becomes {@code report}, and {@code .xlsx} is added.
     *
     * @param config the CF bulk upload configuration
     */
    @Activate
    @Modified
    protected void activate(ScbCfBulkUploadConfiguration config) {
        reportPath = StringUtils.removeEnd(config.reportPath(), "/");
        modelsPath = StringUtils.removeEnd(config.modelsPath(), "/");
        fragmentRootPath = StringUtils.removeEnd(config.fragmentRootPath(), "/");
        maxFileSizeMb = config.maxFileSizeMb();
        maxRows = config.maxRows();
        reportFolder = StringUtils.removeEnd(config.reportFolder(), "/");
        String name = StringUtils.strip(StringUtils.removeEndIgnoreCase(StringUtils.trimToEmpty(config.reportFileName()),
                ".xlsx").replaceAll("[^A-Za-z0-9._-]+", "-"), "-.");
        reportFileName = (name.isEmpty() ? DEFAULT_FILE_NAME : name) + ".xlsx";
    }

    /**
     * {@inheritDoc}
     * Uses the DAM {@link AssetManager}: a revision of the existing file is created first, then the
     * file's original rendition is replaced. A missing report folder is created.
     */
    @Override
    public Map<String, Object> scbSaveReport(ResourceResolver resolver) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        scbWriteReport(resolver, buffer);

        AssetManager assetManager = resolver.adaptTo(AssetManager.class);
        if (assetManager == null) {
            throw new IOException("the DAM asset manager is not available");
        }
        String assetPath = reportFolder + "/" + reportFileName;
        String versionLabel = null;
        try {
            Resource existing = resolver.getResource(assetPath);
            if (existing == null) {
                ScbCfBulkUploadUtil.scbGetOrCreateFolder(resolver, reportFolder);
                resolver.commit();
            } else {
                Asset asset = existing.adaptTo(Asset.class);
                if (asset == null) {
                    throw new IOException(assetPath + " exists but is not a DAM asset");
                }
                versionLabel = LocalDateTime.now().format(VERSION_LABEL_FORMAT);
                assetManager.createRevision(asset, versionLabel, null);
            }
            Asset saved = assetManager.createAsset(assetPath, new ByteArrayInputStream(buffer.toByteArray()),
                    XLSX_MIME_TYPE, true);
            if (saved == null) {
                throw new IOException("DAM did not save " + assetPath);
            }
        } catch (IOException e) {
            resolver.revert();
            throw e;
        } catch (Exception e) {
            resolver.revert();
            throw new IOException(e.getMessage(), e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", assetPath);
        result.put("versionLabel", versionLabel);
        return result;
    }

    /**
     * {@inheritDoc}
     * The first sheet is {@code _Guide} with the authoring rules. Every model with a profile gets a
     * sheet, with only its header row when it has no fragments, so the report doubles as an empty template.
     */
    @Override
    public void scbWriteReport(ResourceResolver resolver, OutputStream out) throws IOException {
        Map<String, List<ContentFragment>> fragmentsByModel = new LinkedHashMap<>();
        Resource folder = resolver.getResource(reportPath);
        if (folder != null) {
            scbFindFragments(folder, fragmentsByModel);
        }
        for (ScbModelProfile profile : profiles) {
            fragmentsByModel.putIfAbsent(modelsPath + "/" + profile.getModel(), new ArrayList<>());
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);
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
     * Writes the {@code _Guide} sheet: the fixed authoring rules, the upload limits from the
     * configuration, and a table of every model profile with its folder, slug, title and ID
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

        String[] columns = {"Sheet", "Folder", "Slug column", "Title column", "ID column", "Required fields"};
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
            row.createCell(1).setCellValue(fragmentRootPath + "/" + profile.getFolder());
            row.createCell(2).setCellValue(profile.getNameField());
            row.createCell(3).setCellValue(StringUtils.defaultString(profile.getTitleField(), "\u2013"));
            row.createCell(4).setCellValue(StringUtils.defaultString(profile.getIdField(), "\u2013"));
            row.createCell(5).setCellValue(required.isEmpty() ? "\u2013" : String.join(", ", required));
        }

        int[] widths = {20, 62, 22, 16, 14, 40};
        for (int c = 0; c < widths.length; c++) {
            sheet.setColumnWidth(c, widths[c] * 256);
        }
    }

    /**
     * Walks a folder recursively and collects every Content Fragment, grouped by model path.
     *
     * @param folder           the folder to walk
     * @param fragmentsByModel receives the fragments, keyed by model path
     */
    private void scbFindFragments(Resource folder, Map<String, List<ContentFragment>> fragmentsByModel) {
        for (Resource child : folder.getChildren()) {
            String type = child.getValueMap().get("jcr:primaryType", String.class);
            if (FOLDER.equals(type) || ORDERED_FOLDER.equals(type)) {
                scbFindFragments(child, fragmentsByModel);
            } else if (ASSET.equals(type)) {
                ContentFragment fragment = child.adaptTo(ContentFragment.class);
                Resource data = child.getChild("jcr:content/data");
                String modelPath = data == null ? null : data.getValueMap().get("cq:model", String.class);
                if (fragment != null && modelPath != null) {
                    fragmentsByModel.computeIfAbsent(modelPath, key -> new ArrayList<>()).add(fragment);
                }
            }
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
     * Writes one sheet named after the model: a header row with an empty action column
     * and the model fields (mandatory ones in red bold), then one row per fragment sorted by ID
     * (no rows when the model has no fragments). The slug column ({@code nameField}) always holds the
     * fragment's node name, not the stored field, so every row addresses its own fragment on UPDATE
     * and an UPDATE back-fills an empty or hand-edited slug field.
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
        for (int f = 0; f < fields.size(); f++) {
            String field = fields.get(f);
            scbHeaderCell(header, f + 1, field, requiredFields.contains(field) ? requiredHeaderStyle : headerStyle);
        }

        int rowIndex = 1;
        ScbModelProfile profile = scbFindProfile(scbModelName(modelPath));
        String idField = profile == null ? null : profile.getIdField();
        String nameField = profile == null ? null : profile.getNameField();
        for (ContentFragment fragment : scbSortById(fragments, idField)) {
            Row row = sheet.createRow(rowIndex++);
            for (int f = 0; f < fields.size(); f++) {
                Object value;
                if (fields.get(f).equals(nameField)) {
                    value = fragment.getName();
                } else {
                    ContentElement element = fragment.getElement(fields.get(f));
                    value = element == null ? null : element.getValue().getValue();
                }
                scbWriteCell(row.createCell(f + 1), value, wrapStyle);
            }
        }
        sheet.createFreezePane(0, 1);
    }

    /**
     * Sorts fragments by the profile's ID field, smallest first; fragments without an ID go last.
     *
     * @param fragments the fragments to sort
     * @param idField   the ID field, or {@code null} when the model has none
     * @return a sorted copy, or the same list when there is no ID field
     */
    private List<ContentFragment> scbSortById(List<ContentFragment> fragments, String idField) {
        if (idField == null) {
            return fragments;
        }
        List<ContentFragment> sorted = new ArrayList<>(fragments);
        sorted.sort(Comparator.comparingLong(fragment -> scbIdValue(fragment, idField)));
        return sorted;
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
