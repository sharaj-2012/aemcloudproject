package com.aemcloudproject.core.services.impl;

import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.cq.dam.cfm.ContentFragmentException;
import com.adobe.cq.dam.cfm.DataType;
import com.adobe.cq.dam.cfm.ElementTemplate;
import com.adobe.cq.dam.cfm.FragmentData;
import com.adobe.cq.dam.cfm.FragmentTemplate;
import com.aemcloudproject.core.config.ScbCfBulkUploadConfiguration;
import com.aemcloudproject.core.dto.ScbSheetData;
import com.aemcloudproject.core.services.ScbContentFragmentWriterService;
import com.aemcloudproject.core.services.ScbModelProfile;
import com.aemcloudproject.core.utils.ScbCfBulkUploadUtil;
import com.day.cq.replication.ReplicationStatus;
import com.day.cq.wcm.commons.ReferenceSearch;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates, updates and deletes Content Fragments with the Content Fragment API, or runs the same
 * checks without writing anything in a dry run.
 */
@Component(service = ScbContentFragmentWriterService.class, configurationPid = ScbCfBulkUploadConfiguration.PID)
@Designate(ocd = ScbCfBulkUploadConfiguration.class)
public class ScbContentFragmentWriterServiceImpl implements ScbContentFragmentWriterService {

    private static final Logger log = LoggerFactory.getLogger(ScbContentFragmentWriterServiceImpl.class);

    private static final String ACTION = "action";
    private static final String SLUG = "slug";
    private static final String CREATE = "CREATE";
    private static final String DELETE = "DELETE";
    private static final String NAME_PATTERN = "[a-z0-9_-]+";
    private static final String CONTENT_PREFIX = "/content/";
    private static final int SHOWN_REFERENCES = 5;

    /** Former clearing token; now rejected because a blank cell clears a field. */
    private static final String CLEAR = "#CLEAR";

    private static final String PASS = "PASS";
    private static final String FAIL = "FAIL";
    private static final String SKIP = "SKIP";
    private static final String WARN = "WARN";

    private String modelsPath;

    @Reference(service = ScbModelProfile.class, cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY)
    private volatile List<ScbModelProfile> profiles;

    /**
     * Reads the models path from the configuration.
     *
     * @param config the CF bulk upload configuration
     */
    @Activate
    @Modified
    protected void activate(ScbCfBulkUploadConfiguration config) {
        modelsPath = StringUtils.removeEnd(config.modelsPath(), "/");
    }

