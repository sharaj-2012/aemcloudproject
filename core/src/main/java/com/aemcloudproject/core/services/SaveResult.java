package com.aemcloudproject.core.services;

/**
 * Immutable result of a JSON asset save, returned to the editor as the response body.
 */
public final class SaveResult {

    private final SaveStatus status;
    private final String message;
    private final Integer line;
    private final Integer column;
    private final String versionLabel;

    private SaveResult(SaveStatus status, String message, Integer line, Integer column, String versionLabel) {
        this.status = status;
        this.message = message;
        this.line = line;
        this.column = column;
        this.versionLabel = versionLabel;
    }

    /**
     * @param status outcome
     * @return result carrying the status's default message
     */
    public static SaveResult of(SaveStatus status) {
        return new SaveResult(status, status.defaultMessage(), null, null, null);
    }

    /**
     * @param message parser message
     * @param line    1-based line of the error, or null when unknown
     * @param column  1-based column of the error, or null when unknown
     * @return invalid JSON result
     */
    public static SaveResult invalidJson(String message, Integer line, Integer column) {
        return new SaveResult(SaveStatus.INVALID_JSON, message, line, column, null);
    }

    /**
     * @param maxBytes configured size limit
     * @return too large result naming the limit
     */
    public static SaveResult tooLarge(long maxBytes) {
        return new SaveResult(SaveStatus.TOO_LARGE, "File exceeds the size limit of " + maxBytes + " bytes.", null, null, null);
    }

    /**
     * @param versionLabel label of the version holding the previous content
     * @return saved result
     */
    public static SaveResult saved(String versionLabel) {
        return new SaveResult(SaveStatus.SAVED, SaveStatus.SAVED.defaultMessage(), null, null, versionLabel);
    }

    public SaveStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Integer getLine() {
        return line;
    }

    public Integer getColumn() {
        return column;
    }

    public String getVersionLabel() {
        return versionLabel;
    }
}
