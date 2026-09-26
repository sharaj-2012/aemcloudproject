package com.aemcloudproject.core.bulkimport;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns parsed workbook rows into an ordered list of fragment writes, without writing anything.
 *
 * <p>Planning is where every cross-sheet decision is made, so the write step can treat each
 * fragment independently:</p>
 * <ul>
 *   <li>Identity: offers are found by offerID, then by slug; every other model by slug. A slug
 *       matches a fragment's node name or its slug property.</li>
 *   <li>References: slugs resolve against fragments that exist or that this workbook creates.
 *       An unknown slug becomes a stub fragment, or an error when stubs are off.</li>
 *   <li>IDs: generated for new fragments as the model's highest existing ID plus one, and never
 *       changed on an existing fragment.</li>
 *   <li>Errors cascade: a row referencing a fragment that will not be created fails too, rather
 *       than writing a dangling reference.</li>
 * </ul>
 */
public final class ImportPlanner {

    static final String DAM_ROOT = "/content/dam/";
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9][a-z0-9_-]*$");
    private static final String MODEL_ITEMS = "jcr:content/model/cq:dialog/content/items";

    private final ResourceResolver resolver;
    private final String root;
    private final boolean createStubs;

    private final Map<ModelSpec, ExistingFragments> existing = new EnumMap<>(ModelSpec.class);
    /** Per model, slug to the path that will hold it once the import finishes. */
    private final Map<ModelSpec, Map<String, String>> slugIndex = new EnumMap<>(ModelSpec.class);
    /** Per model, sheet slugs whose rows failed and do not already exist. */
    private final Map<ModelSpec, Set<String>> erroredSlugs = new EnumMap<>(ModelSpec.class);
    private final Map<ModelSpec, Set<String>> seenSlugs = new EnumMap<>(ModelSpec.class);
    private final Map<ModelSpec, Set<Long>> seenIds = new EnumMap<>(ModelSpec.class);
    private final Map<String, WriteOp> opsByPath = new HashMap<>();
    /** Resolved reference paths per op and property, kept as lists until the op is finalised. */
    private final Map<WriteOp, Map<String, List<String>>> references = new HashMap<>();

    public ImportPlanner(ResourceResolver resolver, String root, boolean createStubs) {
        this.resolver = resolver;
        this.root = root == null ? "" : root.replaceAll("/+$", "");
        this.createStubs = createStubs;
        for (ModelSpec model : ModelSpec.values()) {
            slugIndex.put(model, new HashMap<>());
            erroredSlugs.put(model, new HashSet<>());
            seenSlugs.put(model, new HashSet<>());
            seenIds.put(model, new HashSet<>());
        }
    }

    public ImportPlan plan(ParsedWorkbook workbook) {
        ImportPlan plan = new ImportPlan();
        plan.getIssues().addAll(workbook.getIssues());
        if (!root.startsWith(DAM_ROOT)) {
            plan.getFatalErrors().add("Target folder must be under " + DAM_ROOT + ", got '" + root + "'");
            return plan;
        }
        checkModels(plan);
        if (plan.isFatal()) {
            return plan;
        }

        for (ModelSpec model : ModelSpec.values()) {
            ExistingFragments fragments = ExistingFragments.load(resolver, folderPath(model), model);
            existing.put(model, fragments);
            slugIndex.get(model).putAll(fragments.bySlug);
        }
        for (ModelSpec model : ModelSpec.values()) {
            for (ParsedRow row : workbook.getRows(model)) {
                plan.getOps().add(planRow(row));
            }
        }
        resolveReferences(plan);
        detectCycles(plan);
        cascadeErrors(plan);
        allocateIds(plan);
        finalise(plan);
        return plan;
    }

    private void checkModels(ImportPlan plan) {
        for (ModelSpec model : ModelSpec.values()) {
            Resource items = resolver.getResource(model.getModelPath() + "/" + MODEL_ITEMS);
            if (items == null) {
                plan.getFatalErrors().add("Content fragment model not found at " + model.getModelPath()
                        + "; deploy ui.content first");
                continue;
            }
            Set<String> present = new HashSet<>();
            for (Resource item : items.getChildren()) {
                String name = item.getValueMap().get("name", String.class);
                if (name != null) {
                    present.add(name);
                }
            }
            List<String> missing = model.getFields().stream()
                    .map(FieldSpec::getProperty)
                    .filter(p -> !present.contains(p))
                    .collect(Collectors.toList());
            if (model.getIdProperty() != null && !present.contains(model.getIdProperty())) {
                missing.add(model.getIdProperty());
            }
            if (!missing.isEmpty()) {
                plan.getFatalErrors().add("Model " + model.getModelName() + " on this instance has no element for "
                        + missing + "; deploy the updated content fragment models first");
            }
        }
    }

    private WriteOp planRow(ParsedRow row) {
        ModelSpec model = row.getModel();
        String slug = row.getSlug();
        Long id = row.getId();
        WriteOp op = new WriteOp(model, row.getSheet(), row.getRowNumber(), slug, false);
        op.getErrors().addAll(row.getErrors());
        op.getWarnings().addAll(row.getWarnings());
        op.getValues().putAll(row.getValues());

        if (slug != null && !SLUG.matcher(slug).matches()) {
            op.getErrors().add("invalid " + model.getSlugProperty() + " '" + slug
                    + "'; use lowercase letters, digits, '-' or '_'");
        }
        if (slug != null && !seenSlugs.get(model).add(slug)) {
            op.getErrors().add("duplicate " + model.getSlugProperty() + " '" + slug + "' in this sheet");
        }
        if (id != null && !seenIds.get(model).add(id)) {
            op.getErrors().add("duplicate " + model.getIdProperty() + " " + id + " in this sheet");
        }
        if (!op.hasErrors()) {
            resolveIdentity(op, slug, id);
        }

        if (op.hasErrors()) {
            if (slug != null && !slugIndex.get(model).containsKey(slug)) {
                erroredSlugs.get(model).add(slug);
            }
        } else {
            if (slug != null) {
                slugIndex.get(model).put(slug, op.getPath());
            }
            opsByPath.put(op.getPath(), op);
        }
        return op;
    }

    private void resolveIdentity(WriteOp op, String slug, Long id) {
        ModelSpec model = op.getModel();
        ExistingFragments fragments = existing.get(model);
        String bySlug = slug == null ? null : fragments.bySlug.get(slug);

        if (id != null) {
            String byId = fragments.byId.get(id);
            if (byId != null) {
                if (bySlug != null && !bySlug.equals(byId)) {
                    op.getErrors().add(model.getIdProperty() + " " + id + " is " + byId + ", but "
                            + model.getSlugProperty() + " '" + slug + "' is " + bySlug);
                    return;
                }
                op.setPath(byId);
                return;
            }
            if (bySlug != null) {
                Long existingId = fragments.idByPath.get(bySlug);
                if (existingId != null) {
                    op.getErrors().add(model.getSlugProperty() + " '" + slug + "' is " + bySlug + " with "
                            + model.getIdProperty() + " " + existingId + ", not " + id);
                    return;
                }
                op.setPath(bySlug);
                return;
            }
        }

        if (bySlug != null) {
            op.setPath(bySlug);
            return;
        }
        if (slug == null) {
            op.getErrors().add(model.getSlugProperty() + " is required to create a new fragment");
            return;
        }
        String path = folderPath(model) + "/" + slug;
        if (resolver.getResource(path) != null) {
            op.getErrors().add(path + " already exists but is not a " + model.getModelName() + " fragment");
            return;
        }
        op.setPath(path);
        op.setCreate(true);
    }

    @SuppressWarnings("unchecked")
    private void resolveReferences(ImportPlan plan) {
        List<WriteOp> stubs = new ArrayList<>();
        for (WriteOp op : plan.getOps()) {
            if (op.hasErrors()) {
                continue;
            }
            Map<String, List<String>> resolved = new LinkedHashMap<>();
            for (FieldSpec field : op.getModel().getFields()) {
                Object value = op.getValues().get(field.getProperty());
                if (!field.getType().isReference() || value == null) {
                    continue;
                }
                List<String> paths = new ArrayList<>();
                for (String slug : (List<String>) value) {
                    String path = resolveSlug(field, slug, op, stubs);
                    if (path != null && !paths.contains(path)) {
                        paths.add(path);
                    }
                }
                resolved.put(field.getProperty(), paths);
            }
            references.put(op, resolved);
        }
        plan.getOps().addAll(stubs);
    }

    private String resolveSlug(FieldSpec field, String slug, WriteOp op, List<WriteOp> stubs) {
        ModelSpec target = field.getTargetModel();
        String label = "'" + field.getProperty() + "' references " + target.getModelName() + " '" + slug + "'";
        if (!SLUG.matcher(slug).matches()) {
            op.getErrors().add(label + ", which is not a valid slug");
            return null;
        }
        String path = slugIndex.get(target).get(slug);
        if (path != null) {
            return path;
        }
        if (erroredSlugs.get(target).contains(slug)) {
            op.getErrors().add(label + ", whose own row has errors");
            return null;
        }
        if (!createStubs) {
            op.getErrors().add(label + ", which does not exist and is not defined in sheet " + target.getSheetName());
            return null;
        }
        WriteOp stub = new WriteOp(target, op.getSheet(), op.getRow(), slug, true);
        stub.setPath(folderPath(target) + "/" + slug);
        stub.setCreate(true);
        if (resolver.getResource(stub.getPath()) != null) {
            op.getErrors().add(label + ", but " + stub.getPath() + " is not a " + target.getModelName() + " fragment");
            return null;
        }
        stub.getValues().put(target.getSlugProperty(), slug);
        stub.getValues().put(target.getTitleProperty(), titleCase(slug));
        stub.getWarnings().add("Referenced here but not defined in sheet " + target.getSheetName()
                + "; fill in its remaining fields");
        stubs.add(stub);
        slugIndex.get(target).put(slug, stub.getPath());
        opsByPath.put(stub.getPath(), stub);
        return stub.getPath();
    }

    private void detectCycles(ImportPlan plan) {
        for (ModelSpec model : ModelSpec.values()) {
            FieldSpec parentField = model.getFields().stream()
                    .filter(f -> f.getType() == FieldType.FRAGMENT && f.getTargetModel() == model)
                    .findFirst().orElse(null);
            if (parentField == null) {
                continue;
            }
            Map<String, String> parentOf = new HashMap<>(existing.get(model).parentByPath);
            List<WriteOp> ops = opsOf(plan, model);
            for (WriteOp op : ops) {
                List<String> parent = references.getOrDefault(op, new HashMap<>()).get(parentField.getProperty());
                if (parent != null) {
                    parentOf.put(op.getPath(), parent.isEmpty() ? null : parent.get(0));
                }
            }
            for (WriteOp op : ops) {
                List<String> chain = new ArrayList<>();
                String current = op.getPath();
                while (current != null && !chain.contains(current)) {
                    chain.add(current);
                    current = parentOf.get(current);
                }
                if (op.getPath().equals(current)) {
                    chain.add(current);
                    op.getErrors().add("'" + parentField.getProperty() + "' makes a loop: " + chain.stream()
                            .map(p -> p.substring(p.lastIndexOf('/') + 1))
                            .collect(Collectors.joining(" -> ")));
                }
            }
        }
    }

    private void cascadeErrors(ImportPlan plan) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (WriteOp op : plan.getOps()) {
                if (op.hasErrors()) {
                    continue;
                }
                String failure = firstFailedReference(op);
                if (failure != null) {
                    op.getErrors().add(failure);
                    changed = true;
                }
            }
        }
    }

    private String firstFailedReference(WriteOp op) {
        for (Map.Entry<String, List<String>> reference : references.getOrDefault(op, new HashMap<>()).entrySet()) {
            for (String path : reference.getValue()) {
                WriteOp target = opsByPath.get(path);
                if (target != null && target != op && target.isCreate() && target.hasErrors()) {
                    return "'" + reference.getKey() + "' references " + path
                            + ", which will not be created because its row has errors";
                }
            }
        }
        return null;
    }

    private void allocateIds(ImportPlan plan) {
        for (ModelSpec model : ModelSpec.values()) {
            String idProperty = model.getIdProperty();
            if (idProperty == null) {
                continue;
            }
            ExistingFragments fragments = existing.get(model);
            long next = Math.max(fragments.maxId,
                    seenIds.get(model).stream().mapToLong(Long::longValue).max().orElse(0L)) + 1;
            for (WriteOp op : opsOf(plan, model)) {
                if (!op.isCreate() && fragments.idByPath.get(op.getPath()) != null) {
                    op.getValues().remove(idProperty);
                } else if (op.getValues().get(idProperty) == null) {
                    op.getValues().put(idProperty, next++);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void finalise(ImportPlan plan) {
        for (WriteOp op : plan.getOps()) {
            if (op.hasErrors()) {
                continue;
            }
            Map<String, List<String>> resolved = references.getOrDefault(op, new HashMap<>());
            for (FieldSpec field : op.getModel().getFields()) {
                String property = field.getProperty();
                if (field.getType().isReference() && resolved.containsKey(property)) {
                    List<String> paths = resolved.get(property);
                    op.getValues().put(property, field.getType() == FieldType.FRAGMENTS
                            ? paths.toArray(new String[0])
                            : (paths.isEmpty() ? null : paths.get(0)));
                } else if (field.getType() == FieldType.ASSET) {
                    Object value = op.getValues().get(property);
                    if (value instanceof String && ((String) value).startsWith("/")
                            && resolver.getResource((String) value) == null) {
                        op.getWarnings().add("'" + property + "': nothing found at " + value);
                    }
                }
            }
            Object title = op.getValues().get(op.getModel().getTitleProperty());
            if (title != null && !title.toString().isEmpty()) {
                op.setTitle(title.toString());
            } else if (op.isCreate()) {
                op.setTitle(op.getSlug());
            }
        }
    }

    private static List<WriteOp> opsOf(ImportPlan plan, ModelSpec model) {
        return plan.getOps().stream()
                .filter(op -> op.getModel() == model && !op.hasErrors())
                .collect(Collectors.toList());
    }

    private String folderPath(ModelSpec model) {
        return root + "/" + model.getFolder();
    }

    static String titleCase(String slug) {
        return Arrays.stream(slug.split("[-_]+"))
                .filter(s -> !s.isEmpty())
                .map(s -> s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1))
                .collect(Collectors.joining(" "));
    }

    /**
     * Fragments of one model already in its folder, indexed the ways the planner looks them up.
     */
    private static final class ExistingFragments {

        private final Map<String, String> bySlug = new HashMap<>();
        private final Map<Long, String> byId = new HashMap<>();
        private final Map<String, Long> idByPath = new HashMap<>();
        private final Map<String, String> parentByPath = new HashMap<>();
        private long maxId;

        static ExistingFragments load(ResourceResolver resolver, String folderPath, ModelSpec model) {
            ExistingFragments result = new ExistingFragments();
            Resource folder = resolver.getResource(folderPath);
            if (folder == null) {
                return result;
            }
            for (Resource child : folder.getChildren()) {
                Resource data = child.getChild("jcr:content/data");
                Resource master = data == null ? null : data.getChild("master");
                if (master == null || !model.getModelPath().equals(data.getValueMap().get("cq:model", String.class))) {
                    continue;
                }
                ValueMap values = master.getValueMap();
                String path = child.getPath();
                result.bySlug.putIfAbsent(child.getName(), path);
                String slug = values.get(model.getSlugProperty(), String.class);
                if (slug != null) {
                    result.bySlug.putIfAbsent(slug, path);
                }
                if (model.getIdProperty() != null) {
                    Long id = values.get(model.getIdProperty(), Long.class);
                    if (id != null) {
                        result.byId.put(id, path);
                        result.idByPath.put(path, id);
                        result.maxId = Math.max(result.maxId, id);
                    }
                }
                FieldSpec parent = model.getField("parent");
                if (parent != null && parent.getTargetModel() == model) {
                    result.parentByPath.put(path, values.get("parent", String.class));
                }
            }
            return result;
        }
    }
}
