package com.aemcloudproject.core.cfimport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the import will do with one row: which fragment it targets and whether that fragment is
 * created or updated. Rows with problems have no action and are never written.
 */
public final class PlannedRow {

    enum Action { CREATE, UPDATE }

    private final ConvertedRow row;
    private final String slug;
    private final String path;
    private final String title;
    private final Action action;
    private final List<String> problems;

    PlannedRow(ConvertedRow row, String slug, String path, String title, Action action, List<String> problems) {
        this.row = row;
        this.slug = slug;
        this.path = path;
        this.title = title;
        this.action = action;
        this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
    }

    public ConvertedRow getRow() {
        return row;
    }

    public int getRowNumber() {
        return row.getRowNumber();
    }

    /** The fragment's node name, or null if the row has no usable slug. */
    public String getSlug() {
        return slug;
    }

    /** The fragment's full path, or null if the row has no usable slug. */
    public String getPath() {
        return path;
    }

    /** The title a new fragment gets: the title field, or the slug when that is blank. */
    public String getTitle() {
        return title;
    }

    /** CREATE or UPDATE, or null when the row has problems. */
    public Action getAction() {
        return action;
    }

    /** Problems from converting the row and from planning it, e.g. an invalid slug. */
    public List<String> getProblems() {
        return problems;
    }
}
