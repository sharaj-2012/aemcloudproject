package com.aemcloudproject.core.services.impl;

import com.aemcloudproject.core.dto.ScbSheetData;
import com.aemcloudproject.core.services.ScbWorkbookParserService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.osgi.service.component.annotations.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WHAT: Reads a CF Bulk Upload workbook (.xlsx) with the Apache POI library that AEM ships.
 *
 * CALL ORDER for one workbook:
 *   scbReadWorkbook(file, errors)
 *     ├─ scbOpenWorkbook(file)                  bytes -> POI workbook
 *     └─ for each sheet:
 *          ├─ scbReadHeaders(sheet)             row 1 -> {0=action, 1=offerCtaSlug, ...}
 *          └─ for each row 2..n:
 *               ├─ scbReadRow(row)              -> {action=CREATE, offerCtaSlug=book-now, ...}
 *               │    └─ scbSplitLines(text)     "/content/a\n/content/b" -> [/content/a, /content/b]
 *               └─ scbCheckAction(values)       CREATE/UPDATE check, adds to errors if wrong
 *
 * NUMBERING: POI counts rows and columns from 0; Excel shows rows from 1 and columns as letters.
 *   POI row 0 = Excel row 1 (headers), POI row 1 = Excel row 2 (first data row).
 *   POI column 0 = Excel column A, column 2 = C.
 */
@Component(service = ScbWorkbookParserService.class)
public class ScbWorkbookParserServiceImpl implements ScbWorkbookParserService {

    // Reserved header that says what to do with each row.
    private static final String ACTION = "action";
    private static final String CREATE = "CREATE";
    private static final String UPDATE = "UPDATE";
    // Cells starting with this are repository paths; multi-line ones are split into a list.
    private static final String CONTENT_PATH_PREFIX = "/content";

