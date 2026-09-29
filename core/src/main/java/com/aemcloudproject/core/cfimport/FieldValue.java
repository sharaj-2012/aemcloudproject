package com.aemcloudproject.core.cfimport;

/**
 * What a row says about one field whose column is in the sheet: a converted value, a blank
 * cell, or an explicit request to empty the field. Fields whose column is missing get no
 * FieldValue at all.
 */
public final class FieldValue {

    /** Typed into a cell to empty the field on purpose, since a blank cell may mean "leave as is". */
    static final String CLEAR_TOKEN = "__CLEAR__";

    enum Kind { VALUE, BLANK, CLEAR }

    static final FieldValue BLANK = new FieldValue(Kind.BLANK, null);
    static final FieldValue CLEAR = new FieldValue(Kind.CLEAR, null);

    private final Kind kind;
    private final Object value;

    private FieldValue(Kind kind, Object value) {
        this.kind = kind;
        this.value = value;
    }

    static FieldValue of(Object value) {
        return new FieldValue(Kind.VALUE, value);
    }

    public Kind getKind() {
        return kind;
    }

    /**
     * For {@link Kind#VALUE}: String, Long, Double, Boolean, Calendar, or a List of those for
     * multi-value fields. Null otherwise.
     */
    public Object getValue() {
        return value;
    }
}
