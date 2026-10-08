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
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
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
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates and updates Content Fragments with the Content Fragment API, or runs the same
 * checks without writing anything in a dry run.
 */
@Component(service = ScbContentFragmentWriterService.class, configurationPid = ScbCfBulkUploadConfiguration.PID)
@Designate(ocd = ScbCfBulkUploadConfiguration.class)
public class ScbContentFragmentWriterServiceImpl implements ScbContentFragmentWriterService {

    private static final Logger log = LoggerFactory.getLogger(ScbContentFragmentWriterServiceImpl.class);

    private static final String FOLDER_TYPE = "sling:OrderedFolder";
    private static final String ACTION = "action";
    private static final String NAME_PATTERN = "[a-z0-9_-]+";

    /**
     * Per-model settings: the folder its fragments go in and the columns that give the
     * fragment name and title.
     */
    private static final class Profile {
        final String folder;
        final String nameField;
        final String titleField;

        Profile(String folder, String nameField, String titleField) {
            this.folder = folder;
            this.nameField = nameField;
            this.titleField = titleField;
        }
    }

    /** Profiles keyed by model (and sheet) name. */
    private static final Map<String, Profile> PROFILES;
    static {
        Map<String, Profile> profiles = new HashMap<>();
        profiles.put("offer-detail", new Profile("offer-listing/offers", "offerSlug", "offerTitle"));
        profiles.put("category", new Profile("offer-listing/categories", "offerCategorySlug", "name"));
        profiles.put("merchant-details", new Profile("offer-listing/merchants", "merchantSlug", "merchantName"));
        profiles.put("merchant-venue", new Profile("offer-listing/merchants/venues", "merchantVenueSlug", "name"));
        profiles.put("offer-card", new Profile("offer-listing/cards", "offerCardSlug", "name"));
        profiles.put("offer-cta", new Profile("offer-listing/offer-cta", "offerCtaSlug", "label"));
        PROFILES = Collections.unmodifiableMap(profiles);
    }

    /** Cell value that empties a field. */
    private static final String CLEAR = "#CLEAR";

    private static final String PASS = "PASS";
    private static final String FAIL = "FAIL";

    private String fragmentRootPath;
    private String modelsPath;

    /**
     * Reads the fragment root and models paths from the configuration.
     *
     * @param config the CF bulk upload configuration
     */
    @Activate
    @Modified
    protected void activate(ScbCfBulkUploadConfiguration config) {
        fragmentRootPath = StringUtils.removeEnd(config.fragmentRootPath(), "/");
        modelsPath = StringUtils.removeEnd(config.modelsPath(), "/");
    }

    /**
     * {@inheritDoc}
     * Each row is committed on PASS in a real run and reverted on FAIL or in a dry run.
     */
    @Override
    public List<Map<String, Object>> scbWriteFragments(ResourceResolver resolver, Map<String, ScbSheetData> sheets,
                                                      boolean dryRun) {
        List<Map<String, Object>> results = new ArrayList<>();

        for (Map.Entry<String, ScbSheetData> sheet : sheets.entrySet()) {
            String sheetName = sheet.getKey();

            Profile profile = PROFILES.get(sheetName);
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
            Map<String, DataType> fieldTypes = scbModelFieldTypes(template);

            List<Map<String, Object>> rows = sheet.getValue().getRows();
            List<Integer> rowNumbers = sheet.getValue().getRowNumbers();
            for (int i = 0; i < rows.size(); i++) {
                Map<String, Object> row = rows.get(i);
                int rowNumber = rowNumbers.get(i);
                Map<String, Object> result = scbWriteRow(resolver, sheetName, rowNumber, profile, template,
                        modelPath, fieldTypes, row, dryRun);
                try {
                    if (dryRun || FAIL.equals(result.get("result"))) {
                        resolver.revert();
                    } else {
                        resolver.commit();
                    }
                } catch (PersistenceException e) {
                    log.warn("Could not save {} row {}", sheetName, rowNumber, e);
                    resolver.revert();
                    result = scbResult(sheetName, rowNumber, (String) row.get(profile.nameField),
                            (String) row.get(ACTION), FAIL, "Fragment: could not be saved (" + e.getMessage() + ")");
                }
                results.add(result);
            }
        }
        return results;
    }

