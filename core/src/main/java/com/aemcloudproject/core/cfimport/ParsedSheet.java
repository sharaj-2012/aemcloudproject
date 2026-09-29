package com.aemcloudproject.core.cfimport;

import java.util.Collections;
import java.util.List;

/**
 * The first worksheet of an import file: its column names, in sheet order, and its data rows.
 */
public final class ParsedSheet {

    private final String sheetName;
    private final List<String> columns;
    private final List<ParsedRow> rows;

    ParsedSheet(String sheetName, List<String> columns, List<ParsedRow> rows) {
        this.sheetName = sheetName;
        this.columns = Collections.unmodifiableList(columns);
        this.rows = Collections.unmodifiableList(rows);
    }

    public String getSheetName() {
        return sheetName;
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<ParsedRow> getRows() {
        return rows;
    }
}
