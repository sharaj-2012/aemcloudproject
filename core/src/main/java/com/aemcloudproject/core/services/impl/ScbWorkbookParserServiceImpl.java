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
 * Reads a CF bulk upload workbook with Apache POI.
 */
@Component(service = ScbWorkbookParserService.class)
public class ScbWorkbookParserServiceImpl implements ScbWorkbookParserService {

    private static final String ACTION = "action";
    private static final String CREATE = "CREATE";
    private static final String UPDATE = "UPDATE";
    private static final String CONTENT_PATH_PREFIX = "/content";

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, ScbSheetData> scbReadWorkbook(InputStream file, List<String> errors) throws IOException {
        Map<String, ScbSheetData> sheets = new LinkedHashMap<>();

        DataFormatter formatter = new DataFormatter();
        formatter.setUseCachedValuesForFormulaCells(true);

        try (XSSFWorkbook workbook = scbOpenWorkbook(file)) {
            for (Sheet sheet : workbook) {
                ScbSheetData data = new ScbSheetData();

                Map<Integer, String> headerByColumn = scbReadHeaders(sheet, formatter, errors);
                data.getHeaders().addAll(headerByColumn.values());

                boolean hasActionColumn = headerByColumn.containsValue(ACTION);
                if (!headerByColumn.isEmpty() && !hasActionColumn) {
                    errors.add("Sheet \"" + sheet.getSheetName() + "\": has no action column.");
                }

                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Map<String, Object> values = scbReadRow(sheet.getRow(r), headerByColumn, formatter);
                    if (!values.containsKey(ACTION)) {
                        continue;
                    }
                    scbCheckAction(sheet.getSheetName(), r + 1, values, errors);
                    data.getRows().add(values);
                    data.getRowNumbers().add(r + 1);
                }
                sheets.put(sheet.getSheetName(), data);
            }
        }
        return sheets;
    }

    /**
     * Reads the header row of a sheet. Blank headers are skipped; headers containing
     * whitespace are skipped and reported as errors.
     *
     * @param sheet     the sheet to read
     * @param formatter formats cells as the text Excel shows
     * @param errors    receives an error for every invalid header
     * @return the valid headers keyed by 0-based column index, in column order
     */
    private Map<Integer, String> scbReadHeaders(Sheet sheet, DataFormatter formatter, List<String> errors) {
        Map<Integer, String> headerByColumn = new LinkedHashMap<>();
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return headerByColumn;
        }
        for (Cell cell : headerRow) {
            String header = formatter.formatCellValue(cell).trim();
            if (header.isEmpty()) {
                continue;
            }
            if (header.matches(".*\\s.*")) {
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
     * Reads one data row. Blank cells and cells under a missing or invalid header are left out.
     *
     * @param row            the row to read, may be {@code null}
     * @param headerByColumn the headers keyed by column index
     * @param formatter      formats cells as the text Excel shows
     * @return the cell values keyed by header; empty for a blank or missing row
     */
    private Map<String, Object> scbReadRow(Row row, Map<Integer, String> headerByColumn, DataFormatter formatter) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (row == null) {
            return values;
        }
        for (Cell cell : row) {
            String header = headerByColumn.get(cell.getColumnIndex());
            String value = formatter.formatCellValue(cell).trim();
            if (header != null && !value.isEmpty()) {
                values.put(header, scbSplitLines(value));
            }
        }
        return values;
    }

    /**
     * Splits a multi-line cell into a list when it starts with {@code /content}.
     *
     * @param value the trimmed cell text
     * @return a {@code List<String>} of non-blank lines for a multi-line path cell,
     *         otherwise the single line or the unchanged text
     */
    private Object scbSplitLines(String value) {
        if (!value.startsWith(CONTENT_PATH_PREFIX)) {
            return value;
        }
        List<String> lines = new ArrayList<>();
        for (String line : value.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return lines.size() > 1 ? lines : lines.get(0);
    }

    /**
     * Checks that the row's action is CREATE or UPDATE (any case) and stores it in upper case.
     *
     * @param sheetName the sheet name, for the error message
     * @param rowNumber the Excel row number, for the error message
     * @param values    the row values; the action is replaced by its upper-case form
     * @param errors    receives an error if the action is invalid
     */
    private void scbCheckAction(String sheetName, int rowNumber, Map<String, Object> values, List<String> errors) {
        String action = (String) values.get(ACTION);
        String upper = action.toUpperCase();
        if (!CREATE.equals(upper) && !UPDATE.equals(upper)) {
            errors.add("Sheet \"" + sheetName + "\", row " + rowNumber + ": action \"" + action
                    + "\" must be CREATE or UPDATE.");
            return;
        }
        values.put(ACTION, upper);
    }

    /**
     * Opens the .xlsx with POI.
     *
     * @param file the .xlsx content
     * @return the opened workbook
     * @throws IOException if the content is not a readable .xlsx
     */
    private XSSFWorkbook scbOpenWorkbook(InputStream file) throws IOException {
        try {
            return new XSSFWorkbook(file);
        } catch (RuntimeException e) {
            throw new IOException("Not a valid .xlsx workbook", e);
        }
    }
}
