package com.aemcloudproject.core.bulkimport;

import java.util.Collection;

/**
 * One line of the import report.
 */
public final class ReportRow {

    private final String sheet;
    private final int row;
    private final String model;
    private final String slug;
    private final String path;
    private final Outcome outcome;
    private final String fieldsChanged;
    private final String message;

    ReportRow(String sheet, int row, String model, String slug, String path, Outcome outcome,
              Collection<String> fieldsChanged, String message) {
        this.sheet = sheet;
        this.row = row;
        this.model = model;
        this.slug = slug;
        this.path = path;
        this.outcome = outcome;
        this.fieldsChanged = fieldsChanged == null || fieldsChanged.isEmpty() ? "" : String.join(", ", fieldsChanged);
        this.message = message == null ? "" : message;
    }

    /**
     * A workbook-level issue not tied to a single fragment, such as an unrecognised sheet.
     */
    static ReportRow issue(Outcome outcome, String sheet, int row, String message) {
        return new ReportRow(sheet, row, "", "", "", outcome, null, message);
    }

    public String getSheet() {
        return sheet;
    }

    public int getRow() {
        return row;
    }

    public String getModel() {
        return model;
    }

    public String getSlug() {
        return slug;
    }

    public String getPath() {
        return path;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getFieldsChanged() {
        return fieldsChanged;
    }

    public String getMessage() {
        return message;
    }
}
