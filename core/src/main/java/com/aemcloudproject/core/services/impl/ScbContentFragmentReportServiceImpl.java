package com.aemcloudproject.core.services.impl;

import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.cq.dam.cfm.ElementTemplate;
import com.adobe.cq.dam.cfm.FragmentTemplate;
import com.aemcloudproject.core.services.ScbContentFragmentReportService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;

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
 * WHAT: Exports every Content Fragment under offer-listing to an .xlsx, one sheet per model, in
 * the import format. Reads the stored values directly with the CF API (not GraphQL), so values
 * GraphQL would return as null — external URLs in offer-cta.url, image paths whose asset doesn't
 * exist yet — still appear in the report exactly as stored.
 *
 * CALL ORDER:
 *   scbWriteReport(resolver, out)
 *     ├─ scbFindFragments(folder)          walk offer-listing -> {"offer-cta" -> [book-now, shop-online, ...], ...}
 *     ├─ scbSheetOrder(models)             merchant-venue, merchant-details, category, offer-card, offer-cta, offer-detail
 *     └─ for each model:
 *          └─ scbWriteSheet(model, fragments)
 *               ├─ scbFieldNames(model)    [offerCtaSlug, label, url, deeplink] in model order
 *               ├─ scbRequiredFields(model) {offerCtaSlug} -> those headers in red bold
 *               ├─ scbSortById(fragments)  offer-detail rows by offerID: 10001, 10002, 10003 (sheets without an ID: unchanged)
 *               └─ for each fragment, each field:
 *                    scbWriteCell(value)   10001 -> number cell, true -> TRUE, String[] -> lines, Calendar -> ISO text
 */
@Component(service = ScbContentFragmentReportService.class)
public class ScbContentFragmentReportServiceImpl implements ScbContentFragmentReportService {

    // The folder the report covers (fixed, like the upload root).
    private static final String REPORT_PATH = "/content/dam/aemcloudproject/cfs/offer-listing";
    private static final String ACTION = "action";
    // Sheets come out in the same order as cf-import-combined.xlsx; any other model goes after these.
    private static final List<String> SHEET_ORDER = Arrays.asList(
            "merchant-venue", "merchant-details", "category", "offer-card", "offer-cta", "offer-detail");
    // Same date text as the import workbook: 2026-10-01T00:00:00.000+05:30
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final String FOLDER = "sling:Folder";
    private static final String ORDERED_FOLDER = "sling:OrderedFolder";
    private static final String ASSET = "dam:Asset";

    /**
     * WHAT: Builds the whole workbook and writes it to out. See the interface for the contract.
     *
     * INPUT:  resolver, out (the HTTP response stream)
     * OUTPUT: out receives the .xlsx. With the 22 fragments on the local instance:
     *           merchant-venue 5 rows, merchant-details 2, category 4, offer-card 3, offer-cta 5, offer-detail 3.
     *         With no fragments at all: one sheet "report" holding
     *           "No content fragments found under /content/dam/aemcloudproject/cfs/offer-listing"
     *         (a workbook must have at least one sheet, otherwise Excel can't open it).
     */
    @Override
    public void scbWriteReport(ResourceResolver resolver, OutputStream out) throws IOException {
        // Model path -> its fragments, in the order they were found.
        // e.g. "/conf/.../models/offer-cta" -> [.../ctas/book-now, .../ctas/shop-online, ...]
        Map<String, List<ContentFragment>> fragmentsByModel = new LinkedHashMap<>();
        Resource folder = resolver.getResource(REPORT_PATH);
        if (folder != null) {
            scbFindFragments(folder, fragmentsByModel);
        }

        // try-with-resources: the in-memory workbook is closed (memory released) at the end.
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // Bold font for row 1; wrapped text so multi-line cells show one value per line.
            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);
            // Red bold for the headers of mandatory fields (Required in the model), e.g. offerID, offerSlug.
            CellStyle requiredHeaderStyle = workbook.createCellStyle();
            Font redBold = workbook.createFont();
            redBold.setBold(true);
            redBold.setColor(IndexedColors.RED.getIndex());
            requiredHeaderStyle.setFont(redBold);
            CellStyle wrapStyle = workbook.createCellStyle();
            wrapStyle.setWrapText(true);

