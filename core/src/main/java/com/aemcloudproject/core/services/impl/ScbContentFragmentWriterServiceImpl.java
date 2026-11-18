package com.aemcloudproject.core.services.impl;

import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.cq.dam.cfm.ContentFragmentException;
import com.adobe.cq.dam.cfm.DataType;
import com.adobe.cq.dam.cfm.ElementTemplate;
import com.adobe.cq.dam.cfm.FragmentData;
import com.adobe.cq.dam.cfm.FragmentTemplate;
import com.aemcloudproject.core.config.ScbOfferListingModel;
import com.aemcloudproject.core.dto.ScbSheetData;
import com.aemcloudproject.core.services.ScbContentFragmentWriterService;
import com.aemcloudproject.core.utils.ScbCfBulkUploadUtil;
import com.day.cq.replication.ReplicationStatus;
import com.day.cq.wcm.commons.ReferenceSearch;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.regex.Pattern;

/**
 * Creates, updates and deletes Content Fragments with the Content Fragment API, or runs the same
 * checks without writing anything in a dry run.
 */
@Component(service = ScbContentFragmentWriterService.class)
public class ScbContentFragmentWriterServiceImpl implements ScbContentFragmentWriterService {

    private static final Logger log = LoggerFactory.getLogger(ScbContentFragmentWriterServiceImpl.class);

    private static final String ACTION = "action";
    private static final String CREATE = "CREATE";
    private static final String DELETE = "DELETE";
    private static final String NAME_PATTERN = "[a-z0-9_-]+";
    private static final String CONTENT_PREFIX = "/content/";
    private static final int SHOWN_REFERENCES = 5;
    private static final Pattern OFFSET_AT_END = Pattern.compile("(?:[+-]\\d{2}:\\d{2}|Z)$");
    private static final Set<String> TRUE_VALUES = new HashSet<>(Arrays.asList("true", "1", "yes"));
    private static final Set<String> FALSE_VALUES = new HashSet<>(Arrays.asList("false", "0", "no"));

    private static final String PASS = "PASS";
    private static final String FAIL = "FAIL";
    private static final String SKIP = "SKIP";
    private static final String WARN = "WARN";

    /**
     * {@inheritDoc}
     * Rows without an action are skipped; a sheet where no row has an action gets one SKIP result.
     * CREATE and UPDATE rows run first, sheet by sheet; DELETE rows run afterwards, sheets in reverse
     * model order, so a fragment is checked for references after the other rows are written and
     * after the fragments that point to it are deleted. A CREATE row with an empty ID gets the next ID
     * after the highest ID in its sheet; the ID is used up only when the row passes. On UPDATE a blank
     * cell clears its field, except the ID field. Each row is committed on PASS in a real run and
     * reverted otherwise or in a dry run.
     */
    @Override
    public List<Map<String, Object>> scbWriteFragments(ResourceResolver resolver, Map<String, ScbSheetData> sheets,
                                                      String rootPath, boolean dryRun) {
        ScbRun run = new ScbRun(resolver, rootPath, dryRun);
        List<Map<String, Object>> results = new ArrayList<>();
        List<ScbOfferListingModel> deleteSheets = new ArrayList<>();
        for (Map.Entry<String, ScbSheetData> sheet : sheets.entrySet()) {
            ScbOfferListingModel model = scbWriteSheet(run, sheet.getKey(), sheet.getValue(), results);
            boolean hasDelete = sheet.getValue().getRows().stream().anyMatch(row -> DELETE.equals(row.get(ACTION)));
            if (model != null && hasDelete) {
                deleteSheets.add(model);
            }
        }
        scbDeleteSheets(run, sheets, deleteSheets, results);
        return results;
    }

