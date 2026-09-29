package com.aemcloudproject.core.cfimport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One data row of the sheet, as text.
 *
 * <p>Every column of the sheet has an entry, so a blank cell ({@code ""}) can be told apart from
 * a column the sheet doesn't have at all ({@link #hasColumn} is false, {@link #get} is null).
 */
public final class ParsedRow {

    private final int rowNumber;
    private final Map<String, String> cells;

    ParsedRow(int rowNumber, Map<String, String> cells) {
        this.rowNumber = rowNumber;
        this.cells = Collections.unmodifiableMap(new LinkedHashMap<>(cells));
    }

    /** The row number Excel shows, so messages point the author at the right line. */
    public int getRowNumber() {
        return rowNumber;
    }

    public boolean hasColumn(String column) {
        return cells.containsKey(column);
    }

    /** The cell text, {@code ""} for a blank cell, or null if the sheet has no such column. */
    public String get(String column) {
        return cells.get(column);
    }

    public boolean isBlank(String column) {
        String value = cells.get(column);
        return value != null && value.isEmpty();
    }

    /**
     * The cell split into its lines (Alt+Enter in Excel), each trimmed, with empty lines dropped.
     * Multi-value fields use one value per line.
     */
    public List<String> getLines(String column) {
        String value = cells.get(column);
        List<String> lines = new ArrayList<>();
        if (value != null) {
            for (String line : value.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    lines.add(trimmed);
                }
            }
        }
        return lines;
    }

    /** Column name to cell text, in sheet order. */
    public Map<String, String> getCells() {
        return cells;
    }
}
