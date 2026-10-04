package com.aemcloudproject.core.services.impl;

import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.cq.dam.cfm.ContentFragmentException;
import com.adobe.cq.dam.cfm.DataType;
import com.adobe.cq.dam.cfm.ElementTemplate;
import com.adobe.cq.dam.cfm.FragmentData;
import com.adobe.cq.dam.cfm.FragmentTemplate;
import com.aemcloudproject.core.dto.ScbSheetData;
import com.aemcloudproject.core.services.ScbContentFragmentWriterService;
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
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WHAT: Writes Content Fragments with AEM's Content Fragment API (com.adobe.cq.dam.cfm).
 *
 * CALL ORDER:
 *   scbWriteFragments(resolver, sheets)
 *     └─ for each sheet:
 *          ├─ PROFILES lookup             "offer-cta" -> folder offer-listing/ctas, name from offerCtaSlug, title from label
 *          ├─ load the model              /conf/.../models/offer-cta -> FragmentTemplate + its field names
 *          └─ for each row:
 *               ├─ scbWriteRow(row)        CREATE or UPDATE one fragment, returns CREATED / UPDATED / FAILED
 *               │    ├─ scbCheckFields     every column must be a field of the model
 *               │    ├─ scbGetOrCreateFolder  (CREATE) make .../offer-listing/ctas if missing
 *               │    └─ scbSetFields       write each cell into the fragment, using the field's type
 *               │         └─ scbConvert    "10001" -> 10001 (Integer field), "1.2935" -> 1.2935 (Fraction field), "1" -> true (Boolean field), date text -> Calendar, ...
 *               └─ commit (or revert if the row failed)
 *
 * SCOPE: all six models. Fragment references are written as plain path text — whether the
 * referenced fragment exists is not checked yet (step D).
 */
@Component(service = ScbContentFragmentWriterService.class)
public class ScbContentFragmentWriterServiceImpl implements ScbContentFragmentWriterService {

    private static final Logger log = LoggerFactory.getLogger(ScbContentFragmentWriterServiceImpl.class);

    // All fragments go under this folder (static for now, as agreed).
    private static final String ROOT_PATH = "/content/dam/aemcloudproject/cfs";
    // Sheet name + this = model path, e.g. "offer-cta" -> /conf/aemcloudproject/settings/dam/cfm/models/offer-cta
    private static final String MODELS_PATH = "/conf/aemcloudproject/settings/dam/cfm/models/";
    private static final String FOLDER_TYPE = "sling:OrderedFolder";
    private static final String ACTION = "action";
    // Fragment node names: lowercase letters, digits, "-" and "_" — e.g. "book-now".
    private static final String NAME_PATTERN = "[a-z0-9_-]+";

