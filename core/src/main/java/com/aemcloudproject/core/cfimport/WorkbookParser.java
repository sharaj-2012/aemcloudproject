package com.aemcloudproject.core.cfimport;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the first worksheet of an .xlsx file into text: row 1 holds the column names, every
 * later non-empty row is a data row. It knows nothing about content fragment models; matching
 * columns to fields and converting values to field types happen in later steps.
 */
final class WorkbookParser {

    /** Excel dates have no time zone, so they are written as local date-times. */
    private static final DateTimeFormatter EXCEL_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private WorkbookParser() {
    }

    static ParsedSheet parse(byte[] content) throws ImportException {
        // XSSFWorkbook directly: WorkbookFactory finds its providers through ServiceLoader,
        // which does not see them inside OSGi.
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new ImportException("The workbook has no worksheets.");
            }
            return parse(workbook.getSheetAt(0));
        } catch (IOException | RuntimeException e) {
            // POI reports a file that isn't a real .xlsx with assorted runtime exceptions.
            throw new ImportException("The file is not a readable .xlsx workbook: " + e.getMessage(), e);
        }
    }

    private static ParsedSheet parse(Sheet sheet) throws ImportException {
        Map<Integer, String> columnsByIndex = readHeader(sheet);
        List<ParsedRow> rows = new ArrayList<>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            ParsedRow row = readRow(sheet.getRow(r), r, columnsByIndex);
            if (row != null) {
                rows.add(row);
            }
        }
        if (rows.isEmpty()) {
            throw new ImportException("Sheet '" + sheet.getSheetName() + "' has no data rows below the column names.");
        }
        return new ParsedSheet(sheet.getSheetName(), new ArrayList<>(columnsByIndex.values()), rows);
    }

    /** Column index to column name. Columns with an empty name are left out. */
    private static Map<Integer, String> readHeader(Sheet sheet) throws ImportException {
        Row header = sheet.getRow(0);
        Map<Integer, String> columns = new LinkedHashMap<>();
        Map<String, Integer> seen = new HashMap<>();
        if (header != null) {
            for (int c = 0; c < header.getLastCellNum(); c++) {
                String name = text(header.getCell(c));
                if (name.isEmpty()) {
                    continue;
                }
                Integer earlier = seen.put(name.toLowerCase(Locale.ROOT), c);
                if (earlier != null) {
                    throw new ImportException("Column name '" + name + "' appears twice, in columns "
                            + letter(earlier) + " and " + letter(c) + ".");
                }
                columns.put(c, name);
            }
        }
        if (columns.isEmpty()) {
            throw new ImportException("Sheet '" + sheet.getSheetName() + "' has no column names in row 1.");
        }
        return columns;
    }

    /** The row's cells by column name, or null if every cell is blank. */
    private static ParsedRow readRow(Row row, int index, Map<Integer, String> columns) throws ImportException {
        if (row == null) {
            return null;
        }
        Map<String, String> cells = new LinkedHashMap<>();
        boolean empty = true;
        for (Map.Entry<Integer, String> column : columns.entrySet()) {
            String value = text(row.getCell(column.getKey()));
            cells.put(column.getValue(), value);
            empty &= value.isEmpty();
        }
        // A value in a column without a name would be lost silently, so it is an error.
        for (int c = 0; c < row.getLastCellNum(); c++) {
            if (!columns.containsKey(c) && !text(row.getCell(c)).isEmpty()) {
                throw new ImportException("Cell " + letter(c) + (index + 1)
                        + " has a value, but column " + letter(c) + " has no name in row 1.");
            }
        }
        return empty ? null : new ParsedRow(index + 1, cells);
    }

    /** The cell as text: what the author typed, not how Excel formats it for display. */
    static String text(Cell cell) throws ImportException {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        switch (type) {
            case STRING:
                // Alt+Enter is \n; keep line breaks, drop surrounding spaces.
                return cell.getStringCellValue().replace("\r\n", "\n").replace('\r', '\n').trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().format(EXCEL_DATE);
                }
                // 101.0 becomes "101" and 103.838 stays "103.838", with no scientific notation.
                return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case ERROR:
                throw new ImportException("Cell " + cell.getAddress() + " has the Excel error "
                        + FormulaError.forInt(cell.getErrorCellValue()).getString() + ".");
            default:
                return "";
        }
    }

    private static String letter(int columnIndex) {
        return CellReference.convertNumToColString(columnIndex);
    }
}