    /**
     * WHAT: Reads every sheet of the workbook into headers + rows, collecting problems on the way.
     *
     * INPUT:  file   — the .xlsx bytes (from the DAM asset's original rendition)
     *         errors — an empty list; problems are added to it
     *
     * OUTPUT: sheet name -> ScbSheetData, in tab order. For cf-import-combined.xlsx:
     *           "merchant-venue"   -> headers [action, merchantVenueSlug, name, ...], 5 rows
     *           "merchant-details" -> headers [merchantID, action, merchantSlug, ...], 2 rows
     *           ...
     *           "offer-detail"     -> headers [action, offerID, offerSlug, ...], 3 rows
     *         errors after the call, e.g. (only when something is wrong):
     *           Sheet "offer-cta": has no action column.
     *           Sheet "category", row 3: action "DELET" must be CREATE or UPDATE.
     *
     * THROWS: IOException when the file is not a readable .xlsx.
     */
    @Override
    public Map<String, ScbSheetData> scbReadWorkbook(InputStream file, List<String> errors) throws IOException {
        // LinkedHashMap keeps the sheets in tab order in the JSON.
        Map<String, ScbSheetData> sheets = new LinkedHashMap<>();

        // DataFormatter turns any cell into the text Excel shows:
        //   number 10001 -> "10001", number 1.2935 -> "1.2935", TRUE -> "TRUE", text -> same text.
        DataFormatter formatter = new DataFormatter();
        // For formula cells, use the result Excel saved in the file instead of the formula text:
        //   =UPPER(B3) showing "GOLD-CARD" -> "GOLD-CARD" (without this line it would be "UPPER(B3)").
        formatter.setUseCachedValuesForFormulaCells(true);

        // try-with-resources: the workbook is closed (memory released) at the end of the block.
        try (XSSFWorkbook workbook = scbOpenWorkbook(file)) {
            // Loops over the sheets in tab order: merchant-venue, merchant-details, category, ...
            for (Sheet sheet : workbook) {
                ScbSheetData data = new ScbSheetData();

                // Row 1 -> column index -> header.
                // e.g. "merchant-details": {0=merchantID, 1=action, 2=merchantSlug, 3=merchantName, 4=merchantLogo, 5=merchantVenues}
                Map<Integer, String> headerByColumn = scbReadHeaders(sheet, formatter, errors);
                // headers for the JSON: [merchantID, action, merchantSlug, merchantName, merchantLogo, merchantVenues]
                data.getHeaders().addAll(headerByColumn.values());

                // Every sheet needs an action column. One error per sheet if it's missing
                // (an empty sheet with no headers at all is left alone).
                boolean hasActionColumn = headerByColumn.containsValue(ACTION);
                if (!headerByColumn.isEmpty() && !hasActionColumn) {
                    errors.add("Sheet \"" + sheet.getSheetName() + "\": has no action column.");
                }

                // Data rows: POI index 1 .. last = Excel rows 2 .. last.
                // getLastRowNum() = index of the last row that exists, e.g. 2 for "merchant-details" (Excel rows 2-3).
                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    // e.g. {merchantID=301, action=CREATE, merchantSlug=fine-eats-sg, merchantName=Fine Eats Singapore,
                    //       merchantVenues=[/content/dam/.../central-mall-branch, /content/dam/.../orchard-road-outlet]}
                    Map<String, Object> values = scbReadRow(sheet.getRow(r), headerByColumn, formatter);
                    if (values.isEmpty()) {
                        continue;   // blank row -> skipped
                    }
                    // Without an action column the sheet error above says it all; don't repeat it per row.
                    if (hasActionColumn) {
                        // r + 1 = Excel row number for the message (POI index 1 -> "row 2").
                        scbCheckAction(sheet.getSheetName(), r + 1, values, errors);
                    }
                    data.getRows().add(values);
                }
                sheets.put(sheet.getSheetName(), data);
            }
        }
        return sheets;
    }

    /**
     * WHAT: Reads row 1 (the headers) of one sheet.
     *
     * INPUT:  sheet, e.g. "offer-cta" whose row 1 is | action | offerCtaSlug | label | url | deeplink |
     *
     * OUTPUT: column index -> header, in column order:
     *           {0=action, 1=offerCtaSlug, 2=label, 3=url, 4=deeplink}
     *         - blank header cells are skipped: | action | (blank) | url | -> {0=action, 2=url}
     *         - a header with a space is NOT returned; an error is added instead:
     *           | action | Offer Title | -> {0=action} and errors += Sheet "offer-detail", column B: header "Offer Title" must be a single word.
     *         - no row 1 at all -> {} (empty map)
     */
    private Map<Integer, String> scbReadHeaders(Sheet sheet, DataFormatter formatter, List<String> errors) {
        Map<Integer, String> headerByColumn = new LinkedHashMap<>();
        // POI row 0 = Excel row 1. Null if the sheet has nothing in row 1.
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return headerByColumn;
        }
        // Loops over the cells that exist in row 1, left to right.
        for (Cell cell : headerRow) {
            // " label " -> "label"
            String header = formatter.formatCellValue(cell).trim();
            if (header.isEmpty()) {
                continue;
            }
            // \s = any whitespace (space, tab, line break). "Offer Title" matches -> error.
            if (header.matches(".*\\s.*")) {
                // convertNumToColString: 0 -> "A", 2 -> "C", 26 -> "AA"
                errors.add("Sheet \"" + sheet.getSheetName() + "\", column "
                        + CellReference.convertNumToColString(cell.getColumnIndex())
                        + ": header \"" + header + "\" must be a single word.");
                continue;
            }
            headerByColumn.put(cell.getColumnIndex(), header);
        }
        return headerByColumn;
    }

    /**
     * WHAT: Reads one data row into header -> value. A value is a String, or a List of Strings
     * for a multi-line cell starting with /content (see scbSplitLines).
     *
     * INPUT:  row            — e.g. Excel row 2 of "offer-cta": | CREATE | book-now | Book Now | https://example.com/book | app://book |
     *         headerByColumn — from scbReadHeaders, e.g. {0=action, 1=offerCtaSlug, 2=label, 3=url, 4=deeplink}
     *
     * OUTPUT: {action=CREATE, offerCtaSlug=book-now, label=Book Now, url=https://example.com/book, deeplink=app://book}
     *         - multi-value cell, e.g. "merchant-details" row 2, merchantVenues:
     *             merchantVenues=[/content/dam/aemcloudproject/cfs/offer-listing/venues/central-mall-branch,
     *                             /content/dam/aemcloudproject/cfs/offer-listing/venues/orchard-road-outlet]
     *         - blank cells are left out: row 6 (view-details) has no deeplink -> 4 entries, not 5
     *         - cells under a blank or invalid header are left out
     *         - {} (empty map) for a blank row or a row that doesn't exist
     */
    private Map<String, Object> scbReadRow(Row row, Map<Integer, String> headerByColumn, DataFormatter formatter) {
        // LinkedHashMap keeps column order in the JSON.
        Map<String, Object> values = new LinkedHashMap<>();
        // Null = the row was never touched in Excel.
        if (row == null) {
            return values;
        }
        for (Cell cell : row) {
            // Column 1 -> "offerCtaSlug"; a column with no (valid) header -> null.
            String header = headerByColumn.get(cell.getColumnIndex());
            // Cell as text, outer spaces removed: 10001 -> "10001", "  Book Now " -> "Book Now".
            String value = formatter.formatCellValue(cell).trim();
            if (header != null && !value.isEmpty()) {
                // "book-now" -> "book-now"; "/content/a\n/content/b" -> [/content/a, /content/b]
                values.put(header, scbSplitLines(value));
            }
        }
        return values;
    }

    /**
     * WHAT: Turns a multi-line path cell into a list; leaves every other cell as it is.
     * Only text starting with /content is split, so multi-line HTML (e.g. address, offerDescription,
     * which start with "<p>") is never broken up.
     *
     * INPUT -> OUTPUT:
     *   "/content/dam/.../venues/central-mall-branch\n/content/dam/.../venues/orchard-road-outlet"
     *        -> List [/content/dam/.../venues/central-mall-branch, /content/dam/.../venues/orchard-road-outlet]
     *   "/content/dam/.../merchants/fine-eats-sg"        -> String "/content/dam/.../merchants/fine-eats-sg"   (one line)
     *   "/content/dam/.../cards/gold-card\n"             -> String "/content/dam/.../cards/gold-card"          (blank line dropped)
     *   "<p>1 Central Mall,</p>\n<p>Singapore</p>"      -> String, unchanged                               (doesn't start with /content)
     *   "book-now"                                       -> String "book-now"
     */
    private Object scbSplitLines(String value) {
        if (!value.startsWith(CONTENT_PATH_PREFIX)) {
            return value;
        }
        List<String> lines = new ArrayList<>();
        // \R = any line break (\n, \r\n, \r), so Windows and Mac Excel files behave the same.
        for (String line : value.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        // value starts with /content, so the first line is never blank and lines has at least one entry.
        return lines.size() > 1 ? lines : lines.get(0);
    }

    /**
     * WHAT: Checks the row's action and normalises it to upper case.
     *
     * INPUT:  sheetName "offer-cta", rowNumber 2 (Excel), values {action=create, offerCtaSlug=book-now, ...}
     *
     * OUTPUT: nothing returned. One of:
     *         - action "create" / "Create" / "CREATE" -> values now holds action=CREATE (same for UPDATE)
     *         - action cell blank -> errors += Sheet "offer-cta", row 2: action is missing.
     *         - action "DELETE"   -> errors += Sheet "offer-cta", row 2: action "DELETE" must be CREATE or UPDATE.
     */
    private void scbCheckAction(String sheetName, int rowNumber, Map<String, Object> values, List<String> errors) {
        // Null when the action cell is blank (scbReadRow leaves blank cells out).
        // Always a String: an action never starts with /content, so scbSplitLines never turns it into a list.
        String action = (String) values.get(ACTION);
        if (action == null) {
            errors.add("Sheet \"" + sheetName + "\", row " + rowNumber + ": action is missing.");
            return;
        }
        String upper = action.toUpperCase();
        if (!CREATE.equals(upper) && !UPDATE.equals(upper)) {
            // The message shows what the author typed ("DELET"), not the upper-cased form.
            errors.add("Sheet \"" + sheetName + "\", row " + rowNumber + ": action \"" + action
                    + "\" must be CREATE or UPDATE.");
            return;
        }
        // Store the normalised form so later steps only compare against "CREATE" / "UPDATE".
        values.put(ACTION, upper);
    }

    /**
     * WHAT: Opens the .xlsx with POI.
     *
     * INPUT:  file — the .xlsx bytes
     * OUTPUT: the opened workbook, ready to loop over its sheets
     * THROWS: IOException("Not a valid .xlsx workbook") for anything that isn't an .xlsx
     *         (a .txt or .jpg renamed to .xlsx, an old .xls, a password-protected file).
     *         POI reports those with RuntimeExceptions; converting them to IOException lets
     *         the servlet handle every "can't read this file" case in one catch.
     */
    private XSSFWorkbook scbOpenWorkbook(InputStream file) throws IOException {
        try {
            return new XSSFWorkbook(file);
        } catch (RuntimeException e) {
            throw new IOException("Not a valid .xlsx workbook", e);
        }
    }
}
