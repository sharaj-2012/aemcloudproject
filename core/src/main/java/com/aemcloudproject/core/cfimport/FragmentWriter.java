package com.aemcloudproject.core.cfimport;

import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.cq.dam.cfm.ContentFragmentException;
import com.adobe.cq.dam.cfm.FragmentData;
import com.adobe.cq.dam.cfm.FragmentTemplate;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes planned rows to content fragments through the AEM Content Fragment API, in two passes:
 * {@link #write} creates or opens each fragment and sets every field except fragment references,
 * then {@link #writeReferences} sets those, once every fragment the references point at exists.
 */
final class FragmentWriter {

    private static final String DEFAULT_MIME_TYPE = "text/html";

    /** What happened to one fragment's fields. */
    static final class Result {
        final List<String> set = new ArrayList<>();
        final List<String> cleared = new ArrayList<>();
        final List<String> leftAsIs = new ArrayList<>();
        final List<String> referencesLater = new ArrayList<>();
    }

    private final ResourceResolver resolver;
    private final ModelDefinition model;
    private final ImportTarget target;
    private final Map<String, ModelField> fieldsByName = new HashMap<>();
    private FragmentTemplate template;

    FragmentWriter(ResourceResolver resolver, ModelDefinition model, ImportTarget target) {
        this.resolver = resolver;
        this.model = model;
        this.target = target;
        for (ModelField field : model.getFields()) {
            fieldsByName.put(field.getName(), field);
        }
    }

    /**
     * Creates the folder and any missing parents as DAM folders.
     *
     * @return true if anything was created
     */
    static boolean ensureFolder(ResourceResolver resolver, String path) throws PersistenceException, ImportException {
        if (resolver.getResource(path) != null) {
            return false;
        }
        if (!path.startsWith(ImportFile.DAM_ROOT)) {
            throw new ImportException("Won't create folders outside " + ImportFile.DAM_ROOT + ": " + path);
        }
        String parentPath = path.substring(0, path.lastIndexOf('/'));
        String name = path.substring(path.lastIndexOf('/') + 1);
        ensureFolder(resolver, parentPath);
        Map<String, Object> folderProps = new HashMap<>();
        folderProps.put("jcr:primaryType", "sling:Folder");
        Resource folder = resolver.create(resolver.getResource(parentPath), name, folderProps);
        Map<String, Object> contentProps = new HashMap<>();
        contentProps.put("jcr:primaryType", "nt:unstructured");
        contentProps.put("jcr:title", name);
        resolver.create(folder, "jcr:content", contentProps);
        return true;
    }

    /**
     * Creates or opens the row's fragment and sets its non-reference fields. The caller commits.
     *
     * @param changes for an UPDATE, the fields that differ from the stored fragment; only those
     *                are written. Null for a CREATE, which writes every filled-in field.
     */
    Result write(PlannedRow row, FragmentComparer.Comparison changes) throws ContentFragmentException, ImportException {
        ContentFragment fragment = row.getAction() == PlannedRow.Action.CREATE ? create(row) : open(row);
        Result result = new Result();
        for (Map.Entry<String, FieldValue> entry : row.getRow().getValues().entrySet()) {
            ModelField field = fieldsByName.get(entry.getKey());
            FieldValue value = entry.getValue();
            if (changes != null && !changes.changes(field.getName())) {
                continue; // same as stored
            }
            if (field.getType() == FieldType.FRAGMENT_REFERENCE) {
                if (value.getKind() != FieldValue.Kind.BLANK) {
                    result.referencesLater.add(field.getName());
                }
                continue;
            }
            switch (value.getKind()) {
                case VALUE:
                    set(fragment, field, value.getValue());
                    result.set.add(field.getName());
                    break;
                case CLEAR:
                    set(fragment, field, null);
                    result.cleared.add(field.getName());
                    break;
                default:
                    result.leftAsIs.add(field.getName()); // blank: keep what is stored
            }
        }
        if (changes != null && changes.changes(FragmentComparer.TITLE)) {
            fragment.setTitle(row.getTitle());
            result.set.add(FragmentComparer.TITLE);
        }
        return result;
    }

    /** Saves a version of the fragment as it is now, so an update can be undone from its history. */
    void saveVersion(PlannedRow row, String comment) throws ContentFragmentException, ImportException {
        open(row).createVersion(null, comment);
    }

    /**
     * Second pass: sets the row's fragment references. Every fragment of the file exists by now,
     * so references between rows resolve whatever their order. The caller commits.
     *
     * @param changes as for {@link #write}: only these references are written on an UPDATE
     * @return what happened to the reference fields; only set and cleared are filled
     */
    Result writeReferences(PlannedRow row, FragmentComparer.Comparison changes)
            throws ContentFragmentException, ImportException {
        ContentFragment fragment = open(row);
        Result result = new Result();
        for (Map.Entry<String, FieldValue> entry : row.getRow().getValues().entrySet()) {
            ModelField field = fieldsByName.get(entry.getKey());
            FieldValue value = entry.getValue();
            if (field.getType() != FieldType.FRAGMENT_REFERENCE || value.getKind() == FieldValue.Kind.BLANK
                    || changes != null && !changes.changes(field.getName())) {
                continue;
            }
            if (value.getKind() == FieldValue.Kind.CLEAR) {
                set(fragment, field, null);
                result.cleared.add(field.getName());
            } else {
                set(fragment, field, value.getValue());
                result.set.add(field.getName());
            }
        }
        return result;
    }

    private ContentFragment create(PlannedRow row) throws ContentFragmentException, ImportException {
        Resource folder = resolver.getResource(target.getFolder());
        if (folder == null) {
            throw new ImportException("The folder " + target.getFolder() + " doesn't exist.");
        }
        return template().createFragment(folder, row.getSlug(), row.getTitle());
    }

    private ContentFragment open(PlannedRow row) throws ImportException {
        Resource resource = resolver.getResource(row.getPath());
        ContentFragment fragment = resource == null ? null : resource.adaptTo(ContentFragment.class);
        if (fragment == null) {
            throw new ImportException(row.getPath() + " can't be opened as a content fragment.");
        }
        return fragment;
    }

    /** The model as a template to create fragments from; resolved once per run. */
    private FragmentTemplate template() throws ImportException {
        if (template == null) {
            Resource resource = resolver.getResource(model.getPath());
            FragmentTemplate found = resource == null ? null : resource.adaptTo(FragmentTemplate.class);
            if (found == null && resource != null && resource.getChild("jcr:content") != null) {
                found = resource.getChild("jcr:content").adaptTo(FragmentTemplate.class);
            }
            if (found == null) {
                throw new ImportException("Can't create fragments from the model " + model.getPath() + ".");
            }
            template = found;
        }
        return template;
    }

    /** Sets a field to a converted value, or empties it when the value is null. */
    private static void set(ContentFragment fragment, ModelField field, Object value)
            throws ContentFragmentException, ImportException {
        ContentElement element = fragment.getElement(field.getName());
        if (element == null) {
            throw new ImportException("The fragment has no field " + field.getName() + ".");
        }
        if (field.getType() == FieldType.MULTILINE_TEXT) {
            // setContent keeps the format (HTML); a plain setValue would store it as text.
            String mimeType = field.getMimeType() == null ? DEFAULT_MIME_TYPE : field.getMimeType();
            element.setContent(value == null ? "" : (String) value, mimeType);
            return;
        }
        FragmentData data = element.getValue();
        data.setValue(value instanceof List ? ((List<?>) value).stream().map(String::valueOf).toArray(String[]::new) : value);
        element.setValue(data);
    }
}
