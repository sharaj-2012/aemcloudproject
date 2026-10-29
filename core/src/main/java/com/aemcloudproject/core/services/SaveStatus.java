package com.aemcloudproject.core.services;

/**
 * Outcome of a JSON asset save, with its HTTP status and response code.
 */
public enum SaveStatus {

    SAVED(200, "saved", "Saved."),
    UNCHANGED(200, "unchanged", "Nothing to save."),
    NOT_DAM_ASSET(400, "not_dam_asset", "Not a DAM asset."),
    INVALID_JSON(400, "invalid_json", "Invalid JSON."),
    FORBIDDEN(403, "forbidden", "You do not have permission to modify this asset."),
    NOT_JSON(415, "not_json", "Only JSON assets can be edited."),
    WRONG_CONTENT_TYPE(415, "wrong_content_type", "Requests must be sent as application/json."),
    VERSION_FAILED(500, "version_failed", "Could not create a version; nothing was saved."),
    SAVE_FAILED(500, "save_failed", "Save failed; nothing was changed.");

    private final int httpStatus;
    private final String code;
    private final String defaultMessage;

    SaveStatus(int httpStatus, String code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * @return HTTP status code sent to the client
     */
    public int httpStatus() {
        return httpStatus;
    }

    /**
     * @return machine-readable status sent in the response body
     */
    public String code() {
        return code;
    }

    /**
     * @return user-facing message used when no specific message is given
     */
    public String defaultMessage() {
        return defaultMessage;
    }
}
