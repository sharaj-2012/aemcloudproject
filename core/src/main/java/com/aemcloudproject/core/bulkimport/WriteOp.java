package com.aemcloudproject.core.bulkimport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One planned fragment write: where it goes, whether it is new, and the typed values to set.
 * Reference values hold resolved fragment paths once planning finishes.
 */
public final class WriteOp {

    private final ModelSpec model;
    private final String sheet;
    private final int row;
    private final String slug;
    private final boolean stub;
    private String path;
    private boolean create;
    private String title;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    WriteOp(ModelSpec model, String sheet, int row, String slug, boolean stub) {
        this.model = model;
        this.sheet = sheet;
        this.row = row;
        this.slug = slug;
        this.stub = stub;
    }

    public ModelSpec getModel() {
        return model;
    }

    public String getSheet() {
        return sheet;
    }

    public int getRow() {
        return row;
    }

    public String getSlug() {
        return slug;
    }

    public boolean isStub() {
        return stub;
    }

    public String getPath() {
        return path;
    }

    void setPath(String path) {
        this.path = path;
    }

    public boolean isCreate() {
        return create;
    }

    void setCreate(boolean create) {
        this.create = create;
    }

    public String getTitle() {
        return title;
    }

    void setTitle(String title) {
        this.title = title;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
