package com.aemcloudproject.core.bulkimport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One logical workbook row, with any continuation rows already folded in.
 * Values are typed per {@link FieldType}: String, Long, Double, Boolean, Calendar,
 * or a List of slugs for both reference types (empty means clear). A property absent from the map
 * was blank in the workbook and leaves the fragment's existing value alone.
 */
public final class ParsedRow {

    private final ModelSpec model;
    private final String sheet;
    private final int rowNumber;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    ParsedRow(ModelSpec model, String sheet, int rowNumber) {
        this.model = model;
        this.sheet = sheet;
        this.rowNumber = rowNumber;
    }

    public ModelSpec getModel() {
        return model;
    }

    public String getSheet() {
        return sheet;
    }

    /**
     * @return the 1-based row number as shown in Excel
     */
    public int getRowNumber() {
        return rowNumber;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public String getSlug() {
        return (String) values.get(model.getSlugProperty());
    }

    public Long getId() {
        return model.getIdProperty() == null ? null : (Long) values.get(model.getIdProperty());
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
