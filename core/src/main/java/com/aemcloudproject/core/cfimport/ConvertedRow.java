package com.aemcloudproject.core.cfimport;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A data row with its cells converted to the types of the model's fields, plus every problem
 * found converting it.
 */
public final class ConvertedRow {

    private final int rowNumber;
    private final Map<String, FieldValue> values;
    private final List<String> problems;

    ConvertedRow(int rowNumber, Map<String, FieldValue> values, List<String> problems) {
        this.rowNumber = rowNumber;
        this.values = Collections.unmodifiableMap(values);
        this.problems = Collections.unmodifiableList(problems);
    }

    public int getRowNumber() {
        return rowNumber;
    }

    /**
     * Field name to value, in model order, for the fields whose column is in the sheet. A field
     * that is missing here was not in the sheet, so its stored value must be left alone.
     */
    public Map<String, FieldValue> getValues() {
        return values;
    }

    /** e.g. "offerID: 'abc' is not a whole number." Empty when the row converted cleanly. */
    public List<String> getProblems() {
        return problems;
    }
}
