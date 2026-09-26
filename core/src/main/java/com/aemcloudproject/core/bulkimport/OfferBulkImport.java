package com.aemcloudproject.core.bulkimport;

import com.adobe.acs.commons.fam.ActionManager;
import com.adobe.acs.commons.mcp.ControlledProcessManager;
import com.adobe.acs.commons.mcp.ProcessDefinition;
import com.adobe.acs.commons.mcp.ProcessInstance;
import com.adobe.acs.commons.mcp.form.CheckboxComponent;
import com.adobe.acs.commons.mcp.form.FileUploadComponent;
import com.adobe.acs.commons.mcp.form.FormField;
import com.adobe.acs.commons.mcp.form.PathfieldComponent;
import com.adobe.acs.commons.mcp.model.GenericReport;
import com.day.cq.commons.jcr.JcrConstants;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ACS Commons MCP process that imports the offer workbook into content fragments.
 *
 * <p>Runs in up to three steps: plan every write (and stop if the models are out of date or
 * another import is running), create the folder structure, then write fragments one at a time.
 * A dry run skips the folders and writes nothing, but reports exactly what a real run would do.
 * Writes run with the permissions of the author who starts the process.</p>
 */
public class OfferBulkImport extends ProcessDefinition {

    static final String NAME = "Offer Bulk Import";
    static final String DEFAULT_ROOT = "/content/dam/aemcloudproject/cfs";

    public enum ReportColumn {
        SHEET, ROW, MODEL, SLUG, PATH, OUTCOME, FIELDS_CHANGED, MESSAGE
    }

    @FormField(name = "Workbook",
            description = "Offer workbook (.xlsx) with one sheet per model",
            component = FileUploadComponent.class,
            required = true)
    transient RequestParameter workbook;

    @FormField(name = "Target folder",
            description = "Fragments go into fixed sub-folders below this folder",
            component = PathfieldComponent.FolderSelectComponent.class,
            options = {"default=" + DEFAULT_ROOT, "base=/content/dam"},
            required = true)
    transient String targetRoot = DEFAULT_ROOT;

    @FormField(name = "Dry run",
            description = "Report exactly what would change, without writing anything",
            component = CheckboxComponent.class,
            options = "checked")
    transient boolean dryRun = true;

    @FormField(name = "Create stubs",
            description = "Create a placeholder fragment for a referenced slug that no sheet defines; "
                    + "when off, the referencing row fails instead",
            component = CheckboxComponent.class,
            options = "checked")
    transient boolean createStubs = true;

    @FormField(name = "Version before update",
            description = "Save a version of each fragment before changing it",
            component = CheckboxComponent.class,
            options = "checked")
    transient boolean versionBeforeUpdate = true;

    private final transient ControlledProcessManager processManager;
    private final transient ZoneId zone;
    private final transient List<ReportRow> report = Collections.synchronizedList(new ArrayList<>());
    private transient ParsedWorkbook parsed;
    private transient ImportPlan plan;
    private transient String instanceId;

    OfferBulkImport(ControlledProcessManager processManager, ZoneId zone) {
        this.processManager = processManager;
        this.zone = zone;
    }

    @Override
    public void init() throws RepositoryException {
        if (workbook == null) {
            throw new RepositoryException("Upload a workbook to import");
        }
        try (InputStream in = workbook.getInputStream()) {
            parsed = new WorkbookParser(zone).parse(in);
        } catch (IOException | RuntimeException e) {
            throw new RepositoryException("Could not read the workbook as .xlsx: " + e.getMessage(), e);
        }
        if (parsed.isEmpty()) {
            throw new RepositoryException("The workbook has none of the expected sheets: " + Arrays.stream(ModelSpec.values())
                    .map(ModelSpec::getSheetName).collect(Collectors.joining(", ")));
        }
    }

    @Override
    public void buildProcess(ProcessInstance instance, ResourceResolver rr) throws LoginException, RepositoryException {
        instanceId = instance.getId();
        instance.defineCriticalAction("Plan import", rr, this::planImport);
        if (!dryRun) {
            instance.defineCriticalAction("Create folders", rr, this::createFolders);
        }
        instance.defineCriticalAction(dryRun ? "Preview changes (dry run)" : "Write fragments", rr, this::writeFragments);
    }

