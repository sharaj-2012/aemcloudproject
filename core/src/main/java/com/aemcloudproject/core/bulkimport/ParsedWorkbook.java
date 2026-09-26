package com.aemcloudproject.core.bulkimport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The typed rows of every recognised sheet, plus workbook-level issues.
 */
public final class ParsedWorkbook {

    private final Map<ModelSpec, List<ParsedRow>> rows = new EnumMap<>(ModelSpec.class);
    private final List<ReportRow> issues = new ArrayList<>();

    void add(ParsedRow row) {
        rows.computeIfAbsent(row.getModel(), m -> new ArrayList<>()).add(row);
    }

    void addIssue(ReportRow issue) {
        issues.add(issue);
    }

    void markSheetSeen(ModelSpec model) {
        rows.computeIfAbsent(model, m -> new ArrayList<>());
    }

    public List<ParsedRow> getRows(ModelSpec model) {
        return rows.getOrDefault(model, Collections.emptyList());
    }

    public boolean hasSheet(ModelSpec model) {
        return rows.containsKey(model);
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public List<ReportRow> getIssues() {
        return issues;
    }
}
