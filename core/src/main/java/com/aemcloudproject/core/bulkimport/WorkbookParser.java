package com.aemcloudproject.core.bulkimport;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads every tab of an offer workbook (.xlsx) into typed rows.
 *
 * <p>Conventions it accepts, matching the sample workbook:</p>
 * <ul>
 *   <li>Headers may carry notes after the property name, e.g.
 *       {@code offerCategories (fragment reference) BasePath -> ...}; only the text before the first
 *       bracket or line break is used, and it is matched to model properties ignoring case.</li>
 *   <li>Multi-value reference cells hold slugs separated by commas, line breaks (Alt+Enter) or pipes.</li>
 *   <li>A row whose slug (and, for offers, offerID) is blank continues the row above it, adding its
 *       values to that row's multi-value columns.</li>
 *   <li>{@code []} in a reference cell clears the field on update.</li>
 *   <li>Dates may be real Excel dates or text in {@code d/M/yy H:mm}, {@code d/M/yyyy H:mm}
 *       or ISO-8601 form; text without an offset is read in the configured zone.</li>
 * </ul>
 * Parse problems are recorded against the row rather than thrown, so one bad cell does not
 * hide the rest of the workbook from the report.
 */
public final class WorkbookParser {

    static final String CLEAR = "[]";

    private static final Pattern LIST_SEPARATOR = Pattern.compile("[,\\n|]");

    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = Arrays.asList(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            strict("uuuu-MM-dd H:mm[:ss]"),
            strict("d/M/uu H:mm[:ss]"),
            strict("d/M/uuuu H:mm[:ss]"));

    private static final List<DateTimeFormatter> DATE_FORMATS = Arrays.asList(
            DateTimeFormatter.ISO_LOCAL_DATE,
            strict("d/M/uu"),
            strict("d/M/uuuu"));