    /**
     * Per-model settings: where its fragments go and which columns give the name and title.
     * Example: offer-cta -> folder "offer-listing/ctas", name from "offerCtaSlug", title from "label",
     * so the row {offerCtaSlug=book-now, label=Book Now} becomes
     * /content/dam/aemcloudproject/cfs/offer-listing/ctas/book-now with title "Book Now".
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

    // One entry per model / sheet name (from docs/cf-bulk-upload/01-input-format.md, "Model profiles").
    // A sheet whose name isn't here (e.g. "venues") is reported as SKIPPED.
    private static final Map<String, Profile> PROFILES;
    static {
        Map<String, Profile> profiles = new HashMap<>();
        profiles.put("offer-detail", new Profile("offer-listing/offers", "offerSlug", "offerTitle"));
        profiles.put("category", new Profile("offer-listing/categories", "offerCategorySlug", "name"));
        profiles.put("merchant-details", new Profile("offer-listing/merchants", "merchantSlug", "merchantName"));
        profiles.put("merchant-venue", new Profile("offer-listing/venues", "merchantVenueSlug", "name"));
        profiles.put("offer-card", new Profile("offer-listing/cards", "offerCardSlug", "name"));
        profiles.put("offer-cta", new Profile("offer-listing/ctas", "offerCtaSlug", "label"));
        PROFILES = Collections.unmodifiableMap(profiles);
    }

    // A cell containing exactly this empties the field (on UPDATE; on CREATE the field just stays empty).
    private static final String CLEAR = "#CLEAR";

    /**
     * WHAT: Writes every row of every supported sheet; see the interface for the contract.
     *
     * INPUT:  resolver — author's resolver; sheets — e.g. {"offer-cta" -> 5 rows, "merchant-venue" -> 5 rows, ...}
     * OUTPUT: e.g. [ {sheet=venues, result=SKIPPED, message=Not one of the CF models this uploader knows.},
     *                {sheet=offer-cta, name=book-now, action=CREATE, result=CREATED, path=.../ctas/book-now},
     *                ... ]
     */
    @Override
    public List<Map<String, Object>> scbWriteFragments(ResourceResolver resolver, Map<String, ScbSheetData> sheets) {
        List<Map<String, Object>> results = new ArrayList<>();

        for (Map.Entry<String, ScbSheetData> sheet : sheets.entrySet()) {
            String sheetName = sheet.getKey();      // e.g. "offer-cta"

            // 1. Is this sheet one of our models? e.g. a sheet named "venues" has no PROFILES entry -> one SKIPPED result.
            Profile profile = PROFILES.get(sheetName);
            if (profile == null) {
                results.add(scbResult(sheetName, null, null, "SKIPPED", null, "Not one of the CF models this uploader knows."));
                continue;
            }

            // 2. Load the model. /conf/.../models/offer-cta adapts to FragmentTemplate, which can create fragments.
            String modelPath = MODELS_PATH + sheetName;
            Resource modelResource = resolver.getResource(modelPath);
            FragmentTemplate template = modelResource == null ? null : modelResource.adaptTo(FragmentTemplate.class);
            if (template == null) {
                results.add(scbResult(sheetName, null, null, "FAILED", null, "Model not found: " + modelPath));
                continue;
            }
            // Field names of the model, e.g. offer-cta -> {offerCtaSlug, label, url, deeplink}
            Set<String> modelFields = scbModelFields(template);

            // 3. One row at a time; each row is committed (saved) or reverted (undone) on its own.
            for (Map<String, Object> row : sheet.getValue().getRows()) {
                Map<String, Object> result = scbWriteRow(resolver, sheetName, profile, template, modelPath, modelFields, row);
                try {
                    if ("FAILED".equals(result.get("result"))) {
                        resolver.revert();      // drop anything half-done for this row
                    } else {
                        resolver.commit();      // save this row's fragment to the repository
                    }
                } catch (PersistenceException e) {
                    log.warn("Could not save {} row {}", sheetName, row, e);
                    resolver.revert();
                    // Name and action are plain strings here (they never start with /content, so never a list).
                    result = scbResult(sheetName, (String) row.get(profile.nameField), (String) row.get(ACTION),
                            "FAILED", null, "Could not save: " + e.getMessage());
                }
                results.add(result);
            }
        }
        return results;
    }

