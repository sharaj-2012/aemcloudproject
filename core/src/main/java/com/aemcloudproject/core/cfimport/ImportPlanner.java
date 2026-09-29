package com.aemcloudproject.core.cfimport;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Works out, for each converted row, the fragment it targets (the model's folder plus the slug)
 * and whether that fragment will be created or updated. Reads the repository; writes nothing.
 */
final class ImportPlanner {

    /** Lowercase letters and digits, words joined by single hyphens: safe as a node name and in URLs. */
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private ImportPlanner() {
    }

    static List<PlannedRow> plan(ResourceResolver resolver, ModelDefinition model, ImportTarget target,
                                 List<ConvertedRow> rows) throws ImportException {
        String slugField = target.getSlugField();
        if (!rows.isEmpty() && !hasColumn(rows.get(0), slugField)) {
            throw new ImportException("The sheet has no " + slugField + " column. It names each fragment, so it is required.");
        }
        Map<String, Integer> rowBySlug = new HashMap<>();
        List<PlannedRow> planned = new ArrayList<>();
        for (ConvertedRow row : rows) {
            List<String> problems = new ArrayList<>(row.getProblems());
            String slug = slug(row, slugField, problems);
            String path = null;
            PlannedRow.Action action = null;
            if (slug != null) {
                Integer earlierRow = rowBySlug.putIfAbsent(slug, row.getRowNumber());
                if (earlierRow != null) {
                    problems.add(slugField + ": '" + slug + "' is also used in row " + earlierRow
                            + "; each row must name a different fragment.");
                }
                path = target.getFolder() + "/" + slug;
                action = actionFor(resolver.getResource(path), model, problems);
            }
            planned.add(new PlannedRow(row, slug, path, title(row, target.getTitleField(), slug),
                    problems.isEmpty() ? action : null, problems));
        }
        return planned;
    }

    private static boolean hasColumn(ConvertedRow row, String field) {
        // A field whose value failed to convert is missing from the values but still has a problem.
        return row.getValues().containsKey(field)
                || row.getProblems().stream().anyMatch(problem -> problem.startsWith(field + ":"));
    }

    private static String slug(ConvertedRow row, String slugField, List<String> problems) {
        FieldValue value = row.getValues().get(slugField);
        if (value == null) {
            return null; // conversion failed; that problem is already listed
        }
        if (value.getKind() == FieldValue.Kind.BLANK) {
            problems.add(slugField + ": is blank, but it names the fragment.");
            return null;
        }
        if (value.getKind() == FieldValue.Kind.CLEAR) {
            problems.add(slugField + ": can't be cleared; it names the fragment.");
            return null;
        }
        String slug = String.valueOf(value.getValue());
        if (!SLUG.matcher(slug).matches()) {
            problems.add(slugField + ": '" + slug + "' can't name a fragment. Use lowercase letters, digits "
                    + "and single hyphens, like gourmet-dining-deal.");
            return null;
        }
        return slug;
    }

    /** CREATE if nothing is at the path, UPDATE if a fragment of the same model is, otherwise a problem. */
    private static PlannedRow.Action actionFor(Resource existing, ModelDefinition model, List<String> problems) {
        if (existing == null) {
            return PlannedRow.Action.CREATE;
        }
        String where = existing.getPath();
        String type = existing.getValueMap().get("jcr:primaryType", "");
        if (!"dam:Asset".equals(type)) {
            problems.add(where + " is already taken by a " + type + ", not a content fragment.");
            return null;
        }
        Resource content = existing.getChild("jcr:content");
        if (content == null || !content.getValueMap().get("contentFragment", false)) {
            problems.add(where + " is already taken by an asset that isn't a content fragment.");
            return null;
        }
        Resource data = content.getChild("data");
        String existingModel = data == null ? "" : data.getValueMap().get("cq:model", "");
        if (!model.getPath().equals(existingModel)) {
            String existingId = existingModel.substring(existingModel.lastIndexOf('/') + 1);
            problems.add(where + " is already a " + (existingId.isEmpty() ? "different" : existingId)
                    + " fragment, not " + model.getId() + ".");
            return null;
        }
        return PlannedRow.Action.UPDATE;
    }

    private static String title(ConvertedRow row, String titleField, String slug) {
        FieldValue value = row.getValues().get(titleField);
        return value != null && value.getKind() == FieldValue.Kind.VALUE ? String.valueOf(value.getValue()) : slug;
    }

    /** For the report: whether the model's folder exists or will be created on a real run. */
    static boolean folderExists(ResourceResolver resolver, ImportTarget target) {
        return resolver.getResource(target.getFolder()) != null;
    }
}