    /**
     * {@inheritDoc}
     * Rows without an action are skipped; a sheet where no row has an action gets one SKIP result.
     * CREATE and UPDATE rows run first, sheet by sheet; DELETE rows run afterwards, sheets in reverse
     * profile order, so a fragment is checked for references after the other rows are written and
     * after the fragments that point to it are deleted. A CREATE row with an empty ID gets the next ID
     * after the highest ID in its sheet; the ID is used up only when the row passes. On UPDATE a blank
     * cell clears its field, except the ID field. Each row is committed on PASS in a real run and
     * reverted otherwise or in a dry run.
     */
    @Override
    public List<Map<String, Object>> scbWriteFragments(ResourceResolver resolver, Map<String, ScbSheetData> sheets,
                                                      String rootPath, boolean dryRun) {
        List<Map<String, Object>> results = new ArrayList<>();
        List<ScbModelProfile> deleteSheets = new ArrayList<>();

        for (Map.Entry<String, ScbSheetData> sheet : sheets.entrySet()) {
            String sheetName = sheet.getKey();

            ScbModelProfile profile = scbFindProfile(sheetName);
            if (profile == null) {
                results.add(scbResult(sheetName, null, null, null, FAIL,
                        "Sheet: \"" + sheetName + "\" is not a known CF model"));
                continue;
            }

            String modelPath = modelsPath + "/" + sheetName;
            Resource modelResource = resolver.getResource(modelPath);
            FragmentTemplate template = modelResource == null ? null : modelResource.adaptTo(FragmentTemplate.class);
            if (template == null) {
                results.add(scbResult(sheetName, null, null, null, FAIL, "Sheet: model not found at " + modelPath));
                continue;
            }
            List<Map<String, Object>> rows = sheet.getValue().getRows();
            if (rows.stream().noneMatch(row -> row.containsKey(ACTION))) {
                results.add(scbResult(sheetName, null, null, null, SKIP,
                        "Sheet: no row has an action (CREATE, UPDATE or DELETE)"));
                continue;
            }
            if (rows.stream().anyMatch(row -> DELETE.equals(row.get(ACTION)))) {
                deleteSheets.add(profile);
            }
            Map<String, DataType> fieldTypes = scbModelFieldTypes(template);
            Set<String> requiredFields = ScbCfBulkUploadUtil.scbRequiredFields(resolver, modelPath);

            List<Integer> rowNumbers = sheet.getValue().getRowNumbers();
            List<String> headers = sheet.getValue().getHeaders();
            long nextId = scbNextId(resolver, rows, profile, modelPath, rootPath);
            for (int i = 0; i < rows.size(); i++) {
                Map<String, Object> row = rows.get(i);
                if (!row.containsKey(ACTION) || DELETE.equals(row.get(ACTION))) {
                    continue;
                }
                int rowNumber = rowNumbers.get(i);
                boolean newId = profile.getIdField() != null && CREATE.equals(row.get(ACTION))
                        && !row.containsKey(profile.getIdField());
                if (newId) {
                    row.put(profile.getIdField(), String.valueOf(nextId));
                }
                Map<String, Object> result = scbFinishRow(resolver, scbWriteRow(resolver, sheetName, rowNumber,
                        profile, template, modelPath, rootPath, fieldTypes, requiredFields, headers, row, dryRun), dryRun);
                if (newId && PASS.equals(result.get("result"))) {
                    result.put("details", result.get("details") + " (" + profile.getIdField() + " " + nextId + ")");
                    nextId++;
                }
                results.add(result);
            }
        }

        if (!deleteSheets.isEmpty()) {
            deleteSheets.sort(Comparator.comparingInt(ScbModelProfile::getOrder).reversed());
            Map<String, Set<String>> references = scbFragmentReferences(resolver, rootPath);
            Set<String> deleted = new HashSet<>();
            for (ScbModelProfile profile : deleteSheets) {
                ScbSheetData data = sheets.get(profile.getModel());
                for (int i = 0; i < data.getRows().size(); i++) {
                    Map<String, Object> row = data.getRows().get(i);
                    if (DELETE.equals(row.get(ACTION))) {
                        results.add(scbFinishRow(resolver, scbDeleteRow(resolver, profile, data.getRowNumbers().get(i),
                                rootPath, row, references, deleted, dryRun), dryRun));
                    }
                }
            }
        }
        return results;
    }

    /**
     * Commits the row's changes when it passed in a real run, otherwise reverts them.
     *
     * @param resolver the author's resource resolver
     * @param result   the row's result
     * @param dryRun   {@code true} for a dry run
     * @return the result, or a FAIL result when the commit fails
     */
    private Map<String, Object> scbFinishRow(ResourceResolver resolver, Map<String, Object> result, boolean dryRun) {
        try {
            if (!dryRun && PASS.equals(result.get("result"))) {
                resolver.commit();
            } else {
                resolver.revert();
            }
            return result;
        } catch (PersistenceException e) {
            log.warn("Could not save {} row {}", result.get("sheet"), result.get("row"), e);
            resolver.revert();
            return scbResult((String) result.get("sheet"), (Integer) result.get("row"), (String) result.get("slug"),
                    (String) result.get("action"), FAIL, "Fragment: could not be saved (" + e.getMessage() + ")");
        }
    }

    /**
     * Finds the profile of a model.
     *
     * @param model the model (sheet) name
     * @return the profile, or {@code null} if no profile is configured for the model
     */
    private ScbModelProfile scbFindProfile(String model) {
        for (ScbModelProfile profile : profiles) {
            if (profile.getModel().equals(model)) {
                return profile;
            }
        }
        return null;
    }

