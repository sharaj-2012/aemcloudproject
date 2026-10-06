package com.aemcloudproject.core.services;

import org.apache.sling.api.resource.ResourceResolver;

import java.io.IOException;
import java.io.OutputStream;

/**
 * WHAT: Builds the "Generate report" Excel of every Content Fragment under
 * /content/dam/aemcloudproject/cfs/offer-listing. Implemented by
 * ScbContentFragmentReportServiceImpl; used by ScbCfReportServlet.
 */
public interface ScbContentFragmentReportService {

    /**
     * WHAT: Reads all fragments under offer-listing and writes them as an .xlsx in the same layout
     * as the import workbook (cf-import-combined.xlsx), so a report can be edited and uploaded back.
     *
     * INPUT:  resolver — the logged-in author's resolver (only fragments they may read are included)
     *         out      — where the .xlsx bytes go (the servlet passes the HTTP response)
     *
     * OUTPUT: nothing returned; out receives a workbook with one sheet per model, e.g.
     *           sheet "offer-cta":
     *             | action | offerCtaSlug | label    | url                      | deeplink   |
     *             |        | book-now     | Book Now | https://example.com/book | app://book |
     *             ...
     *         The action column is always empty — the author fills in CREATE / UPDATE before re-uploading.
     *
     * THROWS: IOException if writing to out fails.
     */
    void scbWriteReport(ResourceResolver resolver, OutputStream out) throws IOException;
}