            if (fragmentsByModel.isEmpty()) {
                Sheet sheet = workbook.createSheet("report");
                sheet.createRow(0).createCell(0).setCellValue("No content fragments found under " + REPORT_PATH);
            }
            for (String modelPath : scbSheetOrder(fragmentsByModel.keySet())) {
                scbWriteSheet(workbook, resolver, modelPath, fragmentsByModel.get(modelPath),
                        headerStyle, requiredHeaderStyle, wrapStyle);
            }
            // Serialises the workbook as .xlsx bytes into the response.
            workbook.write(out);
        }
    }

    /**
     * WHAT: Walks a folder and all its sub-folders and collects every Content Fragment, grouped by model.
     *
     * INPUT:  folder — e.g. /content/dam/aemcloudproject/cfs/offer-listing
     *                  (children: offers/, ctas/, merchants/, cards/, categories/, venues/, offer-cta/)
     *         fragmentsByModel — map to fill
     * OUTPUT: nothing returned; fragmentsByModel now holds e.g.
     *           /conf/.../models/merchant-venue -> [venues/central-mall-branch, venues/orchard-road-outlet, ...]
     *           /conf/.../models/offer-cta      -> [ctas/book-now, ctas/shop-online, ...]
     *         Non-fragment assets (images, the .xlsx files, ...) and jcr:content nodes are ignored.
     */
    private void scbFindFragments(Resource folder, Map<String, List<ContentFragment>> fragmentsByModel) {
        for (Resource child : folder.getChildren()) {
            // jcr:primaryType tells folders and assets apart, e.g. "sling:OrderedFolder" or "dam:Asset".
            String type = child.getValueMap().get("jcr:primaryType", String.class);
            if (FOLDER.equals(type) || ORDERED_FOLDER.equals(type)) {
                scbFindFragments(child, fragmentsByModel);      // go one level deeper, e.g. into ctas/
            } else if (ASSET.equals(type)) {
                // Only Content Fragments adapt; a normal asset (image, pdf) returns null.
                ContentFragment fragment = child.adaptTo(ContentFragment.class);
                // The model is stored at <fragment>/jcr:content/data/cq:model,
                // e.g. /conf/aemcloudproject/settings/dam/cfm/models/offer-cta
                Resource data = child.getChild("jcr:content/data");
                String modelPath = data == null ? null : data.getValueMap().get("cq:model", String.class);
                if (fragment != null && modelPath != null) {
                    fragmentsByModel.computeIfAbsent(modelPath, key -> new ArrayList<>()).add(fragment);
                }
            }
        }
    }

    /**
     * WHAT: Puts the models in the same sheet order as the import workbook.
     *
     * INPUT:  model paths in the order found, e.g. [.../offer-detail, .../offer-cta, .../merchant-venue, .../some-new-model]
     * OUTPUT: [.../merchant-venue, .../offer-cta, .../offer-detail, .../some-new-model]
     *         — known models in SHEET_ORDER, unknown ones after them in the order found.
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
     * WHAT: Writes one sheet: header row, then one row per fragment.
     *
     * INPUT:  modelPath "/conf/.../models/offer-cta", its 5 fragments
     * OUTPUT: nothing returned; the workbook gets sheet "offer-cta":
     *   row 1: | action | offerCtaSlug | label        | url                         | deeplink   |   (bold; offerCtaSlug in RED bold = mandatory)
     *   row 2: |        | book-now     | Book Now     | https://example.com/book    | app://book |
     *   ...
     *   row 6: |        | view-details | View Details | https://example.com/details |            |   (empty field -> empty cell)
     */
    private void scbWriteSheet(XSSFWorkbook workbook, ResourceResolver resolver, String modelPath,
                               List<ContentFragment> fragments, CellStyle headerStyle,
                               CellStyle requiredHeaderStyle, CellStyle wrapStyle) {
        // Sheet name = model name, e.g. "offer-cta" — exactly what the importer expects.
        Sheet sheet = workbook.createSheet(scbModelName(modelPath));
        // e.g. [offerCtaSlug, label, url, deeplink]
        List<String> fields = scbFieldNames(resolver, modelPath, fragments.get(0));
        // e.g. {offerCtaSlug} — these headers are written in red bold.
        Set<String> requiredFields = scbRequiredFields(resolver, modelPath);

        // Row 1: "action" in column A (plain bold), then the fields — red bold if mandatory.
        Row header = sheet.createRow(0);
        scbHeaderCell(header, 0, ACTION, headerStyle);
        for (int f = 0; f < fields.size(); f++) {
            String field = fields.get(f);
            scbHeaderCell(header, f + 1, field, requiredFields.contains(field) ? requiredHeaderStyle : headerStyle);
        }

        // Row 2 onwards: one fragment per row, sorted by the ID field if the model has one
        // (e.g. offer-detail by offerID: 10001, 10002, 10003). Column A (action) is left empty on purpose.
        int rowIndex = 1;
        for (ContentFragment fragment : scbSortById(fragments, fields)) {
            Row row = sheet.createRow(rowIndex++);
            for (int f = 0; f < fields.size(); f++) {
                ContentElement element = fragment.getElement(fields.get(f));
                // getValue().getValue() = the stored value: String, Long, Double, Boolean, Calendar or String[]
                Object value = element == null ? null : element.getValue().getValue();
                scbWriteCell(row.createCell(f + 1), value, wrapStyle);
            }
        }
        // Keep row 1 visible while scrolling.
        sheet.createFreezePane(0, 1);
    }

    /**
     * WHAT: Sorts a sheet's fragments by its ID field, smallest first. The ID field is the one named
     * "id" or ending in "ID" (any case): offerID, merchantID, ID, id. Sheets without one keep the
     * order the fragments were found in.
     *
     * INPUT:  fragments [premium-shopping-spree (10002), wellness-city-combo (10003), gourmet-dining-deal (10001)],
     *         fields    [offerID, offerTitle, offerSlug, ...]
     * OUTPUT: [gourmet-dining-deal (10001), premium-shopping-spree (10002), wellness-city-combo (10003)]
     *         - merchant-venue / offer-cta (no ID field) -> same list, unchanged order
     *         - a fragment whose ID is empty goes last
     */
    private List<ContentFragment> scbSortById(List<ContentFragment> fragments, List<String> fields) {
        String idField = null;
        for (String field : fields) {
            // "offerID", "merchantID", "ID", "id" match; "offerSlug", "categoryIconPath" don't.
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
        // Compare as numbers (10001 < 10002); fragments without an ID (Long.MAX_VALUE) end up last.
        sorted.sort(Comparator.comparingLong(fragment -> scbIdValue(fragment, sortField)));
        return sorted;
    }

    /**
     * WHAT: A fragment's ID as a number, for sorting.
     * INPUT:  gourmet-dining-deal, "offerID" -> OUTPUT: 10001
     *         a fragment with an empty or non-numeric ID -> Long.MAX_VALUE (sorts last)
     */
    private long scbIdValue(ContentFragment fragment, String idField) {
        ContentElement element = fragment.getElement(idField);
        Object value = element == null ? null : element.getValue().getValue();
        return value instanceof Number ? ((Number) value).longValue() : Long.MAX_VALUE;
    }

    /**
     * WHAT: Writes one stored value into a cell, in the format the importer reads back.
     *
     * INPUT -> CELL:
     *   null / empty String[]                         -> empty cell
     *   "book-now", "<p>Enjoy 20% off…</p>"           -> text, as stored
     *   10001 (Integer field), 1.2935 (Fraction field) -> number cell
     *   true / false (Boolean field)                  -> TRUE / FALSE
     *   Calendar 2026-10-01 00:00 +05:30              -> text "2026-10-01T00:00:00.000+05:30"
     *   String[] {".../dining", ".../fine-dining"}    -> text ".../dining⏎.../fine-dining" (wrapped, one per line)
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
     * WHAT: Formats a stored date the way the import workbook writes dates, keeping its offset.
     * INPUT:  Calendar 2026-10-01 00:00:00.000 at +05:30
     * OUTPUT: "2026-10-01T00:00:00.000+05:30"
     */
    private String scbFormatDate(Calendar calendar) {
        // The calendar's own offset at that moment, e.g. +05:30 (19800 seconds).
        int offsetSeconds = (calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)) / 1000;
        OffsetDateTime dateTime = calendar.toInstant().atOffset(ZoneOffset.ofTotalSeconds(offsetSeconds));
        return dateTime.format(DATE_FORMAT);
    }

    /**
     * WHAT: The model's field names, in the order defined in the model.
     *
     * INPUT:  "/conf/.../models/offer-cta" (plus one of its fragments as a fallback)
     * OUTPUT: [offerCtaSlug, label, url, deeplink]
     *         If the model can't be read (e.g. no read access to /conf), the fragment's own
     *         fields are used instead — same names, same order.
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
     * WHAT: The model's mandatory fields — those marked Required in the model editor.
     * The CF API doesn't expose this flag, so it is read from the model definition: each field is a
     * node under <model>/jcr:content/model/cq:dialog/content/items with name=<field> and required="on".
     *
     * INPUT:  "/conf/.../models/offer-detail"
     * OUTPUT: {offerID, offerTitle, offerSlug}
     *         For your other models: category {ID, offerCategorySlug}, merchant-details {merchantID, merchantSlug},
     *         merchant-venue {merchantVenueSlug}, offer-card {offerCardSlug}, offer-cta {offerCtaSlug}.
     *         Empty set if the model can't be read — then no header is red.
     */
    private Set<String> scbRequiredFields(ResourceResolver resolver, String modelPath) {
        Set<String> required = new HashSet<>();
        Resource items = resolver.getResource(modelPath + "/jcr:content/model/cq:dialog/content/items");
        if (items == null) {
            return required;
        }
        for (Resource item : items.getChildren()) {
            // e.g. name="offerID", required="on"
            String name = item.getValueMap().get("name", String.class);
            String flag = item.getValueMap().get("required", String.class);
            if (name != null && ("on".equals(flag) || "true".equals(flag))) {
                required.add(name);
            }
        }
        return required;
    }

    /**
     * WHAT: Writes one bold header cell.
     * INPUT:  row 1, column 2, "offerCtaSlug" -> OUTPUT: cell B1 = offerCtaSlug, bold
     */
    private void scbHeaderCell(Row header, int column, String text, CellStyle headerStyle) {
        Cell cell = header.createCell(column);
        cell.setCellValue(text);
        cell.setCellStyle(headerStyle);
    }

    /**
     * WHAT: Model name from a model path — also the sheet name.
     * INPUT:  "/conf/aemcloudproject/settings/dam/cfm/models/offer-cta"
     * OUTPUT: "offer-cta"
     */
    private String scbModelName(String modelPath) {
        return modelPath.substring(modelPath.lastIndexOf('/') + 1);
    }
}