    /**
     * Returns the ID for the first new row of a sheet: one more than the highest numeric ID in any
     * row of the sheet (with or without an action) or in any existing fragment of the model in its
     * folder, or the profile's start ID when there is none.
     *
     * @param resolver  the author's resource resolver
     * @param rows      the sheet rows, keyed by header
     * @param profile   the model's profile
     * @param modelPath the model path
     * @param rootPath  the folder picked on the page
     * @return the next ID, or 0 when the model has no ID field
     */
    private long scbNextId(ResourceResolver resolver, List<Map<String, Object>> rows, ScbModelProfile profile,
                           String modelPath, String rootPath) {
        String idField = profile.getIdField();
        if (idField == null) {
            return 0;
        }
        List<Object> ids = new ArrayList<>();
        rows.forEach(row -> ids.add(row.get(idField)));
        Resource folder = resolver.getResource(rootPath + "/" + profile.getFolder());
        if (folder != null) {
            for (Resource fragment : ScbCfBulkUploadUtil.scbFindFragments(folder)) {
                Resource master = fragment.getChild("jcr:content/data/master");
                String model = fragment.getChild("jcr:content/data").getValueMap().get("cq:model", String.class);
                if (master != null && modelPath.equals(model)) {
                    ids.add(master.getValueMap().get(idField));
                }
            }
        }
        long highest = profile.getStartId() - 1;
        for (Object id : ids) {
            if (id instanceof Number) {
                highest = Math.max(highest, ((Number) id).longValue());
            } else if (id instanceof String && ((String) id).trim().matches("\\d{1,18}")) {
                highest = Math.max(highest, Long.parseLong(((String) id).trim()));
            }
        }
        return highest + 1;
    }

    /**
     * Checks one row and, unless this is a dry run, creates or updates its fragment.
     * All checks (name, columns, required fields, existence and model, values) run before anything is written.
     * On UPDATE, fields whose column is in the sheet but blank are cleared (except the ID field), and
     * the ones that had a value are listed in the result. Does not commit; the caller commits or reverts.
     *
     * @param resolver   the author's resource resolver
     * @param sheetName  the sheet (model) name
     * @param rowNumber  the Excel row number
     * @param profile    the model's profile
     * @param template   the model
     * @param modelPath  the model path
     * @param rootPath   the folder picked on the page
     * @param fieldTypes     the model's field types, keyed by field name
     * @param requiredFields the fields marked Required in the model
     * @param headers        the sheet's column headers
     * @param row            the row values, keyed by header
     * @param dryRun         {@code true} to check only
     * @return a PASS result with the fragment path (and any cleared fields), or a FAIL result whose
     *         details start with the field name or {@code Fragment:}
     */
    private Map<String, Object> scbWriteRow(ResourceResolver resolver, String sheetName, int rowNumber,
                                            ScbModelProfile profile, FragmentTemplate template, String modelPath,
                                            String rootPath, Map<String, DataType> fieldTypes,
                                            Set<String> requiredFields, List<String> headers,
                                            Map<String, Object> row, boolean dryRun) {
        String action = (String) row.get(ACTION);
        String name = scbSlug(row);

        if (name == null || !name.matches(NAME_PATTERN)) {
            return scbResult(sheetName, rowNumber, name, action, FAIL, scbSlugProblem(row));
        }

        String unknownField = scbCheckFields(headers, fieldTypes);
        if (unknownField != null) {
            return scbResult(sheetName, rowNumber, name, action, FAIL,
                    unknownField + ": not a field of model " + sheetName);
        }

        boolean create = CREATE.equals(action);
        String missingRequired = scbCheckRequired(row, headers, requiredFields, create, profile.getIdField());
        if (missingRequired != null) {
            return scbResult(sheetName, rowNumber, name, action, FAIL, missingRequired);
        }

        String folderPath = rootPath + "/" + profile.getFolder();
        String fragmentPath = folderPath + "/" + name;
        Resource existing = resolver.getResource(fragmentPath);

        ContentFragment fragment = null;
        if (create) {
            if (existing != null) {
                return scbResult(sheetName, rowNumber, name, action, FAIL, "Fragment: already exists at " + fragmentPath);
            }
        } else {
            if (existing == null) {
                return scbResult(sheetName, rowNumber, name, action, FAIL, "Fragment: not found at " + fragmentPath);
            }
            String problem = scbCheckExisting(existing, modelPath);
            if (problem != null) {
                return scbResult(sheetName, rowNumber, name, action, FAIL, problem);
            }
            fragment = existing.adaptTo(ContentFragment.class);
        }

        Map<String, Object> values;
        try {
            values = scbPrepareValues(row, headers, fieldTypes, create, profile.getIdField());
        } catch (IllegalArgumentException e) {
            return scbResult(sheetName, rowNumber, name, action, FAIL, e.getMessage());
        }
        List<String> cleared = scbClearedFields(fragment, values);
        String details = cleared.isEmpty() ? fragmentPath
                : fragmentPath + " (cleared: " + String.join(", ", cleared) + ")";

        if (dryRun) {
            return scbResult(sheetName, rowNumber, name, action, PASS, details);
        }

        Object titleValue = profile.getTitleField() == null ? null : row.get(profile.getTitleField());
        String title = titleValue instanceof String ? (String) titleValue : null;
        try {
            if (fragment == null) {
                Resource folder = ScbCfBulkUploadUtil.scbGetOrCreateFolder(resolver, folderPath);
                fragment = template.createFragment(folder, name, title != null ? title : name);
            } else if (title != null) {
                fragment.setTitle(title);
            }
            scbSetFields(fragment, values, fieldTypes);
            return scbResult(sheetName, rowNumber, name, action, PASS, details);
        } catch (ContentFragmentException | PersistenceException | RuntimeException e) {
            log.warn("Could not write {} {}", sheetName, fragmentPath, e);
            return scbResult(sheetName, rowNumber, name, action, FAIL,
                    "Fragment: could not be written (" + e.getMessage() + ")");
        }
    }

