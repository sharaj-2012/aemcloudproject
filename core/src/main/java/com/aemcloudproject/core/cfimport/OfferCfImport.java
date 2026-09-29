package com.aemcloudproject.core.cfimport;

import com.adobe.acs.commons.fam.ActionManager;
import com.adobe.acs.commons.functions.CheckedConsumer;
import com.adobe.acs.commons.mcp.ControlledProcessManager;
import com.adobe.acs.commons.mcp.ProcessDefinition;
import com.adobe.acs.commons.mcp.ProcessInstance;
import com.adobe.acs.commons.mcp.form.CheckboxComponent;
import com.adobe.acs.commons.mcp.form.FormField;
import com.adobe.acs.commons.mcp.form.PathfieldComponent;
import com.adobe.acs.commons.mcp.model.GenericReport;
import com.adobe.acs.commons.mcp.model.ManagedProcess;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.ResourceResolver;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * One run of the Offer CF Import.
 *
 * <p>MCP drives the lifecycle: it fills the {@code @FormField} fields from the submitted form,
 * calls {@link #init()}, then {@link #buildProcess} to register the stages, runs the stages in
 * the background, and finally calls {@link #storeReport}.
 */
public class OfferCfImport extends ProcessDefinition {

    // Form fields. The Java field name is the submitted parameter name; "name" is only the label.

    @FormField(
            name = "DAM file",
            description = "Path of the .xlsx asset in DAM to import",
            component = PathfieldComponent.AssetSelectComponent.class,
            required = true)
    transient String damFilePath;

    @FormField(
            name = "Dry run",
            description = "Report what would change without writing anything",
            component = CheckboxComponent.class,
            options = "checked")
    transient boolean dryRun = true;

    private final transient ControlledProcessManager processManager;

    // Per-run state. Stages run on FAM worker threads one after another; fields set by one stage
    // and read by a later one are volatile.

    transient ImportFile file;
    transient ImportReport report;
    transient volatile ParsedSheet sheet;
    transient volatile ModelDefinition model;
    transient volatile ImportTarget target;
    transient volatile List<ConvertedRow> rows;
    transient volatile List<PlannedRow> plan;
    /** Row number to the fields that differ from the stored fragment; UPDATE rows only. */
    transient volatile Map<Integer, FragmentComparer.Comparison> changes;

    OfferCfImport(ControlledProcessManager processManager) {
        this.processManager = processManager;
    }

    @Override
    public void init() throws RepositoryException {
        // Runs in the start request with no resolver, so a failure here is returned to the author
        // straight away and no run is created. The DAM file is read later, in buildProcess().
        if (damFilePath == null || damFilePath.trim().isEmpty()) {
            throw new RepositoryException("Choose the .xlsx file to import from DAM.");
        }
        // One import at a time: two runs could both plan a CREATE for the same fragment. MCP only
        // marks this run as running after buildProcess(), so it never finds itself here.
        for (ProcessInstance other : processManager.getActiveProcesses()) {
            ManagedProcess info = other.getInfo();
            if (OfferCfImportFactory.PROCESS_NAME.equals(info.getName()) && info.isIsRunning()) {
                throw new RepositoryException("Another Offer CF Import is running (\"" + info.getDescription()
                        + "\", started by " + info.getRequester() + " at " + info.getStartTimeFormatted()
                        + "). Start this one when it has finished.");
            }
        }
    }

    @Override
    public void buildProcess(ProcessInstance instance, ResourceResolver resolver)
            throws LoginException, RepositoryException {
        // MCP calls this in the start request with the author's resolver, before any stage runs.
        // If it throws, MCP records the error on the run and stops it.
        report = new ImportReport(dryRun);
        try {
            file = ImportFile.fromDamAsset(resolver, damFilePath.trim());
        } catch (RepositoryException e) {
            report.stopped(e.getMessage());
            throw e;
        }
        report.setFileName(file.getName());
        // Critical actions run in order, each only if the one before finished without errors.
        instance.defineCriticalAction("Read workbook", resolver, stage(this::readWorkbook));
        instance.defineCriticalAction("Read model", resolver, stage(this::readModel));
        instance.defineCriticalAction("Convert values", resolver, stage(this::convertValues));
        instance.defineCriticalAction("Plan import", resolver, stage(this::planImport));
        // The gate: if any row has a problem this stage fails, so no stage after it runs.
        instance.defineCriticalAction("Validate", resolver, stage(this::validate));
        instance.defineCriticalAction("Compare", resolver, stage(this::compare));
        if (!dryRun) {
            // Only a real run has write stages, so a dry run can't change content.
            instance.defineCriticalAction("Create folders", resolver, stage(this::createFolders));
            instance.defineCriticalAction("Write fragments", resolver, stage(this::writeFragments));
            instance.defineCriticalAction("Write references", resolver, stage(this::writeReferences));
        }
    }

    /**
     * Runs a stage's work on a FAM thread with a resolver of the author's, and records in the
     * report why it failed, so the report explains a stopped run as well as MCP's error list.
     */
    private CheckedConsumer<ActionManager> stage(CheckedConsumer<ResourceResolver> work) {
        return manager -> manager.deferredWithResolver(resolver -> {
            try {
                work.accept(resolver);
            } catch (Exception e) {
                report.stopped(e.getMessage());
                throw e;
            }
        });
    }

    private void readWorkbook(ResourceResolver resolver) throws ImportException {
        ParsedSheet parsed = WorkbookParser.parse(file.getContent());
        for (ParsedRow row : parsed.getRows()) {
            report.row(row.getRowNumber()); // every row gets a line, even if the run stops early
        }
        sheet = parsed;
    }

    private void readModel(ResourceResolver resolver) throws ImportException {
        ModelDefinition definition = ModelReader.forSheet(resolver, sheet.getSheetName());
        // Only models with a destination folder can be imported; this stops the run otherwise.
        ImportTarget destination = ImportTarget.forModel(definition.getId());
        report.setModelId(definition.getId());
        model = definition;
        target = destination;
    }

    private void convertValues(ResourceResolver resolver) {
        RowConverter converter = new RowConverter(model, sheet);
        for (Map.Entry<String, String> correction : converter.getCaseCorrections().entrySet()) {
            report.note("Column '" + correction.getKey() + "' was read as field " + correction.getValue() + ".");
        }
        for (String column : converter.getUnknownColumns()) {
            report.note("Column '" + column + "' matches no field of " + model.getId() + " and was ignored.");
        }
        if (!converter.getFieldsNotInSheet().isEmpty()) {
            report.note("Not in the file, so existing fragments keep their values: "
                    + String.join(", ", converter.getFieldsNotInSheet()) + ".");
        }
        List<ConvertedRow> converted = new ArrayList<>();
        for (ParsedRow row : sheet.getRows()) {
            ConvertedRow result = converter.convert(row);
            report.row(result.getRowNumber()).errors.addAll(result.getProblems());
            converted.add(result);
        }
        rows = converted;
    }

    private void planImport(ResourceResolver resolver) throws ImportException {
        List<PlannedRow> planned = ImportPlanner.plan(resolver, model, target, rows);
        for (PlannedRow row : planned) {
            ImportReport.RowEntry entry = report.row(row.getRowNumber());
            entry.path = row.getPath();
            // The row's conversion problems are already in the report; add only the new ones.
            entry.errors.addAll(row.getProblems().subList(row.getRow().getProblems().size(), row.getProblems().size()));
        }
        plan = planned;
    }

    private void validate(ResourceResolver resolver) throws ImportException {
        for (PreflightValidator.Finding finding : new PreflightValidator(resolver, model, target, plan).validate()) {
            ImportReport.RowEntry entry = report.row(finding.rowNumber);
            (finding.severity == PreflightValidator.Severity.PROBLEM ? entry.errors : entry.warnings).add(finding.message);
        }
        Set<Integer> rowsWithProblems = new TreeSet<>();
        for (PlannedRow row : plan) {
            if (!report.row(row.getRowNumber()).errors.isEmpty()) {
                rowsWithProblems.add(row.getRowNumber());
            }
        }
        if (!rowsWithProblems.isEmpty()) {
            // Failing this critical stage stops the run before any write stage.
            throw new ImportException(String.format("%d of %d rows have problems (rows %s), so nothing was written. "
                    + "Fix them in the file and run it again.", rowsWithProblems.size(), plan.size(),
                    rowsWithProblems.toString().replaceAll("[\\[\\]]", "")));
        }
    }

    private void compare(ResourceResolver resolver) throws ImportException {
        FragmentComparer comparer = new FragmentComparer(resolver, model);
        Map<Integer, FragmentComparer.Comparison> compared = new HashMap<>();
        for (PlannedRow row : plan) {
            ImportReport.RowEntry entry = report.row(row.getRowNumber());
            if (row.getAction() == PlannedRow.Action.CREATE) {
                entry.fieldsChanged = filledFields(row);
                if (dryRun) {
                    entry.outcome = ImportReport.WILL_CREATE;
                }
                continue;
            }
            FragmentComparer.Comparison comparison = comparer.compare(row);
            compared.put(row.getRowNumber(), comparison);
            if (comparison.isUnchanged()) {
                entry.outcome = ImportReport.UNCHANGED;
            } else {
                List<String> parts = new ArrayList<>();
                for (Map.Entry<String, String> change : comparison.changes.entrySet()) {
                    parts.add(change.getKey() + ": " + change.getValue());
                }
                entry.fieldsChanged = String.join("; ", parts);
                if (dryRun) {
                    entry.outcome = ImportReport.WILL_UPDATE;
                }
            }
        }
        changes = compared;
    }

    private void createFolders(ResourceResolver resolver) throws Exception {
        if (FragmentWriter.ensureFolder(resolver, target.getFolder())) {
            resolver.commit();
            report.note("Created the folder " + target.getFolder() + ".");
        }
    }

    private void writeFragments(ResourceResolver resolver) throws ImportException {
        FragmentWriter writer = new FragmentWriter(resolver, model, target);
        List<Integer> failedRows = new ArrayList<>();
        List<PlannedRow> waitingForReferences = new ArrayList<>();
        for (PlannedRow row : plan) {
            FragmentComparer.Comparison rowChanges = changes.get(row.getRowNumber());
            if (rowChanges != null && rowChanges.isUnchanged()) {
                continue; // no write and no version
            }
            ImportReport.RowEntry entry = report.row(row.getRowNumber());
            try {
                if (rowChanges != null) {
                    writer.saveVersion(row, "Before Offer CF Import of " + file.getName() + ", row " + row.getRowNumber());
                }
                FragmentWriter.Result result = writer.write(row, rowChanges);
                resolver.commit(); // one save per fragment
                entry.outcome = row.getAction() == PlannedRow.Action.CREATE ? ImportReport.CREATED : ImportReport.UPDATED;
                if (!result.referencesLater.isEmpty()) {
                    waitingForReferences.add(row);
                }
            } catch (Exception e) {
                resolver.revert(); // drop this row's unsaved changes; the next row starts clean
                failedRows.add(row.getRowNumber());
                entry.outcome = ImportReport.FAILED;
                entry.errors.add("Not written: " + e.getMessage());
            }
        }
        if (!failedRows.isEmpty()) {
            // The references stage won't run, so say so on the rows that were written.
            for (PlannedRow row : waitingForReferences) {
                report.row(row.getRowNumber()).warnings.add("Fragment references were not set, because other rows failed.");
            }
            throw new ImportException(failedRows.size() + " fragment(s) could not be written (rows "
                    + failedRows.toString().replaceAll("[\\[\\]]", "") + "); the others were.");
        }
    }

    private void writeReferences(ResourceResolver resolver) throws ImportException {
        FragmentWriter writer = new FragmentWriter(resolver, model, target);
        List<Integer> failedRows = new ArrayList<>();
        for (PlannedRow row : plan) {
            FragmentComparer.Comparison rowChanges = changes.get(row.getRowNumber());
            if (rowChanges != null && rowChanges.isUnchanged()) {
                continue;
            }
            try {
                FragmentWriter.Result result = writer.writeReferences(row, rowChanges);
                if (!result.set.isEmpty() || !result.cleared.isEmpty()) {
                    resolver.commit();
                }
            } catch (Exception e) {
                resolver.revert();
                failedRows.add(row.getRowNumber());
                ImportReport.RowEntry entry = report.row(row.getRowNumber());
                entry.outcome = ImportReport.FAILED;
                entry.errors.add("Fields written, but fragment references not set: " + e.getMessage());
            }
        }
        if (!failedRows.isEmpty()) {
            throw new ImportException("Fragment references could not be set on " + failedRows.size()
                    + " fragment(s) (rows " + failedRows.toString().replaceAll("[\\[\\]]", "") + ").");
        }
    }

    /** For a new fragment: the fields the row fills in, e.g. "5 fields: ID, name, …". */
    private static String filledFields(PlannedRow row) {
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, FieldValue> entry : row.getRow().getValues().entrySet()) {
            if (entry.getValue().getKind() == FieldValue.Kind.VALUE) {
                names.add(entry.getKey());
            }
        }
        return names.size() + " fields: " + String.join(", ", names);
    }

    @Override
    public void storeReport(ProcessInstance instance, ResourceResolver resolver)
            throws RepositoryException, PersistenceException {
        GenericReport generic = new GenericReport();
        generic.setRows(report == null ? new ArrayList<>() : report.toRows(), ReportColumn.class);
        generic.persist(resolver, instance.getPath() + "/jcr:content/report");
    }
}