    /**
     * Checks one row and, unless this is a dry run, creates or updates its fragment.
     * All checks (name, columns, existence and model, values) run before anything is written.
     * Does not commit; the caller commits or reverts.
     *
     * @param resolver   the author's resource resolver
     * @param sheetName  the sheet (model) name
     * @param rowNumber  the Excel row number
     * @param profile    the model's profile
     * @param template   the model
     * @param modelPath  the model path
     * @param fieldTypes the model's field types, keyed by field name
     * @param row        the row values, keyed by header
     * @param dryRun     {@code true} to check only
     * @return a PASS result with the fragment path, or a FAIL result whose details start
     *         with the field name or {@code Fragment:}
     */
    private Map<String, Object> scbWriteRow(ResourceResolver resolver, String sheetName, int rowNumber, Profile profile,
                                            FragmentTemplate template, String modelPath, Map<String, DataType> fieldTypes,
                                            Map<String, Object> row, boolean dryRun) {
        String action = (String) row.get(ACTION);
        Object nameValue = row.get(profile.nameField);
        String name = nameValue instanceof String ? (String) nameValue : null;

        if (name == null || !name.matches(NAME_PATTERN)) {
            return scbResult(sheetName, rowNumber, name, action, FAIL,
                    profile.nameField + ": \"" + (nameValue == null ? "" : nameValue)
                            + "\" must be lowercase letters, digits, - or _");
        }

        String unknownField = scbCheckFields(row, fieldTypes);
        if (unknownField != null) {
            return scbResult(sheetName, rowNumber, name, action, FAIL,
                    unknownField + ": not a field of model " + sheetName);
        }

        String folderPath = fragmentRootPath + "/" + profile.folder;
        String fragmentPath = folderPath + "/" + name;
        Resource existing = resolver.getResource(fragmentPath);

        ContentFragment fragment = null;
        if ("CREATE".equals(action)) {
            if (existing != null) {
                return scbResult(sheetName, rowNumber, name, action, FAIL, "Fragment: already exists at " + fragmentPath);
            }
        } else {
            if (existing == null) {
                return scbResult(sheetName, rowNumber, name, action, FAIL, "Fragment: not found at " + fragmentPath);
            }
            fragment = existing.adaptTo(ContentFragment.class);
            if (fragment == null) {
                return scbResult(sheetName, rowNumber, name, action, FAIL,
                        "Fragment: " + fragmentPath + " is not a content fragment");
            }
            Resource data = existing.getChild("jcr:content/data");
            String existingModel = data == null ? null : data.getValueMap().get("cq:model", String.class);
            if (!modelPath.equals(existingModel)) {
                return scbResult(sheetName, rowNumber, name, action, FAIL,
                        "Fragment: uses model " + existingModel + ", not " + modelPath);
            }
        }

        Map<String, Object> values;
        try {
            values = scbPrepareValues(row, fieldTypes);
        } catch (IllegalArgumentException e) {
            return scbResult(sheetName, rowNumber, name, action, FAIL, e.getMessage());
        }

        if (dryRun) {
            return scbResult(sheetName, rowNumber, name, action, PASS, fragmentPath);
        }

        String title = row.get(profile.titleField) instanceof String ? (String) row.get(profile.titleField) : null;
        try {
            if (fragment == null) {
                Resource folder = scbGetOrCreateFolder(resolver, folderPath);
                fragment = template.createFragment(folder, name, title != null ? title : name);
            } else if (title != null) {
                fragment.setTitle(title);
            }
            scbSetFields(fragment, values, fieldTypes);
            return scbResult(sheetName, rowNumber, name, action, PASS, fragmentPath);
        } catch (ContentFragmentException | PersistenceException | RuntimeException e) {
            log.warn("Could not write {} {}", sheetName, fragmentPath, e);
            return scbResult(sheetName, rowNumber, name, action, FAIL,
                    "Fragment: could not be written (" + e.getMessage() + ")");
        }
    }

    /**
     * Converts every cell of a row (except the action) into the value to store.
     *
     * @param row        the row values, keyed by header
     * @param fieldTypes the model's field types, keyed by field name
     * @return the values to store, keyed by field name; {@code null} means empty the field
     * @throws IllegalArgumentException for the first value that does not fit its field
     */
    private Map<String, Object> scbPrepareValues(Map<String, Object> row, Map<String, DataType> fieldTypes) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, Object> cell : row.entrySet()) {
            if (!ACTION.equals(cell.getKey())) {
                values.put(cell.getKey(), scbPrepareValue(cell.getKey(), fieldTypes.get(cell.getKey()), cell.getValue()));
            }
        }
        return values;
    }

    /**
     * Converts one cell into the value to store in a field of the given type.
     *
     * @param field the field name, used in error messages
     * @param type  the field's data type
     * @param value the cell value: a {@code String} or a {@code List<String>}
     * @return {@code null} for {@code #CLEAR}, a {@code String[]} for a multi-value text field,
     *         the text for a single text field, or the converted value for a typed field
     * @throws IllegalArgumentException if the value does not fit the field
     */
    private Object scbPrepareValue(String field, DataType type, Object value) {
        String typeName = type.getTypeString();

        if (CLEAR.equals(value)) {
            return null;
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
     * Finds the first column (other than the action) that is not a field of the model.
     *
     * @param row        the row values, keyed by header
     * @param fieldTypes the model's field types, keyed by field name
     * @return the unknown column name, or {@code null} if every column is a model field
     */
    private String scbCheckFields(Map<String, Object> row, Map<String, DataType> fieldTypes) {
        for (String field : row.keySet()) {
            if (!ACTION.equals(field) && !fieldTypes.containsKey(field)) {
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
     * Returns the folder, creating it and any missing parents as {@code sling:OrderedFolder}.
     * Does not commit.
     *
     * @param resolver   the author's resource resolver
     * @param folderPath the folder path
     * @return the existing or new folder
     * @throws PersistenceException if a folder cannot be created
     */
    private Resource scbGetOrCreateFolder(ResourceResolver resolver, String folderPath) throws PersistenceException {
        Resource folder = resolver.getResource(folderPath);
        if (folder != null) {
            return folder;
        }
        int lastSlash = folderPath.lastIndexOf('/');
        Resource parent = scbGetOrCreateFolder(resolver, folderPath.substring(0, lastSlash));
        Map<String, Object> props = new HashMap<>();
        props.put("jcr:primaryType", FOLDER_TYPE);
        return resolver.create(parent, folderPath.substring(lastSlash + 1), props);
    }

    /**
     * Builds one result entry, i.e. one line of the results table.
     *
     * @param sheet   the sheet name
     * @param row     the Excel row number, or {@code null} for a whole-sheet problem
     * @param name    the fragment name, may be {@code null}
     * @param action  CREATE or UPDATE, may be {@code null}
     * @param result  PASS or FAIL
     * @param details the fragment path on PASS, the problem on FAIL
     * @return the result with the keys sheet, row, name, action, result and details
     */
    private Map<String, Object> scbResult(String sheet, Integer row, String name, String action, String result,
                                          String details) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("sheet", sheet);
        entry.put("row", row);
        entry.put("name", name);
        entry.put("action", action);
        entry.put("result", result);
        entry.put("details", details);
        return entry;
    }
}
