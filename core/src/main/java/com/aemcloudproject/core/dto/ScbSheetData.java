package com.aemcloudproject.core.dto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Content of one workbook sheet: its headers, its non-blank rows and the Excel row number of each row.
 */
public class ScbSheetData {

    private final List<String> headers;
    private final List<Map<String, Object>> rows = new ArrayList<>();
    private final List<Integer> rowNumbers = new ArrayList<>();

    /**
     * Creates the sheet data with its header row.
     *
     * @param headers the headers in column order, without blank or invalid headers
     */
    public ScbSheetData(Collection<String> headers) {
        this.headers = new ArrayList<>(headers);
    }

    /**
     * Adds a data row and its Excel row number.
     *
     * @param rowNumber the 1-based Excel row number
     * @param row       the row's values; each value is a {@code String} or a {@code List<String>}
     */
    public void addRow(int rowNumber, Map<String, Object> row) {
        rows.add(row);
        rowNumbers.add(rowNumber);
    }

    /**
     * Returns the header row in column order, without blank or invalid headers.
     *
     * @return the read-only list of headers
     */
    public List<String> getHeaders() {
        return Collections.unmodifiableList(headers);
    }

    /**
     * Returns the data rows, one map per non-blank row, with or without an action.
     *
     * @return the read-only list of rows; each value is a {@code String} or a {@code List<String>}
     */
    public List<Map<String, Object>> getRows() {
        return Collections.unmodifiableList(rows);
    }

    /**
     * Returns the Excel row number of each entry in {@link #getRows()}, by position.
     *
     * @return the read-only list of 1-based Excel row numbers
     */
    public List<Integer> getRowNumbers() {
        return Collections.unmodifiableList(rowNumbers);
    }
}
