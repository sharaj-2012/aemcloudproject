package com.aemcloudproject.core.cfimport;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks planned rows against the model and the repository before anything is written:
 * required fields, references, business IDs and parent loops. Reads only.
 */
final class PreflightValidator {

    enum Severity { PROBLEM, WARNING }

    /** One thing found on one row. Problems stop the import; warnings are only reported. */
    static final class Finding {
        final int rowNumber;
        final Severity severity;
        final String message;

        Finding(int rowNumber, Severity severity, String message) {
            this.rowNumber = rowNumber;
            this.severity = severity;
            this.message = message;
        }
    }

    /** Longest parent chain followed when looking for loops. */
    private static final int MAX_PARENT_DEPTH = 50;

    private final ResourceResolver resolver;
    private final ModelDefinition model;
    private final ImportTarget target;
    private final List<PlannedRow> plan;
    private final Map<String, PlannedRow> plannedByPath = new HashMap<>();
    private final Map<String, String> fragmentModelCache = new HashMap<>();
    private final List<Finding> findings = new ArrayList<>();

    PreflightValidator(ResourceResolver resolver, ModelDefinition model, ImportTarget target, List<PlannedRow> plan) {
        this.resolver = resolver;
        this.model = model;
        this.target = target;
        this.plan = plan;
        for (PlannedRow row : plan) {
            if (row.getAction() != null) {
                plannedByPath.put(row.getPath(), row);
            }
        }
    }

    /** Findings in row order. Rows that already have problems are skipped; their problems are known. */
    List<Finding> validate() {
        Map<Object, Integer> rowById = new HashMap<>();
        for (PlannedRow row : plan) {
            if (row.getAction() == null) {
                continue;
            }
            checkRequired(row);
            checkReferences(row);
            checkUniqueId(row, rowById);
        }
        checkParentLoops();
        findings.sort((a, b) -> Integer.compare(a.rowNumber, b.rowNumber));
        return Collections.unmodifiableList(findings);
    }

    private void checkRequired(PlannedRow row) {
        for (ModelField field : model.getFields()) {
            if (!field.isRequired()) {
                continue;
            }
            FieldValue value = row.getRow().getValues().get(field.getName());
            if (row.getAction() == PlannedRow.Action.CREATE) {
                if (value == null) {
                    problem(row, field.getName() + ": required, but the sheet has no " + field.getName() + " column.");
                } else if (value.getKind() == FieldValue.Kind.BLANK) {
                    problem(row, field.getName() + ": required, but blank.");
                }
            }
            if (value != null && value.getKind() == FieldValue.Kind.CLEAR) {
                problem(row, field.getName() + ": required, so it can't be cleared.");
            }
        }
    }

    private void checkReferences(PlannedRow row) {
        for (Map.Entry<String, FieldValue> entry : row.getRow().getValues().entrySet()) {
            FieldValue value = entry.getValue();
            ModelField field = field(entry.getKey());
            if (value.getKind() != FieldValue.Kind.VALUE || field == null) {
                continue;
            }
            for (Object item : asList(value.getValue())) {
                String reference = String.valueOf(item);
                if (field.getType() == FieldType.FRAGMENT_REFERENCE) {
                    checkFragmentReference(row, field, reference);
                } else if (field.getType() == FieldType.CONTENT_REFERENCE) {
                    checkContentReference(row, field, reference);
                }
            }
        }
    }

    private void checkFragmentReference(PlannedRow row, ModelField field, String path) {
        String name = field.getName() + ": ";
        if (!path.startsWith("/")) {
            problem(row, name + "'" + path + "' is not a repository path.");
            return;
        }
        if (!underRoot(field, path)) {
            problem(row, name + path + " is outside " + field.getRootPath() + ", where this field's fragments must be.");
            return;
        }
        String referencedModel;
        if (plannedByPath.containsKey(path)) {
            referencedModel = model.getPath(); // created or updated by this same file
        } else {
            String existing = fragmentModel(path);
            if (existing == null) {
                problem(row, name + path + " doesn't exist. Import the " + ids(field.getAllowedModels())
                        + " file first, or fix the path.");
                return;
            }
            if (existing.isEmpty()) {
                problem(row, name + path + " exists but isn't a content fragment.");
                return;
            }
            referencedModel = existing;
        }
        if (!field.getAllowedModels().isEmpty() && !field.getAllowedModels().contains(referencedModel)) {
            problem(row, name + path + " uses the model " + id(referencedModel) + "; this field only accepts "
                    + ids(field.getAllowedModels()) + ".");
        }
    }

