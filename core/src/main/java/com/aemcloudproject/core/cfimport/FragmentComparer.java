package com.aemcloudproject.core.cfimport;

import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.cq.dam.cfm.FragmentData;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares a row planned as UPDATE with the fragment stored now, so only fields that really
 * change are written. Reads only.
 */
final class FragmentComparer {

    /** The fields of one row whose value differs from the stored one. */
    static final class Comparison {
        /** Field name to "old → new", in model order. Holds "title" too when the title changes. */
        final Map<String, String> changes = new LinkedHashMap<>();

        boolean isUnchanged() {
            return changes.isEmpty();
        }

        boolean changes(String field) {
            return changes.containsKey(field);
        }
    }

    static final String TITLE = "title";

    private final ResourceResolver resolver;
    private final Map<String, ModelField> fieldsByName = new HashMap<>();

    FragmentComparer(ResourceResolver resolver, ModelDefinition model) {
        this.resolver = resolver;
        for (ModelField field : model.getFields()) {
            fieldsByName.put(field.getName(), field);
        }
    }

    Comparison compare(PlannedRow row) throws ImportException {
        Resource resource = resolver.getResource(row.getPath());
        ContentFragment fragment = resource == null ? null : resource.adaptTo(ContentFragment.class);
        if (fragment == null) {
            throw new ImportException(row.getPath() + " can't be opened as a content fragment.");
        }
        Comparison comparison = new Comparison();
        for (Map.Entry<String, FieldValue> entry : row.getRow().getValues().entrySet()) {
            FieldValue value = entry.getValue();
            if (value.getKind() == FieldValue.Kind.BLANK) {
                continue; // blank keeps the stored value, so it never changes anything
            }
            ModelField field = fieldsByName.get(entry.getKey());
            Object stored = stored(fragment, field);
            Object wanted = value.getKind() == FieldValue.Kind.CLEAR ? null : value.getValue();
            if (!canonical(stored).equals(canonical(wanted))) {
                comparison.changes.put(field.getName(), show(stored) + " → " + show(wanted));
            }
        }
        if (!row.getTitle().equals(fragment.getTitle())) {
            comparison.changes.put(TITLE, show(fragment.getTitle()) + " → " + show(row.getTitle()));
        }
        return comparison;
    }

    private static Object stored(ContentFragment fragment, ModelField field) {
        ContentElement element = fragment.getElement(field.getName());
        if (element == null) {
            return null;
        }
        if (field.getType() == FieldType.MULTILINE_TEXT) {
            return element.getContent();
        }
        FragmentData data = element.getValue();
        return data == null ? null : data.getValue();
    }

    /**
     * One text per value, so values of different Java types but equal meaning compare equal:
     * 101 and 101.0, a String[] and a List, an empty string and null, and two Calendars for the
     * same instant with the same offset.
     */
    private static String canonical(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Object[]) {
            return canonical(Arrays.asList((Object[]) value));
        }
        if (value instanceof List) {
            List<String> items = new ArrayList<>();
            for (Object item : (List<?>) value) {
                items.add(canonical(item));
            }
            return String.join("\n", items);
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
        }
        if (value instanceof GregorianCalendar) {
            return ((GregorianCalendar) value).toZonedDateTime().toOffsetDateTime().toString();
        }
        if (value instanceof Calendar) {
            return ((Calendar) value).toInstant().toString();
        }
        return String.valueOf(value).trim();
    }

    private static String show(Object value) {
        if (value == null || canonical(value).isEmpty()) {
            return "(empty)";
        }
        return ValueConverter.display(value instanceof Object[] ? Arrays.asList((Object[]) value) : value);
    }
}
