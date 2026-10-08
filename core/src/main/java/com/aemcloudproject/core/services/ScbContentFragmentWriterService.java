package com.aemcloudproject.core.services;

import com.aemcloudproject.core.dto.ScbSheetData;
import org.apache.sling.api.resource.ResourceResolver;

import java.util.List;
import java.util.Map;

/**
 * Creates and updates Content Fragments from parsed workbook rows.
 */
public interface ScbContentFragmentWriterService {

    /**
     * Validates every row and, unless this is a dry run, creates or updates its fragment.
     * Each row is saved on its own, so a failing row does not undo the others.
     *
     * @param resolver the author's resource resolver
     * @param sheets   the parsed workbook, keyed by sheet name
     * @param dryRun   {@code true} to validate only, {@code false} to write
     * @return one result per row (or per unusable sheet) with the keys
     *         sheet, row, name, action, result (PASS or FAIL) and details
     */
    List<Map<String, Object>> scbWriteFragments(ResourceResolver resolver, Map<String, ScbSheetData> sheets, boolean dryRun);
}
