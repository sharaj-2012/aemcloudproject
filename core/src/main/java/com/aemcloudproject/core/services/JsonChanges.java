package com.aemcloudproject.core.services;

/**
 * The latest change to a JSON asset: its current content and the content of its most recent version.
 */
public final class JsonChanges {

    /**
     * Outcome of looking up the changes.
     */
    public enum Status {
        OK(200, "ok"),
        NOT_DAM_ASSET(400, "not_dam_asset"),
        NOT_JSON(415, "not_json");

        private final int httpStatus;
        private final String code;

        Status(int httpStatus, String code) {
            this.httpStatus = httpStatus;
            this.code = code;
        }

        public int httpStatus() {
            return httpStatus;
        }

        public String code() {
            return code;
        }
    }

    /**
     * One side of the comparison.
     */
    public static final class Side {

        private final String content;
        private final String label;
        private final Long date;
        private final String user;

        /**
         * @param content file text
         * @param label   version label, or null for the current content
         * @param date    epoch milliseconds of the change or version, or null when unknown
         * @param user    user who made the change, or null when unknown
         */
        public Side(String content, String label, Long date, String user) {
            this.content = content;
            this.label = label;
            this.date = date;
            this.user = user;
        }

        public String getContent() {
            return content;
        }

        public String getLabel() {
            return label;
        }

        public Long getDate() {
            return date;
        }

        public String getUser() {
            return user;
        }
    }

    private final Status status;
    private final String path;
    private final Side current;
    private final Side previous;

    private JsonChanges(Status status, String path, Side current, Side previous) {
        this.status = status;
        this.path = path;
        this.current = current;
        this.previous = previous;
    }

    /**
     * @param status failure status
     * @return a result without content
     */
    public static JsonChanges failed(Status status) {
        return new JsonChanges(status, null, null, null);
    }

    /**
     * @param path     asset path
     * @param current  current content
     * @param previous most recent version, or null when the asset has no versions
     * @return a successful result
     */
    public static JsonChanges of(String path, Side current, Side previous) {
        return new JsonChanges(Status.OK, path, current, previous);
    }

    public Status getStatus() {
        return status;
    }

    public String getPath() {
        return path;
    }

    public Side getCurrent() {
        return current;
    }

    public Side getPrevious() {
        return previous;
    }
}
