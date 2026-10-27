package com.aemcloudproject.core.services;

import com.aemcloudproject.core.dto.ScbSheetData;
import org.apache.sling.api.resource.ResourceResolver;

import java.util.List;
import java.util.Map;

/**
 * Creates, updates and deletes Content Fragments from parsed workbook rows.
 */
public interface ScbContentFragmentWriterService {

    /**
     * Validates every row that has an action and, unless this is a dry run, creates, updates or deletes
     * its fragment under the picked folder; rows without an action are skipped, and a sheet with no action
     * at all gets a SKIP result. CREATE rows with an empty ID get a generated ID. A fragment that is
     * referenced or published is not deleted. Each row is saved on its own, so a failing row does not
     * undo the others.
     *
     * @param resolver the author's resource resolver
     * @param sheets   the parsed workbook, keyed by sheet name
     * @param rootPath the folder picked on the page; the profile folders are under it
     * @param dryRun   {@code true} to validate only, {@code false} to write
     * @return one result per row with an action (or per unusable sheet) with the keys
     *         sheet, row, slug, action, result (PASS, FAIL, WARN or SKIP) and details
     */
    List<Map<String, Object>> scbWriteFragments(ResourceResolver resolver, Map<String, ScbSheetData> sheets,
                                               String rootPath, boolean dryRun);
}