    /**
     * WHAT: Creates or updates the fragment for one row. Does not save — the caller commits or reverts.
     *
     * INPUT:  e.g. sheetName "offer-cta", profile (ctas, offerCtaSlug, label), the offer-cta model,
     *         row {action=CREATE, offerCtaSlug=book-now, label=Book Now, url=https://example.com/book, deeplink=app://book}
     *
     * OUTPUT: one result map:
     *   CREATE, fragment doesn't exist yet -> {..., result=CREATED, path=/content/dam/aemcloudproject/cfs/offer-listing/ctas/book-now}
     *   CREATE, fragment already exists    -> {..., result=FAILED, message=already exists}
     *   UPDATE, fragment exists            -> {..., result=UPDATED, path=...}
     *   UPDATE, fragment doesn't exist     -> {..., result=FAILED, message=not found}
     *   offerCtaSlug empty or "Book Now"   -> {..., result=FAILED, message=offerCtaSlug "Book Now" must be lowercase letters, digits, - or _}
     *   column not in the model            -> {..., result=FAILED, message=field "promoCode" is not in model offer-cta}
     */
    private Map<String, Object> scbWriteRow(ResourceResolver resolver, String sheetName, Profile profile,
                                            FragmentTemplate template, String modelPath, Set<String> modelFields,
                                            Map<String, Object> row) {
        String action = (String) row.get(ACTION);          // "CREATE" or "UPDATE" (the parser already checked it)
        Object nameValue = row.get(profile.nameField);     // e.g. "book-now"
        String name = nameValue instanceof String ? (String) nameValue : null;

        // The name becomes the node name, so it must be a valid one: "book-now" ok, "Book Now" not.
        if (name == null || !name.matches(NAME_PATTERN)) {
            return scbResult(sheetName, name, action, "FAILED", null,
                    profile.nameField + " \"" + (nameValue == null ? "" : nameValue)
                            + "\" must be lowercase letters, digits, - or _");
        }

        // Every column must be a field of the model, otherwise its data would silently be lost.
        String unknownField = scbCheckFields(row, modelFields);
        if (unknownField != null) {
            return scbResult(sheetName, name, action, "FAILED", null,
                    "field \"" + unknownField + "\" is not in model " + sheetName);
        }

        // e.g. /content/dam/aemcloudproject/cfs + /offer-listing/ctas -> .../offer-listing/ctas
        String folderPath = ROOT_PATH + "/" + profile.folder;
        // e.g. .../offer-listing/ctas/book-now
        String fragmentPath = folderPath + "/" + name;
        Resource existing = resolver.getResource(fragmentPath);
        // e.g. "Book Now"; null if the title column is empty
        String title = row.get(profile.titleField) instanceof String ? (String) row.get(profile.titleField) : null;

        try {
            ContentFragment fragment;
            String done;
            if ("CREATE".equals(action)) {
                if (existing != null) {
                    return scbResult(sheetName, name, action, "FAILED", fragmentPath, "already exists");
                }
                Resource folder = scbGetOrCreateFolder(resolver, folderPath);
                // Creates <folder>/book-now from the offer-cta model; title falls back to the name.
                fragment = template.createFragment(folder, name, title != null ? title : name);
                done = "CREATED";
            } else {
                if (existing == null) {
                    return scbResult(sheetName, name, action, "FAILED", fragmentPath, "not found");
                }
                fragment = existing.adaptTo(ContentFragment.class);
                if (fragment == null) {
                    return scbResult(sheetName, name, action, "FAILED", fragmentPath, "is not a content fragment");
                }
                // Never re-model a fragment: the existing one must already use this sheet's model.
                // The model path is stored at <fragment>/jcr:content/data/cq:model,
                // e.g. /conf/aemcloudproject/settings/dam/cfm/models/offer-cta
                Resource data = existing.getChild("jcr:content/data");
                String existingModel = data == null ? null : data.getValueMap().get("cq:model", String.class);
                if (!modelPath.equals(existingModel)) {
                    return scbResult(sheetName, name, action, "FAILED", fragmentPath,
                            "uses model " + existingModel + ", not " + modelPath);
                }
                if (title != null) {
                    fragment.setTitle(title);   // empty title cell = keep the current title
                }
                done = "UPDATED";
            }
            scbSetFields(fragment, row);
            return scbResult(sheetName, name, action, done, fragmentPath, null);
        } catch (ContentFragmentException | PersistenceException | RuntimeException e) {
            log.warn("Could not write {} {}", sheetName, fragmentPath, e);
            return scbResult(sheetName, name, action, "FAILED", fragmentPath, e.getMessage());
        }
    }