    /**
     * Checks one DELETE row and, unless this is a dry run, deletes its fragment. A fragment that
     * other fragments under the picked folder or pages point to, or that is still published, is not
     * deleted and gets a WARN result. Fragments deleted earlier in the same run, dry or real, do not
     * count as references. Does not commit; the caller commits or reverts.
     *
     * @param resolver   the author's resource resolver
     * @param profile    the model's profile
     * @param rowNumber  the Excel row number
     * @param rootPath   the folder picked on the page
     * @param row        the row values, keyed by header
     * @param references the fragments under the picked folder that point to each path
     * @param deleted    the fragments deleted so far in this run; a passing row is added
     * @param dryRun     {@code true} to check only
     * @return a PASS result with the fragment path, a WARN result saying why it was not deleted,
     *         or a FAIL result
     */
    private Map<String, Object> scbDeleteRow(ResourceResolver resolver, ScbModelProfile profile, int rowNumber,
                                             String rootPath, Map<String, Object> row,
                                             Map<String, Set<String>> references, Set<String> deleted,
                                             boolean dryRun) {
        String sheetName = profile.getModel();
        String name = scbSlug(row);
        if (name == null || !name.matches(NAME_PATTERN)) {
            return scbResult(sheetName, rowNumber, name, DELETE, FAIL, scbSlugProblem(row));
        }
        String fragmentPath = rootPath + "/" + profile.getFolder() + "/" + name;
        Resource existing = resolver.getResource(fragmentPath);
        if (existing == null) {
            return scbResult(sheetName, rowNumber, name, DELETE, FAIL, "Fragment: not found at " + fragmentPath);
        }
        String problem = scbCheckExisting(existing, modelsPath + "/" + sheetName);
        if (problem != null) {
            return scbResult(sheetName, rowNumber, name, DELETE, FAIL, problem);
        }

        List<String> referencedBy = new ArrayList<>();
        for (String path : references.getOrDefault(fragmentPath, Collections.emptySet())) {
            if (!path.equals(fragmentPath) && !deleted.contains(path) && resolver.getResource(path) != null) {
                referencedBy.add(path);
            }
        }
        referencedBy.addAll(new ReferenceSearch().setExact(true).setHollow(true)
                .search(resolver, fragmentPath).keySet());
        List<String> reasons = new ArrayList<>();
        if (!referencedBy.isEmpty()) {
            String shown = String.join(", ", referencedBy.subList(0, Math.min(SHOWN_REFERENCES, referencedBy.size())));
            int more = referencedBy.size() - SHOWN_REFERENCES;
            reasons.add("referenced by " + shown + (more > 0 ? " and " + more + " more" : "")
                    + " (remove the references first)");
        }
        ReplicationStatus status = existing.adaptTo(ReplicationStatus.class);
        if (status != null && status.isActivated()) {
            reasons.add("still published (unpublish it first)");
        }
        if (!reasons.isEmpty()) {
            return scbResult(sheetName, rowNumber, name, DELETE, WARN, "Not deleted: " + String.join("; ", reasons));
        }

        if (dryRun) {
            deleted.add(fragmentPath);
            return scbResult(sheetName, rowNumber, name, DELETE, PASS, fragmentPath + " (to be deleted)");
        }
        try {
            resolver.delete(existing);
            deleted.add(fragmentPath);
            return scbResult(sheetName, rowNumber, name, DELETE, PASS, fragmentPath + " (deleted)");
        } catch (PersistenceException e) {
            log.warn("Could not delete {}", fragmentPath, e);
            return scbResult(sheetName, rowNumber, name, DELETE, FAIL,
                    "Fragment: could not be deleted (" + e.getMessage() + ")");
        }
    }

