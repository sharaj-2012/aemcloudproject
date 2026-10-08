package com.aemcloudproject.core.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Content of one workbook sheet: its headers, its rows that have an action and the Excel row number of each row.
 */
public class ScbSheetData {

    private final List<String> headers = new ArrayList<>();
    private final List<Map<String, Object>> rows = new ArrayList<>();
    private final List<Integer> rowNumbers = new ArrayList<>();

    /**
     * Returns the header row in column order, without blank or invalid headers.
     *
     * @return the mutable list of headers
     */
    public List<String> getHeaders() {
        return headers;
    }

    /**
     * Returns the data rows, one map per row that has an action.
     *
     * @return the mutable list of rows; each value is a {@code String} or a {@code List<String>}
     */
    public List<Map<String, Object>> getRows() {
        return rows;
    }

    /**
     * Returns the Excel row number of each entry in {@link #getRows()}, by position.
     *
     * @return the mutable list of 1-based Excel row numbers
     */
    public List<Integer> getRowNumbers() {
        return rowNumbers;
    }
}
