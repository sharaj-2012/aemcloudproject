package com.aemcloudproject.core.bulkimport;

import java.util.ArrayList;
import java.util.List;

/**
 * The outcome of planning: every write in execution order, workbook-level issues, and any
 * problem serious enough that nothing should be written.
 */
public final class ImportPlan {

    private final List<WriteOp> ops = new ArrayList<>();
    private final List<ReportRow> issues = new ArrayList<>();
    private final List<String> fatalErrors = new ArrayList<>();

    public List<WriteOp> getOps() {
        return ops;
    }

    public List<ReportRow> getIssues() {
        return issues;
    }

    public List<String> getFatalErrors() {
        return fatalErrors;
    }

    public boolean isFatal() {
        return !fatalErrors.isEmpty();
    }
}
