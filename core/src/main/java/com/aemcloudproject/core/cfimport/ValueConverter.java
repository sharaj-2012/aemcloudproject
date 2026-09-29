package com.aemcloudproject.core.cfimport;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

/**
 * Converts the text of one cell to the Java value a content fragment field of that type holds.
 */
final class ValueConverter {

    /** The date format import files use. STRICT rejects dates that don't exist, like 2026-02-30. */
    static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX").withResolverStyle(ResolverStyle.STRICT);
    private static final String DATE_EXAMPLE = "2026-10-01T00:00:00.000+05:30";

    private ValueConverter() {
    }

    /**
     * @param text a non-blank cell, already trimmed
     * @return the value, or a List of values for a multi-value field (one per line)
     * @throws ImportException with a message for the author when the text doesn't fit the field
     */
    static Object convert(ModelField field, String text) throws ImportException {
        List<String> lines = lines(text);
        if (field.isMultiple()) {
            List<Object> values = new ArrayList<>();
            for (String line : lines) {
                values.add(single(field, line));
            }
            return values;
        }
        boolean reference = field.getType() == FieldType.FRAGMENT_REFERENCE
                || field.getType() == FieldType.CONTENT_REFERENCE;
        if (reference && lines.size() > 1) {
            throw new ImportException("takes one reference, but the cell has " + lines.size() + " lines.");
        }
        return single(field, text);
    }

    private static Object single(ModelField field, String text) throws ImportException {
        switch (field.getType()) {
            case TEXT:
                if (field.getMaxLength() != null && text.length() > field.getMaxLength()) {
                    throw new ImportException("is " + text.length() + " characters; the model allows "
                            + field.getMaxLength() + ".");
                }
                return text;
            case MULTILINE_TEXT:
            case CONTENT_REFERENCE:
            case FRAGMENT_REFERENCE:
                // Whether referenced items exist is checked in validation, not here.
                return text;
            case LONG:
                try {
                    return new BigDecimal(text).longValueExact();
                } catch (NumberFormatException | ArithmeticException e) {
                    throw new ImportException("'" + text + "' is not a whole number.");
                }
            case DOUBLE:
                try {
                    return new BigDecimal(text).doubleValue();
                } catch (NumberFormatException e) {
                    throw new ImportException("'" + text + "' is not a number.");
                }
            case BOOLEAN:
                return toBoolean(text);
            case DATE:
                try {
                    // Keeps the offset written in the cell: +05:30 is stored as +05:30.
                    return GregorianCalendar.from(OffsetDateTime.parse(text, DATE_FORMAT).toZonedDateTime());
                } catch (DateTimeParseException e) {
                    throw new ImportException("'" + text + "' is not a date like " + DATE_EXAMPLE
                            + " (yyyy-MM-dd'T'HH:mm:ss.SSSXXX).");
                }
            default:
                throw new ImportException("has type " + field.getValueType() + ", which the importer can't write yet.");
        }
    }

    private static Boolean toBoolean(String text) throws ImportException {
        switch (text.toLowerCase(Locale.ROOT)) {
            case "1":
            case "true":
            case "yes":
                return Boolean.TRUE;
            case "0":
            case "false":
            case "no":
                return Boolean.FALSE;
            default:
                throw new ImportException("'" + text + "' is not a yes/no value; use 1 or 0.");
        }
    }

    private static List<String> lines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (!line.trim().isEmpty()) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    /** How a converted value is shown in the report, so its type is visible. */
    static String display(Object value) {
        if (value instanceof List) {
            List<String> items = new ArrayList<>();
            for (Object item : (List<?>) value) {
                items.add(display(item));
            }
            return "[" + String.join(", ", items) + "]";
        }
        if (value instanceof GregorianCalendar) {
            return ((GregorianCalendar) value).toZonedDateTime().format(DATE_FORMAT);
        }
        if (value instanceof Calendar) {
            return ((Calendar) value).toInstant().toString();
        }
        if (value instanceof String) {
            String text = ((String) value).replace("\n", "⏎");
            if (text.length() <= 40) {
                return "\"" + text + "\"";
            }
            if (text.startsWith("/")) {
                // Keep the end of a long path: the folder and name are what the author checks.
                String[] segments = text.split("/");
                return "\"…/" + segments[segments.length - 2] + "/" + segments[segments.length - 1] + "\"";
            }
            return "\"" + text.substring(0, 39) + "…\"";
        }
        return String.valueOf(value);
    }
}
