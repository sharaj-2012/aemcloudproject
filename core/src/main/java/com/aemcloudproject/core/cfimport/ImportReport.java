package com.aemcloudproject.core.cfimport;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The run's report: one line per data row, which the stages fill in as they go, plus a few
 * run-level lines. Rendered into MCP's GenericReport when the run ends, however it ends.
 */
final class ImportReport {

    static final String CREATED = "CREATED";
    static final String UPDATED = "UPDATED";
    static final String WILL_CREATE = "WILL CREATE";
    static final String WILL_UPDATE = "WILL UPDATE";
    static final String UNCHANGED = "UNCHANGED";
    static final String FAILED = "FAILED";
    static final String ERROR = "ERROR";
    static final String SKIPPED = "SKIPPED";

    /** What the report says about one data row. */
    static final class RowEntry {
        volatile String path;
        volatile String outcome;
        volatile String fieldsChanged;
        final List<String> errors = new CopyOnWriteArrayList<>();
        final List<String> warnings = new CopyOnWriteArrayList<>();
    }

    private final Map<Integer, RowEntry> rows = new ConcurrentSkipListMap<>();
    private final List<String> notes = new CopyOnWriteArrayList<>();
    private volatile String fileName;
    private volatile String modelId;
    private volatile String stoppedBecause;
    private final boolean dryRun;

    ImportReport(boolean dryRun) {
        this.dryRun = dryRun;
    }

    void setFileName(String fileName) {
        this.fileName = fileName;
    }

    void setModelId(String modelId) {
        this.modelId = modelId;
    }

    RowEntry row(int rowNumber) {
        return rows.computeIfAbsent(rowNumber, n -> new RowEntry());
    }

    /** A run-level line, e.g. about a column that matches no field. */
    void note(String message) {
        notes.add(message);
    }

    /** Why a stage failed; the run stops there. */
    void stopped(String reason) {
        stoppedBecause = reason;
    }

    List<EnumMap<ReportColumn, Object>> toRows() {
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        List<EnumMap<ReportColumn, Object>> lines = new ArrayList<>();
        for (Map.Entry<Integer, RowEntry> entry : rows.entrySet()) {
            RowEntry row = entry.getValue();
            String outcome = outcome(row);
            switch (outcome) {
                case CREATED:
                case WILL_CREATE:
                    created++;
                    break;
                case UPDATED:
                case WILL_UPDATE:
                    updated++;
                    break;
                case UNCHANGED:
                    unchanged++;
                    break;
                default:
                    break;
            }
            List<String> messages = new ArrayList<>(row.errors);
            for (String warning : row.warnings) {
                messages.add("Warning: " + warning);
            }
            if (SKIPPED.equals(outcome)) {
                messages.add("Not imported, because the run stopped; see the SUMMARY line.");
            }
            lines.add(line(entry.getKey(), row.path, outcome, row.fieldsChanged, String.join(" | ", messages)));
        }
        List<EnumMap<ReportColumn, Object>> report = new ArrayList<>();
        report.add(line(null, null, "SUMMARY", null, summary(created, updated, unchanged)));
        for (String note : notes) {
            report.add(line(null, null, "NOTE", null, note));
        }
        report.addAll(lines);
        return report;
    }

    /** A row that reached no outcome either had errors or was held back when the run stopped. */
    private static String outcome(RowEntry row) {
        if (row.outcome != null) {
            return row.outcome;
        }
        return row.errors.isEmpty() ? SKIPPED : ERROR;
    }

    private String summary(int created, int updated, int unchanged) {
        String mode = dryRun ? "Dry run" : "Import";
        if (stoppedBecause != null) {
            // A real run can stop after writing some rows; their outcomes are on their lines.
            return mode + " stopped: " + stoppedBecause
                    + (!dryRun && created + updated > 0
                        ? String.format(" Written before it stopped: %d created, %d updated.", created, updated) : "");
        }
        String counts = dryRun
                ? String.format("%d to create, %d to update, %d unchanged", created, updated, unchanged)
                : String.format("%d created, %d updated, %d unchanged", created, updated, unchanged);
        return String.format("%s of %d rows: %s.%s", mode, rows.size(), counts,
                dryRun ? " Nothing was written; untick Dry run to import." : "");
    }

    private EnumMap<ReportColumn, Object> line(Integer rowNumber, String path, String outcome,
                                               String fieldsChanged, String message) {
        EnumMap<ReportColumn, Object> line = new EnumMap<>(ReportColumn.class);
        put(line, ReportColumn.FILE, fileName);
        put(line, ReportColumn.ROW, rowNumber);
        put(line, ReportColumn.MODEL, modelId);
        put(line, ReportColumn.PATH, path);
        put(line, ReportColumn.OUTCOME, outcome);
        put(line, ReportColumn.FIELDS_CHANGED, fieldsChanged);
        put(line, ReportColumn.MESSAGE, message);
        return line;
    }

    private static void put(EnumMap<ReportColumn, Object> line, ReportColumn column, Object value) {
        if (value != null && !String.valueOf(value).isEmpty()) {
            line.put(column, value);
        }
    }
}
