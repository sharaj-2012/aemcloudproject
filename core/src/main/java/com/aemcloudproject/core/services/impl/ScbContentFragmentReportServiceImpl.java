package com.aemcloudproject.core.services.impl;

import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.cq.dam.cfm.ElementTemplate;
import com.adobe.cq.dam.cfm.FragmentTemplate;
import com.aemcloudproject.core.config.ScbCfBulkUploadConfiguration;
import com.aemcloudproject.core.services.ScbContentFragmentReportService;
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

import java.io.IOException;
import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exports every Content Fragment under offer-listing to an .xlsx, one sheet per model,
 * reading the stored values with the Content Fragment API.
 */
@Component(service = ScbContentFragmentReportService.class, configurationPid = ScbCfBulkUploadConfiguration.PID)
public class ScbContentFragmentReportServiceImpl implements ScbContentFragmentReportService {

    private static final String ACTION = "action";
    private static final List<String> SHEET_ORDER = Arrays.asList(
            "merchant-venue", "merchant-details", "category", "offer-card", "offer-cta", "offer-detail");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final String FOLDER = "sling:Folder";
    private static final String ORDERED_FOLDER = "sling:OrderedFolder";
    private static final String ASSET = "dam:Asset";

    private String reportPath;

    /**
     * Reads the report path from the configuration.
     *
     * @param config the CF bulk upload configuration
     */
    @Activate
    @Modified
    protected void activate(ScbCfBulkUploadConfiguration config) {
        reportPath = StringUtils.removeEnd(config.reportPath(), "/");
    }

    /**
     * {@inheritDoc}
     * When no fragments are found, the workbook holds a single "report" sheet with a message.
     */
    @Override
    public int scbWriteReport(ResourceResolver resolver, OutputStream out) throws IOException {
        Map<String, List<ContentFragment>> fragmentsByModel = new LinkedHashMap<>();
        Resource folder = resolver.getResource(reportPath);
        if (folder != null) {
            scbFindFragments(folder, fragmentsByModel);
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

            if (fragmentsByModel.isEmpty()) {
                Sheet sheet = workbook.createSheet("report");
                sheet.createRow(0).createCell(0).setCellValue("No content fragments found under " + reportPath);
            }
            for (String modelPath : scbSheetOrder(fragmentsByModel.keySet())) {
                scbWriteSheet(workbook, resolver, modelPath, fragmentsByModel.get(modelPath),
                        headerStyle, requiredHeaderStyle, wrapStyle);
            }
            workbook.write(out);
        }
        return fragmentsByModel.values().stream().mapToInt(List::size).sum();
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
     * Orders model paths like the import workbook; unknown models follow in the order found.
     *
     * @param modelPaths the model paths in the order found
     * @return the model paths in sheet order
     */
    private List<String> scbSheetOrder(Iterable<String> modelPaths) {
        List<String> ordered = new ArrayList<>();
        for (String name : SHEET_ORDER) {
            for (String modelPath : modelPaths) {
                if (scbModelName(modelPath).equals(name)) {
                    ordered.add(modelPath);
                }
            }
        }
        for (String modelPath : modelPaths) {
            if (!ordered.contains(modelPath)) {
                ordered.add(modelPath);
            }
        }
        return ordered;
    }

    /**
     * Writes one sheet named after the model: a header row with an empty action column
     * and the model fields (mandatory ones in red bold), then one row per fragment sorted by ID.
     *
     * @param workbook            the workbook to add the sheet to
     * @param resolver            the author's resource resolver
     * @param modelPath           the model path
     * @param fragments           the model's fragments
     * @param headerStyle         style for normal headers
     * @param requiredHeaderStyle style for mandatory headers
     * @param wrapStyle           style for multi-line cells
     */
    private void scbWriteSheet(XSSFWorkbook workbook, ResourceResolver resolver, String modelPath,
                               List<ContentFragment> fragments, CellStyle headerStyle,
                               CellStyle requiredHeaderStyle, CellStyle wrapStyle) {
        Sheet sheet = workbook.createSheet(scbModelName(modelPath));
        List<String> fields = scbFieldNames(resolver, modelPath, fragments.get(0));
        Set<String> requiredFields = scbRequiredFields(resolver, modelPath);

        Row header = sheet.createRow(0);
        scbHeaderCell(header, 0, ACTION, headerStyle);
        for (int f = 0; f < fields.size(); f++) {
            String field = fields.get(f);
            scbHeaderCell(header, f + 1, field, requiredFields.contains(field) ? requiredHeaderStyle : headerStyle);
        }

        int rowIndex = 1;
        for (ContentFragment fragment : scbSortById(fragments, fields)) {
            Row row = sheet.createRow(rowIndex++);
            for (int f = 0; f < fields.size(); f++) {
                ContentElement element = fragment.getElement(fields.get(f));
                Object value = element == null ? null : element.getValue().getValue();
                scbWriteCell(row.createCell(f + 1), value, wrapStyle);
            }
        }
        sheet.createFreezePane(0, 1);
    }

    /**
     * Sorts fragments by their ID field (named "id" or ending in "ID"), smallest first;
     * fragments without an ID go last.
     *
     * @param fragments the fragments to sort
     * @param fields    the model field names
     * @return a sorted copy, or the same list when the model has no ID field
     */
    private List<ContentFragment> scbSortById(List<ContentFragment> fragments, List<String> fields) {
        String idField = null;
        for (String field : fields) {
            if (field.equalsIgnoreCase("id") || field.endsWith("ID")) {
                idField = field;
                break;
            }
        }
        if (idField == null) {
            return fragments;
        }
        final String sortField = idField;
        List<ContentFragment> sorted = new ArrayList<>(fragments);
        sorted.sort(Comparator.comparingLong(fragment -> scbIdValue(fragment, sortField)));
        return sorted;
    }

    /**
     * Returns a fragment's ID as a number for sorting.
     *
     * @param fragment the fragment
     * @param idField  the ID field name
     * @return the numeric ID, or {@link Long#MAX_VALUE} when it is empty or not a number
     */
    private long scbIdValue(ContentFragment fragment, String idField) {
        ContentElement element = fragment.getElement(idField);
        Object value = element == null ? null : element.getValue().getValue();
        return value instanceof Number ? ((Number) value).longValue() : Long.MAX_VALUE;
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
     * when the model cannot be read.
     *
     * @param resolver  the author's resource resolver
     * @param modelPath the model path
     * @param sample    a fragment of the model, used as a fallback
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
        } else {
            Iterator<ContentElement> elements = sample.getElements();
            while (elements.hasNext()) {
                fields.add(elements.next().getName());
            }
        }
        return fields;
    }

    /**
     * Returns the fields marked Required in the model, read from the model's dialog definition
     * because the Content Fragment API does not expose the flag.
     *
     * @param resolver  the author's resource resolver
     * @param modelPath the model path
     * @return the mandatory field names; empty if the model cannot be read
     */
    private Set<String> scbRequiredFields(ResourceResolver resolver, String modelPath) {
        Set<String> required = new HashSet<>();
        Resource items = resolver.getResource(modelPath + "/jcr:content/model/cq:dialog/content/items");
        if (items == null) {
            return required;
        }
        for (Resource item : items.getChildren()) {
            String name = item.getValueMap().get("name", String.class);
            String flag = item.getValueMap().get("required", String.class);
            if (name != null && ("on".equals(flag) || "true".equals(flag))) {
                required.add(name);
            }
        }
        return required;
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
