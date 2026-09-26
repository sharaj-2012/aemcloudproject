package com.aemcloudproject.core.bulkimport;

import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.cq.dam.cfm.ContentFragmentException;
import com.adobe.cq.dam.cfm.FragmentData;
import com.adobe.cq.dam.cfm.FragmentTemplate;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Creates or updates one content fragment from a planned {@link WriteOp}.
 *
 * <p>Values are set through {@link FragmentData#setValue(Object)} so multi-value references,
 * numbers, booleans and dates keep their types. Each fragment is committed on its own, so a
 * failure rolls back that fragment only. In a dry run the same comparison runs and nothing is
 * written, which makes the dry-run report an exact preview.</p>
 */
public final class FragmentWriter {

    static final String TITLE = "(title)";
    private static final String PLAIN_TEXT = "text/plain";
    private static final Logger LOG = LoggerFactory.getLogger(FragmentWriter.class);

    private final boolean dryRun;
    private final boolean versionBeforeUpdate;
    private final Map<ModelSpec, FragmentTemplate> templates = new EnumMap<>(ModelSpec.class);

    public FragmentWriter(boolean dryRun, boolean versionBeforeUpdate) {
        this.dryRun = dryRun;
        this.versionBeforeUpdate = versionBeforeUpdate;
    }

    public ReportRow write(ResourceResolver resolver, WriteOp op) {
        if (op.hasErrors()) {
            return row(op, Outcome.ERROR, null, String.join("; ", op.getErrors()));
        }
        try {
            ReportRow result = op.isCreate() ? create(resolver, op) : update(resolver, op);
            if (!dryRun) {
                resolver.commit();
            }
            return result;
        } catch (ContentFragmentException | PersistenceException | RuntimeException e) {
            LOG.warn("Offer bulk import failed to write {}", op.getPath(), e);
            resolver.revert();
            return row(op, Outcome.ERROR, null, "Write failed: " + e.getMessage());
        }
    }

    private ReportRow create(ResourceResolver resolver, WriteOp op) throws ContentFragmentException {
        List<String> fields = new ArrayList<>(op.getValues().keySet());
        if (!dryRun) {
            String path = op.getPath();
            int slash = path.lastIndexOf('/');
            Resource parent = resolver.getResource(path.substring(0, slash));
            if (parent == null) {
                throw new ContentFragmentException("Parent folder of " + path + " does not exist");
            }
            ContentFragment fragment = template(resolver, op.getModel())
                    .createFragment(parent, path.substring(slash + 1), op.getTitle());
            for (Map.Entry<String, Object> value : op.getValues().entrySet()) {
                if (value.getValue() != null) {
                    set(element(fragment, op.getModel(), value.getKey()), fieldType(op.getModel(), value.getKey()),
                            value.getValue());
                }
            }
        }
        return row(op, op.isStub() ? Outcome.STUB_CREATED : Outcome.CREATED, fields, null);
    }

    private ReportRow update(ResourceResolver resolver, WriteOp op) throws ContentFragmentException {
        Resource resource = resolver.getResource(op.getPath());
        ContentFragment fragment = resource == null ? null : resource.adaptTo(ContentFragment.class);
        if (fragment == null) {
            throw new ContentFragmentException(op.getPath() + " is not a content fragment");
        }

        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, Object> value : op.getValues().entrySet()) {
            FieldType type = fieldType(op.getModel(), value.getKey());
            if (!same(type, read(element(fragment, op.getModel(), value.getKey()), type), value.getValue())) {
                changed.add(value.getKey());
            }
        }
        boolean titleChanged = op.getTitle() != null && !op.getTitle().equals(fragment.getTitle());
        if (titleChanged) {
            changed.add(TITLE);
        }
        if (changed.isEmpty()) {
            return row(op, Outcome.UNCHANGED, null, null);
        }

        if (!dryRun) {
            if (versionBeforeUpdate) {
                fragment.createVersion("Before offer bulk import", "Automatic version before a bulk import update");
            }
            for (String property : changed) {
                if (!TITLE.equals(property)) {
                    set(element(fragment, op.getModel(), property), fieldType(op.getModel(), property),
                            op.getValues().get(property));
                }
            }
            if (titleChanged) {
                fragment.setTitle(op.getTitle());
            }
        }
        return row(op, Outcome.UPDATED, changed, null);
    }

    private static Object read(ContentElement element, FieldType type) {
        if (type == FieldType.MULTILINE) {
            return element.getContent();
        }
        FragmentData data = element.getValue();
        return data == null ? null : data.getValue();
    }

    private static void set(ContentElement element, FieldType type, Object value) throws ContentFragmentException {
        if (type == FieldType.MULTILINE) {
            element.setContent(value == null ? "" : value.toString(), PLAIN_TEXT);
            return;
        }
        FragmentData data = element.getValue();
        data.setValue(value);
        element.setValue(data);
    }

    /**
     * Compares a stored value with a planned one, tolerating the type drift a repository can hold,
     * such as an ID stored as a double or a single reference stored as a one-element array.
     */
    static boolean same(FieldType type, Object current, Object planned) {
        if (planned == null) {
            return current == null || isEmpty(current);
        }
        if (current == null) {
            return isEmpty(planned);
        }
        switch (type) {
            case FRAGMENTS:
                return Arrays.equals(toArray(current), (String[]) planned);
            case LONG:
                return current instanceof Number && ((Number) current).longValue() == (Long) planned;
            case DOUBLE:
                return current instanceof Number && Double.compare(((Number) current).doubleValue(), (Double) planned) == 0;
            case DATETIME:
                return current instanceof Calendar
                        && ((Calendar) current).getTimeInMillis() == ((Calendar) planned).getTimeInMillis();
            case BOOLEAN:
                return Objects.equals(current instanceof String ? Boolean.valueOf((String) current) : current, planned);
            default:
                Object single = current instanceof Object[] && ((Object[]) current).length == 1 ? ((Object[]) current)[0] : current;
                return Objects.equals(String.valueOf(single), String.valueOf(planned));
        }
    }

    private static String[] toArray(Object value) {
        if (value instanceof String[]) {
            return (String[]) value;
        }
        if (value instanceof Object[]) {
            return Arrays.stream((Object[]) value).map(String::valueOf).toArray(String[]::new);
        }
        return new String[] {String.valueOf(value)};
    }

    private static boolean isEmpty(Object value) {
        return (value instanceof Object[] && ((Object[]) value).length == 0)
                || (value instanceof String && ((String) value).isEmpty());
    }

    private static ContentElement element(ContentFragment fragment, ModelSpec model, String property)
            throws ContentFragmentException {
        if (!fragment.hasElement(property)) {
            throw new ContentFragmentException("Model " + model.getModelName() + " has no element '" + property + "'");
        }
        return fragment.getElement(property);
    }

    private static FieldType fieldType(ModelSpec model, String property) {
        FieldSpec field = model.getField(property);
        if (field != null) {
            return field.getType();
        }
        return property.equals(model.getIdProperty()) ? FieldType.LONG : FieldType.TEXT;
    }

    private FragmentTemplate template(ResourceResolver resolver, ModelSpec model) throws ContentFragmentException {
        FragmentTemplate template = templates.get(model);
        if (template == null) {
            Resource resource = resolver.getResource(model.getModelPath());
            template = resource == null ? null : resource.adaptTo(FragmentTemplate.class);
            if (template == null && resource != null && resource.getChild("jcr:content") != null) {
                template = resource.getChild("jcr:content").adaptTo(FragmentTemplate.class);
            }
            if (template == null) {
                throw new ContentFragmentException("Cannot load content fragment model " + model.getModelPath());
            }
            templates.put(model, template);
        }
        return template;
    }

    private static ReportRow row(WriteOp op, Outcome outcome, List<String> fields, String message) {
        List<String> notes = new ArrayList<>(op.getWarnings());
        if (message != null) {
            notes.add(0, message);
        }
        return new ReportRow(op.getSheet(), op.getRow(), op.getModel().getModelName(), op.getSlug(),
                op.getPath() == null ? "" : op.getPath(), outcome, fields, String.join("; ", notes));
    }
}
