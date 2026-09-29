package com.aemcloudproject.core.cfimport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Matches the sheet's columns to the model's fields, ignoring case, and converts each row's
 * cells to typed {@link FieldValue}s.
 */
final class RowConverter {

    /** Model field name to the sheet column holding it, in model order. */
    private final Map<String, String> columnByField = new LinkedHashMap<>();
    private final Map<String, ModelField> fieldsByName = new LinkedHashMap<>();
    private final List<String> unknownColumns = new ArrayList<>();
    private final List<String> fieldsNotInSheet = new ArrayList<>();

    RowConverter(ModelDefinition model, ParsedSheet sheet) {
        Map<String, String> columnByLowerName = new LinkedHashMap<>();
        for (String column : sheet.getColumns()) {
            columnByLowerName.put(column.toLowerCase(Locale.ROOT), column);
        }
        for (ModelField field : model.getFields()) {
            String column = columnByLowerName.remove(field.getName().toLowerCase(Locale.ROOT));
            if (column == null) {
                fieldsNotInSheet.add(field.getName());
            } else {
                columnByField.put(field.getName(), column);
                fieldsByName.put(field.getName(), field);
            }
        }
        unknownColumns.addAll(columnByLowerName.values());
    }

    ConvertedRow convert(ParsedRow row) {
        Map<String, FieldValue> values = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> mapped : columnByField.entrySet()) {
            String fieldName = mapped.getKey();
            String text = row.get(mapped.getValue());
            if (text.isEmpty()) {
                values.put(fieldName, FieldValue.BLANK);
            } else if (FieldValue.CLEAR_TOKEN.equals(text)) {
                values.put(fieldName, FieldValue.CLEAR);
            } else {
                try {
                    values.put(fieldName, FieldValue.of(ValueConverter.convert(fieldsByName.get(fieldName), text)));
                } catch (ImportException e) {
                    problems.add(fieldName + ": " + e.getMessage());
                }
            }
        }
        return new ConvertedRow(row.getRowNumber(), values, problems);
    }

    /** Columns that match no field; their values would be lost. */
    List<String> getUnknownColumns() {
        return unknownColumns;
    }

    /** Fields with no column; rows leave them unchanged. */
    List<String> getFieldsNotInSheet() {
        return fieldsNotInSheet;
    }

    /** Sheet column to field name, where the case differs, e.g. offerCategoryslug to offerCategorySlug. */
    Map<String, String> getCaseCorrections() {
        Map<String, String> corrections = new LinkedHashMap<>();
        for (Map.Entry<String, String> mapped : columnByField.entrySet()) {
            if (!mapped.getKey().equals(mapped.getValue())) {
                corrections.put(mapped.getValue(), mapped.getKey());
            }
        }
        return corrections;
    }
}
