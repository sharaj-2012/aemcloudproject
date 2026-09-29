package com.aemcloudproject.core.cfimport;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads content fragment models from /conf, as deployed on the instance, so the import follows
 * whatever the models are today rather than a copy kept in code.
 */
final class ModelReader {

    static final String MODELS_ROOT = "/conf/aemcloudproject/settings/dam/cfm/models";

    /** Where the model editor keeps the fields, relative to the model node, in editor order. */
    private static final String FIELDS = "jcr:content/model/cq:dialog/content/items";
    private static final String MULTIPLE_SUFFIX = "[]";

    private ModelReader() {
    }

    /** The model whose node name matches the sheet name, ignoring case. */
    static ModelDefinition forSheet(ResourceResolver resolver, String sheetName) throws ImportException {
        Resource root = resolver.getResource(MODELS_ROOT);
        if (root == null) {
            throw new ImportException("Can't read the models folder " + MODELS_ROOT + ".");
        }
        List<String> modelIds = new ArrayList<>();
        Resource match = null;
        for (Resource child : root.getChildren()) {
            if (child.getChild(FIELDS) == null) {
                continue; // jcr:content and other non-model nodes
            }
            modelIds.add(child.getName());
            if (child.getName().equalsIgnoreCase(sheetName.trim())) {
                match = child;
            }
        }
        if (match == null) {
            throw new ImportException("Sheet '" + sheetName + "' doesn't match a content fragment model. "
                    + "Name the sheet after one of: " + String.join(", ", modelIds) + ".");
        }
        return read(match);
    }

    private static ModelDefinition read(Resource model) throws ImportException {
        ValueMap content = model.getChild("jcr:content").getValueMap();
        // Older models have no status; the editor only offers enabled ones when creating fragments.
        String status = content.get("status", "enabled");
        if (!"enabled".equals(status)) {
            throw new ImportException("Model " + model.getName() + " is " + status
                    + ", so fragments can't be created from it. Enable it first.");
        }
        List<ModelField> fields = new ArrayList<>();
        for (Resource item : model.getChild(FIELDS).getChildren()) {
            ValueMap props = item.getValueMap();
            String name = props.get("name", String.class);
            if (name != null && !name.trim().isEmpty()) { // tab placeholders have no name
                fields.add(field(name, props));
            }
        }
        if (fields.isEmpty()) {
            throw new ImportException("Model " + model.getName() + " has no fields.");
        }
        return new ModelDefinition(model.getName(), model.getPath(), content.get("jcr:title", model.getName()), fields);
    }

    private static ModelField field(String name, ValueMap props) {
        String valueType = props.get("valueType", "");
        boolean multiple = valueType.endsWith(MULTIPLE_SUFFIX);
        String baseValueType = multiple ? valueType.substring(0, valueType.length() - MULTIPLE_SUFFIX.length()) : valueType;
        // Saved as "on" by the model editor; accept a real boolean too.
        String required = props.get("required", "");
        // A single path, or several when a field accepts more than one model.
        String[] allowedModels = props.get("fragmentmodelreference", new String[0]);
        return new ModelField(
                name,
                label(name, props),
                FieldType.of(props.get("metaType", String.class), baseValueType),
                valueType,
                "on".equals(required) || "true".equals(required),
                multiple,
                new ArrayList<>(Arrays.asList(allowedModels)),
                props.get("rootPath", String.class),
                props.get("maxlength", Integer.class),
                props.get("default-mime-type", String.class));
    }

    /** Multi line text keeps its label in cfm-element, a checkbox in text, the rest in fieldLabel. */
    private static String label(String name, ValueMap props) {
        for (String key : new String[] {"fieldLabel", "cfm-element", "text"}) {
            String label = props.get(key, String.class);
            if (label != null && !label.trim().isEmpty()) {
                return label;
            }
        }
        return name;
    }
}
