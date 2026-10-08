package com.aemcloudproject.core.services;

import com.aemcloudproject.core.dto.ScbSheetData;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Reads a CF bulk upload workbook.
 */
public interface ScbWorkbookParserService {

    /**
     * Reads every sheet and checks its headers and action column.
     * Rows with a blank action are skipped.
     *
     * @param file   the .xlsx content
     * @param errors receives every problem found
     * @return the sheets, keyed by sheet name
     * @throws IOException if the file is not a readable .xlsx
     */
    Map<String, ScbSheetData> scbReadWorkbook(InputStream file, List<String> errors) throws IOException;
}