    private void planImport(ActionManager manager) {
        manager.deferredWithResolver(rr -> {
            String other = otherRunningImport();
            if (other != null) {
                fail("Another import is already running (" + other + "); wait for it to finish");
            }
            plan = new ImportPlanner(rr, targetRoot, createStubs).plan(parsed);
            report.addAll(plan.getIssues());
            if (plan.isFatal()) {
                fail(String.join("; ", plan.getFatalErrors()));
            }
        });
    }

    private void createFolders(ActionManager manager) {
        manager.deferredWithResolver(rr -> {
            String root = targetRoot.replaceAll("/+$", "");
            for (ModelSpec model : ModelSpec.values()) {
                ensureFolder(rr, root + "/" + model.getFolder());
            }
            rr.commit();
        });
    }

    private void writeFragments(ActionManager manager) {
        manager.deferredWithResolver(rr -> {
            FragmentWriter writer = new FragmentWriter(dryRun, versionBeforeUpdate);
            for (WriteOp op : plan.getOps()) {
                manager.setCurrentItem(op.getPath());
                report.add(writer.write(rr, op));
            }
        });
    }

    private String otherRunningImport() {
        if (processManager == null) {
            return null;
        }
        for (ProcessInstance instance : processManager.getActiveProcesses()) {
            if (!instance.getId().equals(instanceId) && instance.getName() != null && instance.getName().startsWith(NAME)) {
                return instance.getName();
            }
        }
        return null;
    }

    private void fail(String message) {
        report.add(ReportRow.issue(Outcome.ERROR, "", 0, message));
        throw new IllegalStateException(message);
    }

    /**
     * Creates a DAM folder and any missing ancestors below /content/dam.
     */
    static void ensureFolder(ResourceResolver rr, String path) throws PersistenceException {
        if (rr.getResource(path) != null) {
            return;
        }
        if (!path.startsWith(ImportPlanner.DAM_ROOT)) {
            throw new PersistenceException("Refusing to create a folder outside /content/dam: " + path);
        }
        int slash = path.lastIndexOf('/');
        String parentPath = path.substring(0, slash);
        ensureFolder(rr, parentPath);
        String name = path.substring(slash + 1);
        Resource folder = rr.create(rr.getResource(parentPath), name,
                Collections.singletonMap(JcrConstants.JCR_PRIMARYTYPE, "sling:Folder"));
        Map<String, Object> content = new HashMap<>();
        content.put(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED);
        content.put(JcrConstants.JCR_TITLE, name);
        rr.create(folder, JcrConstants.JCR_CONTENT, content);
    }

    @Override
    public void storeReport(ProcessInstance instance, ResourceResolver rr) throws RepositoryException, PersistenceException {
        List<EnumMap<ReportColumn, Object>> rows = new ArrayList<>();
        synchronized (report) {
            for (ReportRow row : report) {
                rows.add(toColumns(row));
            }
        }
        GenericReport genericReport = new GenericReport();
        genericReport.setName(instance.getName());
        genericReport.setRows(rows, ReportColumn.class);
        genericReport.persist(rr, instance.getPath() + "/jcr:content/report");
    }

    static EnumMap<ReportColumn, Object> toColumns(ReportRow row) {
        EnumMap<ReportColumn, Object> columns = new EnumMap<>(ReportColumn.class);
        columns.put(ReportColumn.SHEET, row.getSheet());
        columns.put(ReportColumn.ROW, row.getRow() == 0 ? "" : String.valueOf(row.getRow()));
        columns.put(ReportColumn.MODEL, row.getModel());
        columns.put(ReportColumn.SLUG, row.getSlug() == null ? "" : row.getSlug());
        columns.put(ReportColumn.PATH, row.getPath());
        columns.put(ReportColumn.OUTCOME, row.getOutcome().name());
        columns.put(ReportColumn.FIELDS_CHANGED, row.getFieldsChanged());
        columns.put(ReportColumn.MESSAGE, row.getMessage());
        return columns;
    }

    List<ReportRow> getReport() {
        return report;
    }
}