    private final ZoneId zone;
    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);
    private FormulaEvaluator evaluator;

    public WorkbookParser(ZoneId zone) {
        this.zone = zone;
    }

    public ParsedWorkbook parse(InputStream in) throws IOException {
        ParsedWorkbook result = new ParsedWorkbook();
        try (XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (Sheet sheet : workbook) {
                ModelSpec model = ModelSpec.forSheet(sheet.getSheetName());
                if (model == null) {
                    result.addIssue(ReportRow.issue(Outcome.WARNING, sheet.getSheetName(), 0,
                            "Sheet not recognised and ignored. Expected one of: " + expectedSheets()));
                } else if (result.hasSheet(model)) {
                    result.addIssue(ReportRow.issue(Outcome.WARNING, sheet.getSheetName(), 0,
                            "A second sheet for " + model.getModelName() + " was ignored"));
                } else {
                    parseSheet(sheet, model, result);
                }
            }
        }
        return result;
    }

    private void parseSheet(Sheet sheet, ModelSpec model, ParsedWorkbook result) {
        String sheetName = sheet.getSheetName();
        result.markSheetSeen(model);
        Row header = sheet.getRow(sheet.getFirstRowNum());
        if (header == null) {
            result.addIssue(ReportRow.issue(Outcome.WARNING, sheetName, 0, "Sheet is empty"));
            return;
        }

        Map<Integer, FieldSpec> columns = new LinkedHashMap<>();
        for (Cell cell : header) {
            String name = headerName(text(cell));
            if (name.isEmpty()) {
                continue;
            }
            FieldSpec field = model.getField(name);
            if (field == null) {
                result.addIssue(ReportRow.issue(Outcome.WARNING, sheetName, header.getRowNum() + 1,
                        "Column '" + name + "' matches no property of model " + model.getModelName() + " and was ignored"));
            } else if (columns.containsValue(field)) {
                result.addIssue(ReportRow.issue(Outcome.WARNING, sheetName, header.getRowNum() + 1,
                        "Duplicate column '" + name + "' was ignored"));
            } else {
                columns.put(cell.getColumnIndex(), field);
            }
        }
        if (columnOf(columns, model.getSlugProperty()) == null) {
            result.addIssue(ReportRow.issue(Outcome.ERROR, sheetName, header.getRowNum() + 1,
                    "No '" + model.getSlugProperty() + "' column, so rows cannot be identified; sheet skipped"));
            return;
        }

        ParsedRow current = null;
        for (int r = header.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || isBlank(row, columns)) {
                continue;
            }
            int excelRow = r + 1;
            if (isContinuation(row, columns, model)) {
                if (current == null) {
                    result.addIssue(ReportRow.issue(Outcome.WARNING, sheetName, excelRow,
                            "Row has no " + model.getSlugProperty() + " and follows no other row; ignored"));
                } else {
                    appendContinuation(current, row, columns, excelRow);
                }
                continue;
            }
            current = new ParsedRow(model, sheetName, excelRow);
            for (Map.Entry<Integer, FieldSpec> column : columns.entrySet()) {
                Cell cell = row.getCell(column.getKey());
                if (!isBlank(cell)) {
                    read(current, column.getValue(), cell);
                }
            }
            result.add(current);
        }
    }

    private boolean isContinuation(Row row, Map<Integer, FieldSpec> columns, ModelSpec model) {
        Integer slugColumn = columnOf(columns, model.getSlugProperty());
        Integer idColumn = model.getIdProperty() == null ? null : columnOf(columns, model.getIdProperty());
        return isBlank(row.getCell(slugColumn)) && (idColumn == null || isBlank(row.getCell(idColumn)));
    }

    @SuppressWarnings("unchecked")
    private void appendContinuation(ParsedRow current, Row row, Map<Integer, FieldSpec> columns, int excelRow) {
        for (Map.Entry<Integer, FieldSpec> column : columns.entrySet()) {
            Cell cell = row.getCell(column.getKey());
            if (isBlank(cell)) {
                continue;
            }
            FieldSpec field = column.getValue();
            if (field.getType() == FieldType.FRAGMENTS) {
                List<String> slugs = (List<String>) current.getValues()
                        .computeIfAbsent(field.getProperty(), k -> new ArrayList<String>());
                slugs.addAll(readList(cell));
            } else {
                current.getWarnings().add("Row " + excelRow + ": value in '" + field.getProperty()
                        + "' ignored; continuation rows only add to multi-value reference columns");
            }
        }
    }

    private void read(ParsedRow row, FieldSpec field, Cell cell) {
        String property = field.getProperty();
        try {
            switch (field.getType()) {
                case LONG:
                    row.getValues().put(property, readLong(cell));
                    break;
                case DOUBLE:
                    row.getValues().put(property, readDouble(cell));
                    break;
                case BOOLEAN:
                    row.getValues().put(property, readBoolean(cell));
                    break;
                case DATETIME:
                    row.getValues().put(property, readDateTime(cell));
                    break;
                case FRAGMENT:
                    List<String> slugs = readList(cell);
                    if (slugs.size() > 1) {
                        throw new IllegalArgumentException("only one value allowed, got " + slugs);
                    }
                    row.getValues().put(property, slugs);
                    break;
                case FRAGMENTS:
                    row.getValues().put(property, readList(cell));
                    break;
                default:
                    row.getValues().put(property, text(cell).trim());
                    break;
            }
        } catch (IllegalArgumentException | ArithmeticException e) {
            row.getErrors().add("'" + property + "': " + e.getMessage());
        }
    }

    private List<String> readList(Cell cell) {
        String raw = text(cell).trim();
        if (CLEAR.equals(raw)) {
            return new ArrayList<>();
        }
        return LIST_SEPARATOR.splitAsStream(raw)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Long readLong(Cell cell) {
        if (typeOf(cell) == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (value != Math.rint(value)) {
                throw new IllegalArgumentException("expected a whole number, got " + value);
            }
            return (long) value;
        }
        String raw = text(cell).trim();
        try {
            return new BigDecimal(raw).longValueExact();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("expected a whole number, got '" + raw + "'");
        }
    }

    private Double readDouble(Cell cell) {
        if (typeOf(cell) == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        String raw = text(cell).trim();
        try {
            return Double.valueOf(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("expected a number, got '" + raw + "'");
        }
    }

    private Boolean readBoolean(Cell cell) {
        CellType type = typeOf(cell);
        if (type == CellType.BOOLEAN) {
            return cell.getBooleanCellValue();
        }
        String raw = type == CellType.NUMERIC ? text(cell) : text(cell).trim().toLowerCase(Locale.ROOT);
        switch (raw) {
            case "1": case "true": case "yes": case "y":
                return Boolean.TRUE;
            case "0": case "false": case "no": case "n":
                return Boolean.FALSE;
            default:
                throw new IllegalArgumentException("expected 1/0, true/false or yes/no, got '" + raw + "'");
        }
    }

    private Calendar readDateTime(Cell cell) {
        if (typeOf(cell) == CellType.NUMERIC) {
            if (!DateUtil.isCellDateFormatted(cell)) {
                throw new IllegalArgumentException("expected a date, got the number " + cell.getNumericCellValue());
            }
            return GregorianCalendar.from(cell.getLocalDateTimeCellValue().atZone(zone));
        }
        return parseDate(text(cell).trim(), zone);
    }

    /**
     * Parses a text date. Package-visible so the accepted formats can be tested directly.
     */
    static Calendar parseDate(String raw, ZoneId zone) {
        try {
            return GregorianCalendar.from(OffsetDateTime.parse(raw).toZonedDateTime());
        } catch (DateTimeParseException ignored) {
            // no explicit offset; try the local formats below
        }
        for (DateTimeFormatter format : DATE_TIME_FORMATS) {
            try {
                return GregorianCalendar.from(LocalDateTime.parse(raw, format).atZone(zone));
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return GregorianCalendar.from(ZonedDateTime.of(LocalDate.parse(raw, format).atStartOfDay(), zone));
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        throw new IllegalArgumentException("unrecognised date '" + raw
                + "'; use dd/MM/yy HH:mm, dd/MM/yyyy HH:mm or yyyy-MM-ddTHH:mm:ss");
    }

    /**
     * Cell text as displayed, except whole numbers, which are written out in full so long
     * codes are not shown in scientific notation.
     */
    private String text(Cell cell) {
        if (cell == null) {
            return "";
        }
        if (typeOf(cell) == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            double value = cell.getNumericCellValue();
            if (value == Math.rint(value) && Math.abs(value) < 1e15) {
                return String.valueOf((long) value);
            }
            return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        }
        return formatter.formatCellValue(cell, evaluator);
    }

    private static CellType typeOf(Cell cell) {
        CellType type = cell.getCellType();
        return type == CellType.FORMULA ? cell.getCachedFormulaResultType() : type;
    }

    private boolean isBlank(Cell cell) {
        return cell == null || typeOf(cell) == CellType.BLANK || text(cell).trim().isEmpty();
    }

    private boolean isBlank(Row row, Map<Integer, FieldSpec> columns) {
        return columns.keySet().stream().allMatch(c -> isBlank(row.getCell(c)));
    }

    private static Integer columnOf(Map<Integer, FieldSpec> columns, String property) {
        return columns.entrySet().stream()
                .filter(e -> e.getValue().getProperty().equals(property))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    static String headerName(String raw) {
        return raw.split("[(\\r\\n]", 2)[0].trim();
    }

    private static String expectedSheets() {
        return Arrays.stream(ModelSpec.values()).map(ModelSpec::getSheetName).collect(Collectors.joining(", "));
    }

    private static DateTimeFormatter strict(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    }
}