    /**
     * Writes the CREATE and UPDATE rows of one sheet, or adds one FAIL or SKIP result for the whole sheet
     * when its name is not a model, its model is missing, or no row has an action.
     *
     * @param run       the run settings
     * @param sheetName the sheet name
     * @param data      the sheet's headers and rows
     * @param results   the results; one is added per row written
     * @return the sheet's model, or {@code null} when the sheet was not written
     */
    private ScbOfferListingModel scbWriteSheet(ScbRun run, String sheetName, ScbSheetData data,
                                               List<Map<String, Object>> results) {
        ScbOfferListingModel model = ScbOfferListingModel.scbFromSheet(sheetName);
        if (model == null) {
            results.add(scbResult(sheetName, null, null, null, FAIL,
                    "Sheet: \"" + sheetName + "\" is not a known CF model"));
            return null;
        }
        Resource modelResource = run.resolver.getResource(model.getModelPath());
        FragmentTemplate template = modelResource == null ? null : modelResource.adaptTo(FragmentTemplate.class);
        if (template == null) {
            results.add(scbResult(sheetName, null, null, null, FAIL,
                    "Sheet: model not found at " + model.getModelPath()));
            return null;
        }
        List<Map<String, Object>> rows = data.getRows();
        if (rows.stream().noneMatch(row -> row.containsKey(ACTION))) {
            results.add(scbResult(sheetName, null, null, null, SKIP,
                    "Sheet: no row has an action (CREATE, UPDATE or DELETE)"));
            return null;
        }
        ScbSheet sheet = new ScbSheet(model, template, scbModelFieldTypes(template), data.getHeaders());
        long nextId = scbNextId(run.resolver, rows, model, run.rootPath);
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            if (row.containsKey(ACTION) && !DELETE.equals(row.get(ACTION))) {
                nextId = scbWriteSheetRow(run, sheet, data.getRowNumbers().get(i), row, nextId, results);
            }
        }
        return model;
    }

    /**
     * Writes one CREATE or UPDATE row and adds its result. A CREATE row with an empty ID gets
     * {@code nextId}, which is used up only when the row passes.
     *
     * @param run       the run settings
     * @param sheet     the sheet's model, template, field types and headers
     * @param rowNumber the Excel row number
     * @param row       the row values, keyed by header
     * @param nextId    the ID for the next new row
     * @param results   the results; the row's result is added
     * @return the ID for the following new row
     */
    private long scbWriteSheetRow(ScbRun run, ScbSheet sheet, int rowNumber, Map<String, Object> row, long nextId,
                                  List<Map<String, Object>> results) {
        String idField = sheet.model.getIdField();
        boolean newId = idField != null && CREATE.equals(row.get(ACTION)) && !row.containsKey(idField);
        if (newId) {
            row.put(idField, String.valueOf(nextId));
        }
        Map<String, Object> result = scbFinishRow(run, scbWriteRow(run, sheet, rowNumber, row));
        results.add(result);
        if (newId && PASS.equals(result.get("result"))) {
            result.put("details", result.get("details") + " (" + idField + " " + nextId + ")");
            return nextId + 1;
        }
        return nextId;
    }

    /**
     * Runs the DELETE rows of the given sheets, sheets in reverse model order, and adds their results.
     *
     * @param run          the run settings; its references are collected here
     * @param sheets       the parsed workbook, keyed by sheet name
     * @param deleteSheets the models whose sheets have DELETE rows
     * @param results      the results; one is added per DELETE row
     */
    private void scbDeleteSheets(ScbRun run, Map<String, ScbSheetData> sheets,
                                 List<ScbOfferListingModel> deleteSheets, List<Map<String, Object>> results) {
        if (deleteSheets.isEmpty()) {
            return;
        }
        deleteSheets.sort(Comparator.reverseOrder());
        run.references = scbFragmentReferences(run.resolver, run.rootPath);
        for (ScbOfferListingModel model : deleteSheets) {
            ScbSheetData data = sheets.get(model.getModel());
            for (int i = 0; i < data.getRows().size(); i++) {
                Map<String, Object> row = data.getRows().get(i);
                if (DELETE.equals(row.get(ACTION))) {
                    results.add(scbFinishRow(run, scbDeleteRow(run, model, data.getRowNumbers().get(i), row)));
                }
            }
        }
    }

    /**
     * Commits the row's changes when it passed in a real run, otherwise reverts them.
     *
     * @param run    the run settings
     * @param result the row's result
     * @return the result, or a FAIL result when the commit fails
     */
    private Map<String, Object> scbFinishRow(ScbRun run, Map<String, Object> result) {
        try {
            if (!run.dryRun && PASS.equals(result.get("result"))) {
                run.resolver.commit();
            } else {
                run.resolver.revert();
            }
            return result;
        } catch (PersistenceException e) {
            log.warn("Could not save {} row {}", result.get("sheet"), result.get("row"), e);
            run.resolver.revert();
            return scbResult((String) result.get("sheet"), (Integer) result.get("row"), (String) result.get("slug"),
                    (String) result.get("action"), FAIL, "Fragment: could not be saved (" + e.getMessage() + ")");
        }
    }

    /**
     * Returns the ID for the first new row of a sheet: one more than the highest numeric ID in any
     * row of the sheet (with or without an action) or in any existing fragment of the model in its
     * folder, or the model's start ID when there is none.
     *
     * @param resolver the author's resource resolver
     * @param rows     the sheet rows, keyed by header
     * @param model    the sheet's model
     * @param rootPath the folder picked on the page
     * @return the next ID, or 0 when the model has no ID field
     */
    private long scbNextId(ResourceResolver resolver, List<Map<String, Object>> rows, ScbOfferListingModel model,
                           String rootPath) {
        String idField = model.getIdField();
        if (idField == null) {
            return 0;
        }
        List<Object> ids = new ArrayList<>();
        rows.forEach(row -> ids.add(row.get(idField)));
        Resource folder = resolver.getResource(rootPath + "/" + model.getFolder());
        if (folder != null) {
            for (Resource fragment : ScbCfBulkUploadUtil.scbFindFragments(folder)) {
                Resource master = fragment.getChild("jcr:content/data/master");
                if (master != null && model.getModelPath().equals(ScbCfBulkUploadUtil.scbModelOf(fragment))) {
                    ids.add(master.getValueMap().get(idField));
                }
            }
        }
        long highest = model.getStartId() - 1;
        for (Object id : ids) {
            Long value = ScbCfBulkUploadUtil.scbParseId(id);
            if (value != null) {
                highest = Math.max(highest, value);
            }
        }
        return highest + 1;
    }

    /**
     * Checks one row and, unless this is a dry run, creates or updates its fragment.
     * All checks (slug, columns, required fields, existence and model, values) run before anything is written.
     * On UPDATE, fields whose column is in the sheet but blank are cleared (except the ID field), and
     * the ones that had a value are listed in the result. Does not commit; the caller commits or reverts.
     *
     * @param run       the run settings
     * @param sheet     the sheet's model, template, field types and headers
     * @param rowNumber the Excel row number
     * @param row       the row values, keyed by header
     * @return a PASS result with the fragment path (and any cleared fields), or a FAIL result whose
     *         details start with the field name or {@code Fragment:}
     */
    private Map<String, Object> scbWriteRow(ScbRun run, ScbSheet sheet, int rowNumber, Map<String, Object> row) {
        ScbOfferListingModel model = sheet.model;
        String sheetName = model.getModel();
        String action = (String) row.get(ACTION);
        String name = scbSlug(row, model.getSlugColumn());
        boolean create = CREATE.equals(action);
        String fragmentPath = run.rootPath + "/" + model.getFolder() + "/" + name;

        String problem = scbCheckRow(run, sheet, row, create, fragmentPath);
        if (problem != null) {
            return scbResult(sheetName, rowNumber, name, action, FAIL, problem);
        }
        ContentFragment fragment = create ? null
                : run.resolver.getResource(fragmentPath).adaptTo(ContentFragment.class);

        Map<String, Object> values;
        try {
            values = scbPrepareValues(row, sheet.headers, sheet.fieldTypes, create, model.getIdField(),
                    model.getSlugColumn());
        } catch (IllegalArgumentException e) {
            return scbResult(sheetName, rowNumber, name, action, FAIL, e.getMessage());
        }
        List<String> cleared = scbClearedFields(fragment, values);
        String details = cleared.isEmpty() ? fragmentPath
                : fragmentPath + " (cleared: " + String.join(", ", cleared) + ")";

        if (run.dryRun) {
            return scbResult(sheetName, rowNumber, name, action, PASS, details);
        }
        try {
            scbSaveFragment(run, sheet, fragment, name, row, values);
            return scbResult(sheetName, rowNumber, name, action, PASS, details);
        } catch (ContentFragmentException | PersistenceException | RuntimeException e) {
            log.warn("Could not write {} {}", sheetName, fragmentPath, e);
            return scbResult(sheetName, rowNumber, name, action, FAIL,
                    "Fragment: could not be written (" + e.getMessage() + ")");
        }
    }

    /**
     * Checks a CREATE or UPDATE row before its values are read: the slug, the columns, the required fields,
     * and that the fragment does not exist (CREATE) or exists with the sheet's model (UPDATE).
     *
     * @param run          the run settings
     * @param sheet        the sheet's model, template, field types and headers
     * @param row          the row values, keyed by header
     * @param create       {@code true} for a CREATE row
     * @param fragmentPath the fragment path built from the slug
     * @return the problem, or {@code null} when the row can be written
     */
    private String scbCheckRow(ScbRun run, ScbSheet sheet, Map<String, Object> row, boolean create,
                               String fragmentPath) {
        ScbOfferListingModel model = sheet.model;
        String problem = scbCheckSlug(row, model.getSlugColumn());
        if (problem != null) {
            return problem;
        }
        String unknownField = scbCheckFields(sheet.headers, sheet.fieldTypes, model.getSlugColumn());
        if (unknownField != null) {
            return unknownField + ": not a field of model " + model.getModel();
        }
        problem = scbCheckRequired(row, sheet.headers, model.getRequiredFields(), create, model.getIdField());
        if (problem != null) {
            return problem;
        }
        Resource existing = run.resolver.getResource(fragmentPath);
        if (create) {
            return existing == null ? null : "Fragment: already exists at " + fragmentPath;
        }
        return scbCheckExisting(existing, fragmentPath, model.getModelPath());
    }

    /**
     * Creates the fragment (and a missing model folder) or sets the title of the existing one, then
     * writes the values. Does not commit.
     *
     * @param run      the run settings
     * @param sheet    the sheet's model, template, field types and headers
     * @param fragment the existing fragment, or {@code null} to create it
     * @param name     the fragment name (slug)
     * @param row      the row values, keyed by header, for the title
     * @param values   the prepared field values
     * @throws ContentFragmentException if the fragment cannot be created or written
     * @throws PersistenceException     if the model folder cannot be created
     */
    private void scbSaveFragment(ScbRun run, ScbSheet sheet, ContentFragment fragment, String name,
                                 Map<String, Object> row, Map<String, Object> values)
            throws ContentFragmentException, PersistenceException {
        Object titleValue = row.get(sheet.model.getTitleField());
        String title = titleValue instanceof String ? (String) titleValue : null;
        ContentFragment target = fragment;
        if (target == null) {
            Resource folder = ScbCfBulkUploadUtil.scbGetOrCreateFolder(run.resolver,
                    run.rootPath + "/" + sheet.model.getFolder());
            target = sheet.template.createFragment(folder, name, title != null ? title : name);
        } else if (title != null) {
            target.setTitle(title);
        }
        scbSetFields(target, values, sheet.fieldTypes);
    }

    /**
     * Checks one DELETE row and, unless this is a dry run, deletes its fragment. A fragment that
     * other fragments under the picked folder or pages point to, or that is still published, is not
     * deleted and gets a WARN result. Fragments deleted earlier in the same run, dry or real, do not
     * count as references. Does not commit; the caller commits or reverts.
     *
     * @param run       the run settings, with the references and the fragments deleted so far
     * @param model     the sheet's model
     * @param rowNumber the Excel row number
     * @param row       the row values, keyed by header
     * @return a PASS result with the fragment path, a WARN result saying why it was not deleted,
     *         or a FAIL result
     */
    private Map<String, Object> scbDeleteRow(ScbRun run, ScbOfferListingModel model, int rowNumber,
                                             Map<String, Object> row) {
        String sheetName = model.getModel();
        String name = scbSlug(row, model.getSlugColumn());
        String fragmentPath = run.rootPath + "/" + model.getFolder() + "/" + name;
        String problem = scbCheckSlug(row, model.getSlugColumn());
        Resource existing = problem == null ? run.resolver.getResource(fragmentPath) : null;
        if (problem == null) {
            problem = scbCheckExisting(existing, fragmentPath, model.getModelPath());
        }
        if (problem != null) {
            return scbResult(sheetName, rowNumber, name, DELETE, FAIL, problem);
        }
        List<String> reasons = scbDeleteBlockers(run, existing, fragmentPath);
        if (!reasons.isEmpty()) {
            return scbResult(sheetName, rowNumber, name, DELETE, WARN, "Not deleted: " + String.join("; ", reasons));
        }
        if (run.dryRun) {
            run.deleted.add(fragmentPath);
            return scbResult(sheetName, rowNumber, name, DELETE, PASS, fragmentPath + " (to be deleted)");
        }
        try {
            run.resolver.delete(existing);
            run.deleted.add(fragmentPath);
            return scbResult(sheetName, rowNumber, name, DELETE, PASS, fragmentPath + " (deleted)");
        } catch (PersistenceException e) {
            log.warn("Could not delete {}", fragmentPath, e);
            return scbResult(sheetName, rowNumber, name, DELETE, FAIL,
                    "Fragment: could not be deleted (" + e.getMessage() + ")");
        }
    }

    /**
     * Lists why a fragment must not be deleted: other fragments under the picked folder or pages point
     * to it (showing at most five), or it is still published.
     *
     * @param run          the run settings, with the references and the fragments deleted so far
     * @param existing     the fragment
     * @param fragmentPath the fragment path
     * @return the reasons, empty when it can be deleted
     */
    private List<String> scbDeleteBlockers(ScbRun run, Resource existing, String fragmentPath) {
        List<String> referencedBy = new ArrayList<>();
        for (String path : run.references.getOrDefault(fragmentPath, Collections.emptySet())) {
            if (!path.equals(fragmentPath) && !run.deleted.contains(path) && run.resolver.getResource(path) != null) {
                referencedBy.add(path);
            }
        }
        referencedBy.addAll(scbPageReferences(run.resolver, fragmentPath));
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
        return reasons;
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
     * Finds the pages that point to a fragment.
     *
     * @param resolver     the author's resource resolver
     * @param fragmentPath the fragment path
     * @return the paths of the referencing pages
     */
    Set<String> scbPageReferences(ResourceResolver resolver, String fragmentPath) {
        return new ReferenceSearch().setExact(true).setHollow(true).search(resolver, fragmentPath).keySet();
    }

    /**
     * Checks that an UPDATE or DELETE row's fragment exists and is a Content Fragment of the sheet's model.
     *
     * @param existing     the resource at the fragment path, or {@code null}
     * @param fragmentPath the fragment path, for the message
     * @param modelPath    the model the sheet stands for
     * @return the problem, starting with {@code Fragment:}, or {@code null} when it fits
     */
    private String scbCheckExisting(Resource existing, String fragmentPath, String modelPath) {
        if (existing == null) {
            return "Fragment: not found at " + fragmentPath;
        }
        if (existing.adaptTo(ContentFragment.class) == null) {
            return "Fragment: " + fragmentPath + " is not a content fragment";
        }
        String existingModel = ScbCfBulkUploadUtil.scbModelOf(existing);
        if (!modelPath.equals(existingModel)) {
            return "Fragment: uses model " + existingModel + ", not " + modelPath;
        }
        return null;
    }

    /**
     * Returns the row's slug, the fragment's node name.
     *
     * @param row        the row values, keyed by header
     * @param slugColumn the model's slug column, e.g. {@code offerSlug}
     * @return the slug, or {@code null} when the cell is empty or holds several lines
     */
    private String scbSlug(Map<String, Object> row, String slugColumn) {
        Object value = row.get(slugColumn);
        return value instanceof String ? (String) value : null;
    }

    /**
     * Checks the row's slug.
     *
     * @param row        the row values, keyed by header
     * @param slugColumn the model's slug column, e.g. {@code offerSlug}
     * @return the problem, e.g. {@code offerSlug: "Summer Sale" must be lowercase letters, digits, - or _},
     *         or {@code null} when the slug is valid
     */
    private String scbCheckSlug(Map<String, Object> row, String slugColumn) {
        String slug = scbSlug(row, slugColumn);
        if (slug != null && slug.matches(NAME_PATTERN)) {
            return null;
        }
        Object value = row.get(slugColumn);
        return slugColumn + ": \"" + (value == null ? "" : value) + "\" must be lowercase letters, digits, - or _";
    }

    /**
     * Converts every column of a row (except the action and slug) into the value to store. A blank cell
     * becomes {@code null} (clear the field) on UPDATE, except in the ID field; on CREATE it is left out.
     *
     * @param row        the row values, keyed by header; blank cells are absent
     * @param headers    the sheet's column headers
     * @param fieldTypes the model's field types, keyed by field name
     * @param create     {@code true} for a CREATE row
     * @param idField    the model's ID field, or {@code null}
     * @param slugColumn the model's slug column, e.g. {@code offerSlug}
     * @return the values to store, keyed by field name; {@code null} means clear the field
     * @throws IllegalArgumentException for the first value that does not fit its field
     */
    private Map<String, Object> scbPrepareValues(Map<String, Object> row, List<String> headers,
                                                 Map<String, DataType> fieldTypes, boolean create, String idField,
                                                 String slugColumn) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String field : headers) {
            if (ACTION.equals(field) || slugColumn.equals(field)) {
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
     * @throws IllegalArgumentException if the value does not fit the field
     */
    private Object scbPrepareValue(String field, DataType type, Object value) {
        String typeName = type.getTypeString();

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
                    return scbParseBoolean(field, text);
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
     * Reads a yes/no cell: true/false, 1/0 or yes/no, in any case.
     *
     * @param field the field name, used in the error message
     * @param text  the cell text
     * @return the value
     * @throws IllegalArgumentException if the text is none of those
     */
    private Boolean scbParseBoolean(String field, String text) {
        String lower = text.toLowerCase();
        if (TRUE_VALUES.contains(lower)) {
            return Boolean.TRUE;
        }
        if (FALSE_VALUES.contains(lower)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException(field + ": \"" + text + "\" must be true/false, 1/0 or yes/no");
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
        } else if (OFFSET_AT_END.matcher(text).find()) {
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
     * @param requiredFields the model's required fields
     * @param create         {@code true} for a CREATE row
     * @param idField        the model's ID field, or {@code null}
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
     * @param slugColumn the model's slug column, e.g. {@code offerSlug}
     * @return the unknown column name, or {@code null} if every column is a model field
     */
    private String scbCheckFields(List<String> headers, Map<String, DataType> fieldTypes, String slugColumn) {
        for (String field : headers) {
            if (!ACTION.equals(field) && !slugColumn.equals(field) && !fieldTypes.containsKey(field)) {
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

    /**
     * The settings shared by every row of one upload, plus what the DELETE rows collect along the way.
     */
    private static final class ScbRun {

        private final ResourceResolver resolver;
        private final String rootPath;
        private final boolean dryRun;
        private final Set<String> deleted = new HashSet<>();
        private Map<String, Set<String>> references = Collections.emptyMap();

        private ScbRun(ResourceResolver resolver, String rootPath, boolean dryRun) {
            this.resolver = resolver;
            this.rootPath = rootPath;
            this.dryRun = dryRun;
        }
    }

    /**
     * One sheet's model, its template and field types, and the sheet's column headers.
     */
    private static final class ScbSheet {

        private final ScbOfferListingModel model;
        private final FragmentTemplate template;
        private final Map<String, DataType> fieldTypes;
        private final List<String> headers;

        private ScbSheet(ScbOfferListingModel model, FragmentTemplate template, Map<String, DataType> fieldTypes,
                         List<String> headers) {
            this.model = model;
            this.template = template;
            this.fieldTypes = fieldTypes;
            this.headers = headers;
        }
    }
}