    /**
     * Collects, for every path stored in a fragment under the picked folder, the fragments that store it.
     *
     * @param resolver the author's resource resolver
     * @param rootPath the folder picked on the page
     * @return the referring fragment paths, keyed by the referenced path
     */
    private Map<String, Set<String>> scbFragmentReferences(ResourceResolver resolver, String rootPath) {
        Map<String, Set<String>> references = new HashMap<>();
        Resource root = resolver.getResource(rootPath);
        if (root == null) {
            return references;
        }
        for (Resource fragment : ScbCfBulkUploadUtil.scbFindFragments(root)) {
            Resource master = fragment.getChild("jcr:content/data/master");
            if (master == null) {
                continue;
            }
            for (Object value : master.getValueMap().values()) {
                Object[] values = value instanceof Object[] ? (Object[]) value : new Object[] {value};
                for (Object item : values) {
                    if (item instanceof String && ((String) item).startsWith(CONTENT_PREFIX)) {
                        references.computeIfAbsent((String) item, key -> new LinkedHashSet<>()).add(fragment.getPath());
                    }
                }
            }
        }
        return references;
    }

    /**
     * Checks that an existing resource is a Content Fragment of the expected model.
     *
     * @param existing  the resource at the fragment path
     * @param modelPath the model the sheet stands for
     * @return the problem, starting with {@code Fragment:}, or {@code null} when it fits
     */
    private String scbCheckExisting(Resource existing, String modelPath) {
        if (existing.adaptTo(ContentFragment.class) == null) {
            return "Fragment: " + existing.getPath() + " is not a content fragment";
        }
        Resource data = existing.getChild("jcr:content/data");
        String existingModel = data == null ? null : data.getValueMap().get("cq:model", String.class);
        if (!modelPath.equals(existingModel)) {
            return "Fragment: uses model " + existingModel + ", not " + modelPath;
        }
        return null;
    }

    /**
     * Returns the row's slug, the fragment's node name.
     *
     * @param row the row values, keyed by header
     * @return the slug, or {@code null} when the cell is empty or holds several lines
     */
    private String scbSlug(Map<String, Object> row) {
        Object value = row.get(SLUG);
        return value instanceof String ? (String) value : null;
    }

    /**
     * Describes an empty or invalid slug.
     *
     * @param row the row values, keyed by header
     * @return the message, e.g. {@code slug: "Summer Sale" must be lowercase letters, digits, - or _}
     */
    private String scbSlugProblem(Map<String, Object> row) {
        Object value = row.get(SLUG);
        return SLUG + ": \"" + (value == null ? "" : value) + "\" must be lowercase letters, digits, - or _";
    }

