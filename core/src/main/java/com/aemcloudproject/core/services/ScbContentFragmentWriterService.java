package com.aemcloudproject.core.services;

import com.aemcloudproject.core.dto.ScbSheetData;
import org.apache.sling.api.resource.ResourceResolver;

import java.util.List;
import java.util.Map;

/**
 * WHAT: Creates and updates Content Fragments from the rows the parser read.
 * Implemented by ScbContentFragmentWriterServiceImpl; used by ScbCfBulkUploadServlet.
 */
public interface ScbContentFragmentWriterService {

    /**
     * WHAT: For every row: CREATE makes a new fragment, UPDATE changes an existing one.
     * Each row is saved on its own, so one failing row doesn't undo the others.
     *
     * INPUT:  resolver — the logged-in author's resolver (their permissions apply)
     *         sheets   — the parser's output, e.g.
     *                    "offer-cta" -> rows [{action=CREATE, offerCtaSlug=book-now, label=Book Now,
     *                                          url=https://example.com/book, deeplink=app://book}, ...]
     *
     * OUTPUT: one result per row (or per sheet when the whole sheet is skipped), e.g.
     *           {sheet=offer-cta, name=book-now, action=CREATE, result=CREATED,
     *            path=/content/dam/aemcloudproject/cfs/offer-listing/ctas/book-now}
     *           {sheet=offer-cta, name=shop-online, action=CREATE, result=FAILED, message=already exists}
     *           {sheet=venues, result=SKIPPED, message=Not one of the CF models this uploader knows.}
     */
    List<Map<String, Object>> scbWriteFragments(ResourceResolver resolver, Map<String, ScbSheetData> sheets);
}