    /**
     * WHAT: Writes each cell of the row into the fragment's field of the same name, converted to
     * the type the model defines for that field.
     *
     * INPUT:  fragment — e.g. .../offers/gourmet-dining-deal
     *         row      — e.g. {action=CREATE, offerID=10001, offerSlug=gourmet-dining-deal,
     *                          offerSummary=<p>Enjoy 20% off ...</p>, hotPromo=1,
     *                          offerStartDate=2026-10-01T00:00:00.000+05:30,
     *                          offerCategories=[/content/dam/.../categories/dining, /content/dam/.../categories/fine-dining],
     *                          offerMerchants=/content/dam/.../merchants/fine-eats-sg, offerTnc=#CLEAR, ...}
     *
     * OUTPUT: nothing returned; the fragment now has, field by field:
     *           offerID          Integer   10001
     *           offerSlug        text      gourmet-dining-deal
     *           offerSummary     HTML      <p>Enjoy 20% off ...</p>         (written as text/html)
     *           hotPromo         boolean   true
     *           offerStartDate   date      2026-10-01 00:00 +05:30
     *           offerCategories  list      [.../dining, .../fine-dining]    (reference paths as plain text)
     *           offerMerchants   list      [.../fine-eats-sg]               (one path into a multi-value field)
     *           offerTnc         emptied                                    (#CLEAR)
     *         Blank cells are not in the row, so those fields are left untouched (on UPDATE they
     *         keep their current value).
     *
     * THROWS: IllegalArgumentException with a readable message when a value doesn't fit its field,
     *         e.g. offerID "10001a" is not a whole number;  ContentFragmentException if AEM rejects a value.
     */
    private void scbSetFields(ContentFragment fragment, Map<String, Object> row) throws ContentFragmentException {
        for (Map.Entry<String, Object> cell : row.entrySet()) {
            String field = cell.getKey();       // e.g. "offerID"
            Object value = cell.getValue();     // String, or List<String> for a multi-line /content cell
            if (ACTION.equals(field)) {
                continue;   // "action" is an instruction, not a field
            }

            ContentElement element = fragment.getElement(field);    // the fragment's "offerID" field
            FragmentData data = element.getValue();                 // its current value + type
            DataType type = data.getDataType();
            // Type name the CF API reports for the model field. Model editor -> API name:
            //   Single/Multi line text, references -> "string"   Number Integer -> "long"   Number Fraction -> "double"
            //   Boolean -> "boolean"   Date and time -> "calendar"
            String typeName = type.getTypeString();

            // #CLEAR -> empty the field, whatever its type.
            if (CLEAR.equals(value)) {
                data.setValue(null);
                element.setValue(data);
                continue;
            }

            // Several values (a multi-line /content cell) -> only allowed for a multi-value field.
            // e.g. offerCategories=[/content/dam/.../dining, /content/dam/.../fine-dining] -> String[2]
            if (value instanceof List) {
                if (!type.isMultiValue()) {
                    throw new IllegalArgumentException(field + " takes one value, but the cell has several lines");
                }
                @SuppressWarnings("unchecked")
                List<String> values = (List<String>) value;
                data.setValue(values.toArray(new String[0]));
                element.setValue(data);
                continue;
            }

            String text = (String) value;
            if ("string".equals(typeName)) {
                if (type.isMultiValue()) {
                    // One path in a multi-value field, e.g. offerMerchants=/content/dam/.../fine-eats-sg -> String[1]
                    data.setValue(new String[] {text});
                    element.setValue(data);
                } else {
                    // Single text / HTML / single reference. setContent with the field's own content type
                    // keeps HTML fields as text/html and plain fields as text/plain.
                    element.setContent(text, element.getContentType());
                }
                continue;
            }

            // Typed field: convert the text, e.g. "10001" -> 10001L, then store it.
            data.setValue(scbConvert(field, typeName, text));
            element.setValue(data);
        }
    }

