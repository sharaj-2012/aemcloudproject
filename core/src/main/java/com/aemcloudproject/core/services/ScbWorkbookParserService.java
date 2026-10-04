package com.aemcloudproject.core.services;

import com.aemcloudproject.core.dto.ScbSheetData;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * WHAT: Reads a CF Bulk Upload workbook. Implemented by ScbWorkbookParserServiceImpl;
 * used by ScbCfBulkUploadServlet.
 */
public interface ScbWorkbookParserService {

    /**
     * WHAT: Reads every sheet — row 1 as headers, rows 2..n as header -> cell text — and checks
     * the headers (single word) and the action column (CREATE / UPDATE).
     *
     * INPUT:  file   — the .xlsx bytes, e.g. cf-import-combined.xlsx
     *         errors — an empty list; every problem found is added to it
     *
     * OUTPUT: sheet name -> ScbSheetData, e.g.
     *           "offer-cta" -> headers [action, offerCtaSlug, label, url, deeplink]
     *                          rows    [{action=CREATE, offerCtaSlug=book-now, label=Book Now, ...}, ...]
     *         A multi-line cell starting with /content becomes a list, e.g. in "merchant-details":
     *           merchantVenues=[/content/dam/.../venues/central-mall-branch, /content/dam/.../venues/orchard-road-outlet]
     *         errors, e.g.
     *           Sheet "offer-detail", column C: header "Offer Title" must be a single word.
     *           Sheet "offer-cta", row 4: action "DELET" must be CREATE or UPDATE.
     *
     * THROWS: IOException if the file is not a readable .xlsx.
     */
    Map<String, ScbSheetData> scbReadWorkbook(InputStream file, List<String> errors) throws IOException;
}
