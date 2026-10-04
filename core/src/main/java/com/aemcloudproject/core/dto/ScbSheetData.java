package com.aemcloudproject.core.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WHAT: The content of one sheet, as read by ScbWorkbookParserServiceImpl.
 *
 * EXAMPLE — sheet "offer-cta" of cf-import-combined.xlsx:
 *   headers = [action, offerCtaSlug, label, url, deeplink]
 *   rows    = [ {action=CREATE, offerCtaSlug=book-now,     label=Book Now,     url=https://example.com/book,    deeplink=app://book},
 *               {action=CREATE, offerCtaSlug=shop-online,  label=Shop Online,  url=https://example.com/shop,    deeplink=app://shop},
 *               ...
 *               {action=CREATE, offerCtaSlug=view-details, label=View Details, url=https://example.com/details} ]   <- no deeplink: blank cells are left out
 *
 * A row value is either a String or a List of Strings. A cell starting with /content that has
 * several lines becomes a list — e.g. sheet "merchant-details", row 2:
 *   {action=CREATE, merchantSlug=fine-eats-sg,
 *    merchantVenues=[/content/dam/aemcloudproject/cfs/offer-listing/venues/central-mall-branch,
 *                    /content/dam/aemcloudproject/cfs/offer-listing/venues/orchard-road-outlet]}
 *
 * JSON (Jackson uses the getters): {"headers":[...],"rows":[{...,"merchantVenues":["...","..."]},...]}
 */
public class ScbSheetData {

    // Row 1, in column order, without blank or invalid headers.
    private final List<String> headers = new ArrayList<>();
    // Rows 2..n, one map per non-blank row: header -> String, or List<String> for a multi-value cell.
    private final List<Map<String, Object>> rows = new ArrayList<>();

    /** OUTPUT: e.g. [action, offerCtaSlug, label, url, deeplink]. The parser adds to this list. */
    public List<String> getHeaders() {
        return headers;
    }

    /**
     * OUTPUT: e.g. [{action=CREATE, offerCtaSlug=book-now, ...}, ...]. Values are String or
     * List&lt;String&gt; (multi-value cells). The parser adds to this list.
     */
    public List<Map<String, Object>> getRows() {
        return rows;
    }
}