    /**
     * Converts every column of a row (except the action and slug) into the value to store. A blank cell
     * becomes {@code null} (clear the field) on UPDATE, except in the ID field; on CREATE it is left out.
     *
     * @param row        the row values, keyed by header; blank cells are absent
     * @param headers    the sheet's column headers
     * @param fieldTypes the model's field types, keyed by field name
     * @param create     {@code true} for a CREATE row
     * @param idField    the profile's ID field, or {@code null}
     * @return the values to store, keyed by field name; {@code null} means clear the field
     * @throws IllegalArgumentException for the first value that does not fit its field
     */
    private Map<String, Object> scbPrepareValues(Map<String, Object> row, List<String> headers,
                                                 Map<String, DataType> fieldTypes, boolean create, String idField) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String field : headers) {
            if (ACTION.equals(field) || SLUG.equals(field)) {
                continue;
            }
            Object value = row.get(field);
            if (value != null) {
                values.put(field, scbPrepareValue(field, fieldTypes.get(field), value));
            } else if (!create && !field.equals(idField)) {
                values.put(field, null);
            }
        }
        return values;
    }

    /**
     * Finds the fields a row would clear that currently have a value, and drops clears of fields
     * that are already empty so they are neither written nor reported.
     *
     * @param fragment the existing fragment, or {@code null} for a CREATE row
     * @param values   the prepared values; {@code null} entries for already-empty fields are removed
     * @return the fields that will be cleared, in column order
     */
    private List<String> scbClearedFields(ContentFragment fragment, Map<String, Object> values) {
        List<String> cleared = new ArrayList<>();
        if (fragment == null) {
            return cleared;
        }
        Iterator<Map.Entry<String, Object>> entries = values.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, Object> entry = entries.next();
            if (entry.getValue() != null) {
                continue;
            }
            ContentElement element = fragment.getElement(entry.getKey());
            Object current = element == null ? null : element.getValue().getValue();
            boolean empty = current == null
                    || (current instanceof String && StringUtils.isBlank((String) current))
                    || (current instanceof Object[] && ((Object[]) current).length == 0);
            if (empty) {
                entries.remove();
            } else {
                cleared.add(entry.getKey());
            }
        }
        return cleared;
    }

    /**
     * Converts one cell into the value to store in a field of the given type.
     *
     * @param field the field name, used in error messages
     * @param type  the field's data type
     * @param value the cell value: a {@code String} or a {@code List<String>}
     * @return a {@code String[]} for a multi-value text field, the text for a single text field,
     *         or the converted value for a typed field
     * @throws IllegalArgumentException if the value does not fit the field, or is the retired {@code #CLEAR}
     */
    private Object scbPrepareValue(String field, DataType type, Object value) {
        String typeName = type.getTypeString();

        if (CLEAR.equals(value)) {
            throw new IllegalArgumentException(field
                    + ": #CLEAR is no longer used \u2014 leave the cell empty to clear the field");
        }
        if (value instanceof List) {
            if (!type.isMultiValue()) {
                throw new IllegalArgumentException(field + ": takes one value, but the cell has several lines");
            }
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) value;
            return list.toArray(new String[0]);
        }
        String text = (String) value;
        if ("string".equals(typeName)) {
            return type.isMultiValue() ? new String[] {text} : text;
        }
        return scbConvert(field, typeName, text);
    }

    /**
     * Writes prepared values into the fragment. Single text fields keep their own content type
     * (plain or HTML); other values are set as fragment data. Fields not in {@code values} keep
     * their current value.
     *
     * @param fragment   the fragment to write
     * @param values     the prepared values, keyed by field name
     * @param fieldTypes the model's field types, keyed by field name
     * @throws ContentFragmentException if AEM rejects a value
     */
    private void scbSetFields(ContentFragment fragment, Map<String, Object> values, Map<String, DataType> fieldTypes)
            throws ContentFragmentException {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            ContentElement element = fragment.getElement(entry.getKey());
            DataType type = fieldTypes.get(entry.getKey());
            Object value = entry.getValue();

            if (value instanceof String && "string".equals(type.getTypeString()) && !type.isMultiValue()) {
                element.setContent((String) value, element.getContentType());
            } else {
                FragmentData data = element.getValue();
                data.setValue(value);
                element.setValue(data);
            }
        }
    }

    /**
     * Converts cell text to the Java type of a field: {@code long} (Integer), {@code double}
     * (Fraction), {@code boolean} (true/false, 1/0, yes/no) or {@code calendar}.
     *
     * @param field    the field name, used in error messages
     * @param typeName the type name reported by the Content Fragment API
     * @param text     the cell text
     * @return the converted value, or the text unchanged for any other type
     * @throws IllegalArgumentException if the text cannot be converted
     */
    private Object scbConvert(String field, String typeName, String text) {
        try {
            switch (typeName) {
                case "long":
                    return Long.parseLong(text);
                case "double":
                    return Double.parseDouble(text);
                case "boolean":
                    String lower = text.toLowerCase();
                    if ("true".equals(lower) || "1".equals(lower) || "yes".equals(lower)) {
                        return Boolean.TRUE;
                    }
                    if ("false".equals(lower) || "0".equals(lower) || "no".equals(lower)) {
                        return Boolean.FALSE;
                    }
                    throw new IllegalArgumentException(field + ": \"" + text + "\" must be true/false, 1/0 or yes/no");
                case "calendar":
                    return scbParseDate(text);
                default:
                    return text;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + ": \"" + text + "\" is not a "
                    + ("long".equals(typeName) ? "whole number" : "number"));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + ": \"" + text
                    + "\" is not a date (use e.g. 2026-10-01T00:00:00.000+05:30)");
        }
    }

    /**
     * Parses a date, a date-time with an offset (kept as written) or a date-time without one
     * (in the server's time zone).
     *
     * @param text the date text, e.g. {@code 2026-10-01T00:00:00.000+05:30}
     * @return the parsed date
     * @throws DateTimeParseException if the text is not a supported date
     */
    private Calendar scbParseDate(String text) {
        ZonedDateTime dateTime;
        if (text.length() == 10) {
            dateTime = LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault());
        } else if (text.matches(".*([+-]\\d{2}:\\d{2}|Z)$")) {
            dateTime = OffsetDateTime.parse(text).toZonedDateTime();
        } else {
            dateTime = LocalDateTime.parse(text).atZone(ZoneId.systemDefault());
        }
        return GregorianCalendar.from(dateTime);
    }

    /**
     * Checks the model's required fields: on CREATE each must have a value; on UPDATE a required
     * column in the sheet may not be blank, because a blank cell would clear it. The ID field is
     * exempt on UPDATE, since a blank ID keeps the current one.
     *
     * @param row            the row values, keyed by header; blank cells are absent
     * @param headers        the sheet's column headers
     * @param requiredFields the fields marked Required in the model
     * @param create         {@code true} for a CREATE row
     * @param idField        the profile's ID field, or {@code null}
     * @return the problem, e.g. {@code offerTitle: required}, or {@code null} when all are fine
     */
    private String scbCheckRequired(Map<String, Object> row, List<String> headers, Set<String> requiredFields,
                                    boolean create, String idField) {
        List<String> missing = new ArrayList<>();
        for (String field : requiredFields) {
            if (row.get(field) != null) {
                continue;
            }
            if (create || (headers.contains(field) && !field.equals(idField))) {
                missing.add(field);
            }
        }
        return missing.isEmpty() ? null : String.join(", ", missing) + ": required";
    }

    /**
     * Finds the first column (other than the action and slug) that is not a field of the model.
     *
     * @param headers    the sheet's column headers
     * @param fieldTypes the model's field types, keyed by field name
     * @return the unknown column name, or {@code null} if every column is a model field
     */
    private String scbCheckFields(List<String> headers, Map<String, DataType> fieldTypes) {
        for (String field : headers) {
            if (!ACTION.equals(field) && !SLUG.equals(field) && !fieldTypes.containsKey(field)) {
                return field;
            }
        }
        return null;
    }

    /**
     * Reads every field of a model with its data type.
     *
     * @param template the model
     * @return the data types keyed by field name
     */
    private Map<String, DataType> scbModelFieldTypes(FragmentTemplate template) {
        Map<String, DataType> types = new HashMap<>();
        Iterator<ElementTemplate> elements = template.getElements();
        while (elements.hasNext()) {
            ElementTemplate element = elements.next();
            types.put(element.getName(), element.getDataType());
        }
        return types;
    }

    /**
     * Builds one result entry, i.e. one line of the results table.
     *
     * @param sheet   the sheet name
     * @param row     the Excel row number, or {@code null} for a whole-sheet problem
     * @param slug    the fragment name, may be {@code null}
     * @param action  CREATE, UPDATE or DELETE, may be {@code null}
     * @param result  PASS, FAIL, WARN or SKIP
     * @param details the fragment path on PASS, the reason on WARN, the problem on FAIL
     * @return the result with the keys sheet, row, slug, action, result and details
     */
    private Map<String, Object> scbResult(String sheet, Integer row, String slug, String action, String result,
                                          String details) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("sheet", sheet);
        entry.put("row", row);
        entry.put("slug", slug);
        entry.put("action", action);
        entry.put("result", result);
        entry.put("details", details);
        return entry;
    }
}
