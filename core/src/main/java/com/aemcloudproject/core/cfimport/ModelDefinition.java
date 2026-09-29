package com.aemcloudproject.core.cfimport;

import java.util.Collections;
import java.util.List;

/**
 * A content fragment model as deployed on the instance: its identity and its fields in editor order.
 */
public final class ModelDefinition {

    private final String id;
    private final String path;
    private final String title;
    private final List<ModelField> fields;

    ModelDefinition(String id, String path, String title, List<ModelField> fields) {
        this.id = id;
        this.path = path;
        this.title = title;
        this.fields = Collections.unmodifiableList(fields);
    }

    /** The model's node name, e.g. {@code offer-detail}; import sheets are named after it. */
    public String getId() {
        return id;
    }

    /** e.g. {@code /conf/aemcloudproject/settings/dam/cfm/models/offer-detail}; fragments store it as cq:model. */
    public String getPath() {
        return path;
    }

    public String getTitle() {
        return title;
    }

    public List<ModelField> getFields() {
        return fields;
    }
}