    private void checkContentReference(PlannedRow row, ModelField field, String reference) {
        String name = field.getName() + ": ";
        if (reference.startsWith("http://") || reference.startsWith("https://")) {
            return; // an external link, kept as typed
        }
        if (!reference.startsWith("/")) {
            problem(row, name + "'" + reference + "' must be a repository path or an http(s) link.");
            return;
        }
        if (!underRoot(field, reference)) {
            problem(row, name + reference + " is outside " + field.getRootPath() + ", where this field's items must be.");
            return;
        }
        if (resolver.getResource(reference) == null) {
            findings.add(new Finding(row.getRowNumber(), Severity.WARNING,
                    name + reference + " doesn't exist; the fragment would point at a missing item."));
        }
    }

    private void checkUniqueId(PlannedRow row, Map<Object, Integer> rowById) {
        String idField = target.getIdField();
        FieldValue value = idField == null ? null : row.getRow().getValues().get(idField);
        if (value == null || value.getKind() != FieldValue.Kind.VALUE) {
            return;
        }
        Integer earlierRow = rowById.putIfAbsent(value.getValue(), row.getRowNumber());
        if (earlierRow != null) {
            problem(row, idField + ": " + value.getValue() + " is also used in row " + earlierRow + ".");
        }
    }

    /**
     * For each single fragment reference that points at the model's own fragments (parent),
     * follows the links through this file's rows and then the stored fragments, and reports a
     * row whose chain comes back to itself.
     */
    private void checkParentLoops() {
        for (ModelField field : model.getFields()) {
            boolean selfReference = field.getType() == FieldType.FRAGMENT_REFERENCE && !field.isMultiple()
                    && field.getAllowedModels().contains(model.getPath());
            if (!selfReference) {
                continue;
            }
            for (PlannedRow row : plannedByPath.values()) {
                Set<String> chain = new LinkedHashSet<>();
                chain.add(row.getPath());
                String next = parentOf(row.getPath(), field.getName());
                for (int depth = 0; next != null && depth < MAX_PARENT_DEPTH; depth++) {
                    if (next.equals(row.getPath())) {
                        problem(row, field.getName() + ": following the links comes back to this fragment ("
                                + String.join(" → ", names(chain)) + " → " + name(next) + ").");
                        break;
                    }
                    if (!chain.add(next)) {
                        break; // a loop that doesn't include this row; that row reports it
                    }
                    next = parentOf(next, field.getName());
                }
            }
        }
    }

    /** The parent this import gives the fragment, or else the one stored on it now. */
    private String parentOf(String path, String fieldName) {
        PlannedRow planned = plannedByPath.get(path);
        if (planned != null && planned.getRow().getValues().containsKey(fieldName)) {
            FieldValue value = planned.getRow().getValues().get(fieldName);
            return value.getKind() == FieldValue.Kind.VALUE ? String.valueOf(value.getValue()) : null;
        }
        Resource master = resolver.getResource(path + "/jcr:content/data/master");
        return master == null ? null : master.getValueMap().get(fieldName, String.class);
    }

    /**
     * The cq:model of the fragment at the path: null when nothing is there, "" when something is
     * there but it isn't a content fragment (a real fragment always has a model).
     */
    private String fragmentModel(String path) {
        if (fragmentModelCache.containsKey(path)) {
            return fragmentModelCache.get(path);
        }
        Resource resource = resolver.getResource(path);
        String result = null;
        if (resource != null) {
            Resource content = resource.getChild("jcr:content");
            Resource data = content == null ? null : content.getChild("data");
            boolean fragment = content != null && content.getValueMap().get("contentFragment", false) && data != null;
            result = fragment ? data.getValueMap().get("cq:model", "") : "";
        }
        fragmentModelCache.put(path, result);
        return result;
    }

    private void problem(PlannedRow row, String message) {
        findings.add(new Finding(row.getRowNumber(), Severity.PROBLEM, message));
    }

    private ModelField field(String name) {
        for (ModelField field : model.getFields()) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        return null;
    }

    private static boolean underRoot(ModelField field, String path) {
        String root = field.getRootPath();
        return root == null || root.isEmpty() || path.equals(root) || path.startsWith(root.endsWith("/") ? root : root + "/");
    }

    private static List<?> asList(Object value) {
        return value instanceof List ? (List<?>) value : Collections.singletonList(value);
    }

    private static String id(String modelPath) {
        return modelPath.substring(modelPath.lastIndexOf('/') + 1);
    }

    private static String ids(List<String> modelPaths) {
        List<String> ids = new ArrayList<>();
        for (String path : modelPaths) {
            ids.add(id(path));
        }
        return ids.isEmpty() ? "referenced" : String.join(" or ", ids);
    }

    private static String name(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static List<String> names(Set<String> paths) {
        List<String> names = new ArrayList<>();
        for (String path : paths) {
            names.add(name(path));
        }
        return names;
    }
}
