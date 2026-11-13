package com.aemcloudproject.core.utils;

import com.aemcloudproject.core.config.ScbOfferListingModel;
import com.aemcloudproject.core.testcontext.AppAemContext;
import com.aemcloudproject.core.testcontext.ScbOfferListingTestContent;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.aemcloudproject.core.testcontext.ScbOfferListingTestContent.ROOT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link ScbCfBulkUploadUtil}.
 */
@ExtendWith(AemContextExtension.class)
class ScbCfBulkUploadUtilTest {

    private final AemContext context = AppAemContext.newAemContext();

    @BeforeEach
    void setUp() throws Exception {
        ScbOfferListingTestContent.scbSetUp(context);
    }

    @Test
    void scbGetOrCreateFolderReturnsAnExistingFolder() throws Exception {
        Resource existing = context.resourceResolver().getResource(ROOT);
        assertSame(existing.getPath(),
                ScbCfBulkUploadUtil.scbGetOrCreateFolder(context.resourceResolver(), ROOT).getPath());
    }

    @Test
    void scbGetOrCreateFolderCreatesMissingParentsAsOrderedFolders() throws Exception {
        Resource folder = ScbCfBulkUploadUtil.scbGetOrCreateFolder(context.resourceResolver(), ROOT + "/a/b/c");
        assertEquals(ROOT + "/a/b/c", folder.getPath());
        for (String path : new String[] {ROOT + "/a", ROOT + "/a/b", ROOT + "/a/b/c"}) {
            assertEquals("sling:OrderedFolder", context.resourceResolver().getResource(path).getValueMap()
                    .get("jcr:primaryType", String.class));
        }
    }

    @Test
    void scbFindFragmentsWalksSubFoldersAndSkipsOtherNodes() throws Exception {
        String venue = ScbOfferListingTestContent.scbFragment(context, ScbOfferListingModel.MERCHANT_VENUE, "v1");
        String offer = ScbOfferListingTestContent.scbFragment(context, ScbOfferListingModel.OFFER_DETAIL, "o1");
        context.create().resource(ROOT + "/offers/plain-asset", "jcr:primaryType", "dam:Asset");
        context.create().resource(ROOT + "/offers/other", "jcr:primaryType", "nt:unstructured");

        List<String> found = ScbCfBulkUploadUtil.scbFindFragments(context.resourceResolver().getResource(ROOT))
                .stream().map(Resource::getPath).collect(Collectors.toList());

        assertEquals(2, found.size());
        assertTrue(found.contains(venue));
        assertTrue(found.contains(offer));
    }

    @Test
    void scbModelOfReadsTheFragmentModel() throws Exception {
        String offer = ScbOfferListingTestContent.scbFragment(context, ScbOfferListingModel.OFFER_DETAIL, "o1");
        assertEquals(ScbOfferListingModel.OFFER_DETAIL.getModelPath(),
                ScbCfBulkUploadUtil.scbModelOf(context.resourceResolver().getResource(offer)));
        assertNull(ScbCfBulkUploadUtil.scbModelOf(context.resourceResolver().getResource(ROOT)));
    }

    @Test
    void scbParseIdAcceptsNumbersAndDigitStrings() {
        assertEquals(42L, ScbCfBulkUploadUtil.scbParseId(42));
        assertEquals(100001L, ScbCfBulkUploadUtil.scbParseId(100001L));
        assertEquals(7L, ScbCfBulkUploadUtil.scbParseId(new BigDecimal("7.9")));
        assertEquals(1001L, ScbCfBulkUploadUtil.scbParseId(" 1001 "));
        assertEquals(999999999999999999L, ScbCfBulkUploadUtil.scbParseId("999999999999999999"));
    }

    @Test
    void scbParseIdRejectsEverythingElse() {
        assertNull(ScbCfBulkUploadUtil.scbParseId(null));
        assertNull(ScbCfBulkUploadUtil.scbParseId(""));
        assertNull(ScbCfBulkUploadUtil.scbParseId("12a"));
        assertNull(ScbCfBulkUploadUtil.scbParseId("-5"));
        assertNull(ScbCfBulkUploadUtil.scbParseId("1.5"));
        assertNull(ScbCfBulkUploadUtil.scbParseId("1234567890123456789"));
        assertNull(ScbCfBulkUploadUtil.scbParseId(Collections.singletonList("1")));
    }

    @Test
    void scbRootPathTrimsAndDropsTheTrailingSlash() {
        context.request().setParameterMap(Collections.singletonMap("rootPath", "  " + ROOT + "/ "));
        assertEquals(ROOT, ScbCfBulkUploadUtil.scbRootPath(context.request()));
    }

    @Test
    void scbRootPathIsEmptyWithoutTheParameter() {
        assertEquals("", ScbCfBulkUploadUtil.scbRootPath(context.request()));
    }

    @Test
    void scbCheckFolderAcceptsTheOfferListingFolder() {
        assertNull(ScbCfBulkUploadUtil.scbCheckFolder(context.resourceResolver(), ROOT));
    }

    @Test
    void scbCheckFolderExplainsEveryProblem() {
        context.create().resource("/content/dam/test/file/offer-listing", "jcr:primaryType", "dam:Asset");

        assertEquals("Select the fragment folder first.",
                ScbCfBulkUploadUtil.scbCheckFolder(context.resourceResolver(), ""));
        assertEquals("/content/test/offer-listing is not a DAM folder.",
                ScbCfBulkUploadUtil.scbCheckFolder(context.resourceResolver(), "/content/test/offer-listing"));
        assertEquals("Pick an offer-listing folder, e.g. /content/dam/aemcloudproject/cfs/offer-listing.",
                ScbCfBulkUploadUtil.scbCheckFolder(context.resourceResolver(), ROOT + "/offers"));
        assertEquals("No folder found at /content/dam/missing/offer-listing.",
                ScbCfBulkUploadUtil.scbCheckFolder(context.resourceResolver(), "/content/dam/missing/offer-listing"));
        assertEquals("/content/dam/test/file/offer-listing is not a folder.",
                ScbCfBulkUploadUtil.scbCheckFolder(context.resourceResolver(), "/content/dam/test/file/offer-listing"));
    }

    @Test
    void scbCheckFolderAcceptsAPlainSlingFolder() {
        context.create().resource("/content/dam/plain/offer-listing", "jcr:primaryType", "sling:Folder");
        assertNull(ScbCfBulkUploadUtil.scbCheckFolder(context.resourceResolver(), "/content/dam/plain/offer-listing"));
    }

    @Test
    void scbWriteJsonWritesStatusTypeAndBody() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("rows", 3);

        ScbCfBulkUploadUtil.scbWriteJson(context.response(), 201, body);

        assertEquals(201, context.response().getStatus());
        assertTrue(context.response().getContentType().startsWith("application/json"));
        assertEquals("UTF-8", context.response().getCharacterEncoding());
        assertEquals("{\"status\":\"ok\",\"rows\":3}", context.response().getOutputAsString());
    }
}