    /**
     * WHAT: Converts cell text to the Java type a field expects.
     *
     * INPUT -> OUTPUT (field name only used in error messages):
     *   ("offerID",        "long",     "10001")                          -> 10001     (Integer field; AEM calls it "long")
     *   ("offerID",        "long",     "10001a")                         -> IllegalArgumentException: offerID "10001a" is not a whole number
     *   ("latitude",       "double",   "1.2935")                         -> 1.2935    (Fraction field; AEM calls it "double")
     *   ("hotPromo",       "boolean",  "1" / "true" / "Yes")             -> Boolean true
     *   ("hotPromo",       "boolean",  "0" / "false" / "no")             -> Boolean false
     *   ("hotPromo",       "boolean",  "maybe")                          -> IllegalArgumentException: hotPromo "maybe" must be true/false, 1/0 or yes/no
     *   ("offerStartDate", "calendar", "2026-10-01T00:00:00.000+05:30")  -> Calendar 2026-10-01 00:00 at +05:30 (as written)
     *   ("offerStartDate", "calendar", "2026-10-01T09:30")               -> Calendar in the AEM server's time zone
     *   ("offerStartDate", "calendar", "2026-10-01")                     -> Calendar at midnight, server's time zone
     *   ("offerStartDate", "calendar", "next monday")                    -> IllegalArgumentException: offerStartDate "next monday" is not a date ...
     *   any other type                                                    -> the text unchanged
     */
    private Object scbConvert(String field, String typeName, String text) {
        try {
            switch (typeName) {
                case "long":        // Number field set to Integer in the model editor, e.g. offerID
                    return Long.parseLong(text);
                case "double":      // Number field set to Fraction, e.g. latitude
                    return Double.parseDouble(text);
                case "boolean":
                    String lower = text.toLowerCase();
                    if ("true".equals(lower) || "1".equals(lower) || "yes".equals(lower)) {
                        return Boolean.TRUE;
                    }
                    if ("false".equals(lower) || "0".equals(lower) || "no".equals(lower)) {
                        return Boolean.FALSE;
                    }
                    throw new IllegalArgumentException(field + " \"" + text + "\" must be true/false, 1/0 or yes/no");
                case "calendar":
                    return scbParseDate(text);
                default:
                    return text;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " \"" + text + "\" is not a "
                    + ("long".equals(typeName) ? "whole number" : "number"));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + " \"" + text
                    + "\" is not a date (use e.g. 2026-10-01T00:00:00.000+05:30)");
        }
    }

    /**
     * WHAT: Reads date text as written — the offset in the text is kept; without one, the AEM
     * server's own time zone is used.
     *
     * INPUT -> OUTPUT:
     *   "2026-10-01T00:00:00.000+05:30" -> 2026-10-01 00:00:00.000 at +05:30
     *   "2026-10-01T09:30"              -> 2026-10-01 09:30 in the server's time zone
     *   "2026-10-01"                    -> 2026-10-01 00:00 in the server's time zone
     *   "01/10/2026"                    -> DateTimeParseException (caught by scbConvert)
     */
    private Calendar scbParseDate(String text) {
        ZonedDateTime dateTime;
        if (text.length() == 10) {
            // Date only: 2026-10-01
            dateTime = LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault());
        } else if (text.matches(".*([+-]\\d{2}:\\d{2}|Z)$")) {
            // Ends with an offset (+05:30, -04:00) or Z: keep it exactly.
            dateTime = OffsetDateTime.parse(text).toZonedDateTime();
        } else {
            // Date and time without offset: 2026-10-01T09:30
            dateTime = LocalDateTime.parse(text).atZone(ZoneId.systemDefault());
        }
        return GregorianCalendar.from(dateTime);
    }

    /**
     * WHAT: Finds the first column that is not a field of the model.
     * INPUT:  row {action=..., offerCtaSlug=..., label=..., promoCode=...}, modelFields {offerCtaSlug, label, url, deeplink}
     * OUTPUT: "promoCode"; null when every column (except action) is a model field.
     */
    private String scbCheckFields(Map<String, Object> row, Set<String> modelFields) {
        for (String field : row.keySet()) {
            if (!ACTION.equals(field) && !modelFields.contains(field)) {
                return field;
            }
        }
        return null;
    }

    /**
     * WHAT: Reads the field names of a model.
     * INPUT:  the offer-cta model
     * OUTPUT: {offerCtaSlug, label, url, deeplink}
     */
    private Set<String> scbModelFields(FragmentTemplate template) {
        Set<String> fields = new HashSet<>();
        Iterator<ElementTemplate> elements = template.getElements();
        while (elements.hasNext()) {
            fields.add(elements.next().getName());
        }
        return fields;
    }

    /**
     * WHAT: Returns the folder, creating every missing folder on the way as sling:OrderedFolder.
     * Not saved here — the caller's commit saves the folders together with the fragment.
     *
     * INPUT:  "/content/dam/aemcloudproject/cfs/offer-listing/ctas"
     * OUTPUT: that folder's Resource. If only .../cfs exists, "offer-listing" and then "ctas" are created.
     */
    private Resource scbGetOrCreateFolder(ResourceResolver resolver, String folderPath) throws PersistenceException {
        Resource folder = resolver.getResource(folderPath);
        if (folder != null) {
            return folder;
        }
        // Make sure the parent exists first (recursively), then create this folder in it.
        // ".../offer-listing/ctas" -> parent ".../offer-listing", name "ctas"
        int lastSlash = folderPath.lastIndexOf('/');
        Resource parent = scbGetOrCreateFolder(resolver, folderPath.substring(0, lastSlash));
        Map<String, Object> props = new HashMap<>();
        props.put("jcr:primaryType", FOLDER_TYPE);
        return resolver.create(parent, folderPath.substring(lastSlash + 1), props);
    }

    /**
     * WHAT: Builds one result entry for the page, leaving out empty parts.
     * INPUT:  "offer-cta", "book-now", "CREATE", "CREATED", ".../ctas/book-now", null
     * OUTPUT: {sheet=offer-cta, name=book-now, action=CREATE, result=CREATED, path=.../ctas/book-now}
     */
    private Map<String, Object> scbResult(String sheet, String name, String action, String result, String path, String message) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("sheet", sheet);
        if (name != null) {
            entry.put("name", name);
        }
        if (action != null) {
            entry.put("action", action);
        }
        entry.put("result", result);
        if (path != null) {
            entry.put("path", path);
        }
        if (message != null) {
            entry.put("message", message);
        }
        return entry;
    }
}
