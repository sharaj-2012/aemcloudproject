package com.aemcloudproject.core.cfimport;

/**
 * A problem with the workbook itself, reported to the author as it is. Thrown in a critical
 * stage, it stops the run before any later stage starts.
 */
public class ImportException extends Exception {

    private static final long serialVersionUID = 1L;

    public ImportException(String message) {
        super(message);
    }

    public ImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
