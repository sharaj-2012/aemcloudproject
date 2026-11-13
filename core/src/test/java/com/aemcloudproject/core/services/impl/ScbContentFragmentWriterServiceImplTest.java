package com.aemcloudproject.core.services.impl;

import com.aemcloudproject.core.config.ScbOfferListingModel;
import com.aemcloudproject.core.dto.ScbSheetData;
import com.aemcloudproject.core.testcontext.AppAemContext;
import com.aemcloudproject.core.testcontext.ScbOfferListingTestContent;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aemcloudproject.core.testcontext.ScbOfferListingTestContent.ROOT;
import static com.aemcloudproject.core.testcontext.ScbOfferListingTestContent.scbFragment;
import static com.aemcloudproject.core.testcontext.ScbOfferListingTestContent.scbMaster;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link ScbContentFragmentWriterServiceImpl} against fragments in the {@link AemContext} repository.
 */
@ExtendWith(AemContextExtension.class)
class ScbContentFragmentWriterServiceImplTest {

    private static final String OFFERS = ROOT + "/offers";
    private static final String CARDS = ROOT + "/cards";
    private static final String IMAGE = "/content/dam/test/images/offer.png";

    private final AemContext context = AppAemContext.newAemContext();
    private final Map<String, Set<String>> pageReferences = new HashMap<>();
    private final Map<String, ScbSheetData> sheets = new LinkedHashMap<>();

    private ScbContentFragmentWriterServiceImpl writer;

    @BeforeEach
    void setUp() throws Exception {
        ScbOfferListingTestContent.scbSetUp(context);
        writer = new ScbContentFragmentWriterServiceImpl() {
            @Override
            Set<String> scbPageReferences(ResourceResolver resolver, String fragmentPath) {
                return pageReferences.getOrDefault(fragmentPath, Collections.emptySet());
            }
        };
    }

    @Test
    void unknownSheetFails() {
        scbSheet("offers", "action", "offerSlug");
        scbRow("offers", 2, "action", "CREATE", "offerSlug", "o1");

        Map<String, Object> result = scbRun(true).get(0);

        assertEquals("offers", result.get("sheet"));
        assertNull(result.get("row"));
        assertEquals("FAIL", result.get("result"));
        assertEquals("Sheet: \"offers\" is not a known CF model", result.get("details"));
    }

    @Test
    void missingModelFailsTheSheet() throws Exception {
        context.resourceResolver().delete(context.resourceResolver()
                .getResource(ScbOfferListingModel.OFFER_CTA.getModelPath()));
        context.resourceResolver().commit();
        scbSheet("offer-cta", "action", "offerCtaSlug");
        scbRow("offer-cta", 2, "action", "CREATE", "offerCtaSlug", "apply");

        Map<String, Object> result = scbRun(true).get(0);

        assertEquals("FAIL", result.get("result"));
        assertEquals("Sheet: model not found at " + ScbOfferListingModel.OFFER_CTA.getModelPath(),
                result.get("details"));
    }

    @Test
    void sheetWithoutActionsIsSkipped() {
        scbSheet("offer-cta", "action", "offerCtaSlug", "label");
        scbRow("offer-cta", 2, "offerCtaSlug", "apply", "label", "Apply");

        List<Map<String, Object>> results = scbRun(true);

        assertEquals(1, results.size());
        assertEquals("SKIP", results.get(0).get("result"));
        assertEquals("Sheet: no row has an action (CREATE, UPDATE or DELETE)", results.get(0).get("details"));
    }

    @Test
    void rowsWithoutAnActionAreIgnored() {
        scbSheet("offer-cta", "action", "offerCtaSlug", "label");
        scbRow("offer-cta", 2, "offerCtaSlug", "apply", "label", "Apply");
        scbRow("offer-cta", 3, "action", "CREATE", "offerCtaSlug", "learn", "label", "Learn");

        List<Map<String, Object>> results = scbRun(true);

        assertEquals(1, results.size());
        assertEquals(3, results.get(0).get("row"));
    }

    @Test
    void dryRunCreateAssignsTheNextIdsAndWritesNothing() throws Exception {
        scbFragment(context, ScbOfferListingModel.OFFER_DETAIL, "existing", "offerID", "100005", "offerTitle", "Old");
        scbSheet("offer-detail", "action", "offerSlug", "offerTitle");
        scbRow("offer-detail", 2, "action", "CREATE", "offerSlug", "o1", "offerTitle", "One",
                "offerImage", IMAGE, "alternateText", "Alt text");
        scbRow("offer-detail", 3, "action", "CREATE", "offerSlug", "o2", "offerTitle", "Two",
                "offerImage", IMAGE, "alternateText", "Alt text");

        List<Map<String, Object>> results = scbRun(true);

        assertEquals("PASS", results.get(0).get("result"));
        assertEquals(OFFERS + "/o1 (offerID 100006)", results.get(0).get("details"));
        assertEquals(OFFERS + "/o2 (offerID 100007)", results.get(1).get("details"));
        assertNull(context.resourceResolver().getResource(OFFERS + "/o1"));
        assertNull(context.resourceResolver().getResource(OFFERS + "/o2"));
    }

    @Test
    void nextIdIsAboveTheHighestIdInTheSheetAndStartsAtTheModelStart() {
        scbSheet("category", "action", "offerCategorySlug", "name", "ID");
        scbRow("category", 2, "action", "CREATE", "offerCategorySlug", "dining", "name", "Dining");
        scbSheet("offer-detail", "action", "offerSlug", "offerTitle", "offerID");
        scbRow("offer-detail", 2, "offerSlug", "old", "offerID", "100050");
        scbRow("offer-detail", 3, "action", "CREATE", "offerSlug", "o1", "offerTitle", "One",
                "offerImage", IMAGE, "alternateText", "Alt text");

        List<Map<String, Object>> results = scbRun(true);

        assertEquals(ROOT + "/categories/dining (ID 1001)", results.get(0).get("details"));
        assertEquals(OFFERS + "/o1 (offerID 100051)", results.get(1).get("details"));
    }

    @Test
    void finalRunCreateWritesTypedValuesTitleAndId() {
        scbSheet("offer-detail", "action", "offerSlug", "offerID", "offerTitle", "offerEndDate", "featured",
                "priority", "score", "offerCards", "offerCta");
        scbRow("offer-detail", 2, "action", "CREATE", "offerSlug", "summer-sale", "offerTitle", "Summer sale",
                "offerEndDate", "2026-12-31T00:00:00.000+05:30", "featured", "Yes", "priority", "5", "score", "1.5",
                "offerCards", Arrays.asList(CARDS + "/gold", CARDS + "/platinum"), "offerCta", ROOT + "/offer-cta/apply",
                "offerImage", IMAGE, "alternateText", "Alt text");

        Map<String, Object> result = scbRun(false).get(0);

        assertEquals("PASS", result.get("result"));
        assertEquals(OFFERS + "/summer-sale (offerID 100001)", result.get("details"));
        ValueMap master = scbMaster(context, OFFERS + "/summer-sale");
        assertEquals("Summer sale", master.get("offerTitle"));
        assertEquals("100001", master.get("offerID"));
        assertEquals(Boolean.TRUE, master.get("featured"));
        assertEquals(5L, master.get("priority"));
        assertEquals(1.5d, master.get("score"));
        assertArrayEquals(new String[] {CARDS + "/gold", CARDS + "/platinum"}, master.get("offerCards", String[].class));
        assertEquals(ROOT + "/offer-cta/apply", master.get("offerCta"));
        assertEquals(OffsetDateTime.parse("2026-12-31T00:00:00.000+05:30").toInstant().toEpochMilli(),
                master.get("offerEndDate", Calendar.class).getTimeInMillis());
        assertEquals("Summer sale", context.resourceResolver().getResource(OFFERS + "/summer-sale/jcr:content")
                .getValueMap().get("jcr:title"));
    }

    @Test
    void datesAreReadWithOrWithoutTimeAndOffset() {
        scbSheet("offer-detail", "action", "offerSlug", "offerTitle", "offerEndDate");
        scbRow("offer-detail", 2, "action", "CREATE", "offerSlug", "d1", "offerTitle", "D1",
                "offerEndDate", "2026-12-31",
                "offerImage", IMAGE, "alternateText", "Alt text");
        scbRow("offer-detail", 3, "action", "CREATE", "offerSlug", "d2", "offerTitle", "D2",
                "offerEndDate", "2026-12-31T09:30",
                "offerImage", IMAGE, "alternateText", "Alt text");
        scbRow("offer-detail", 4, "action", "CREATE", "offerSlug", "d3", "offerTitle", "D3",
                "offerEndDate", "2026-12-31T09:30:00Z",
                "offerImage", IMAGE, "alternateText", "Alt text");

        scbRun(false);

        ZoneId zone = ZoneId.systemDefault();
        assertEquals(LocalDate.parse("2026-12-31").atStartOfDay(zone).toInstant().toEpochMilli(),
                scbMaster(context, OFFERS + "/d1").get("offerEndDate", Calendar.class).getTimeInMillis());
        assertEquals(LocalDateTime.parse("2026-12-31T09:30").atZone(zone).toInstant().toEpochMilli(),
                scbMaster(context, OFFERS + "/d2").get("offerEndDate", Calendar.class).getTimeInMillis());
        assertEquals(OffsetDateTime.parse("2026-12-31T09:30:00Z").toInstant().toEpochMilli(),
                scbMaster(context, OFFERS + "/d3").get("offerEndDate", Calendar.class).getTimeInMillis());
    }

    @Test
    void finalRunCreateUsesTheSlugAsTitleAndRecreatesAMissingModelFolder() throws Exception {
        context.resourceResolver().delete(context.resourceResolver().getResource(ROOT + "/merchants/venues"));
        context.resourceResolver().commit();
        scbSheet("merchant-venue", "action", "merchantVenueSlug", "address");
        scbRow("merchant-venue", 2, "action", "CREATE", "merchantVenueSlug", "mall-1", "address", "Main street");

        Map<String, Object> result = scbRun(false).get(0);

        assertEquals("PASS", result.get("result"));
        assertEquals(ROOT + "/merchants/venues/mall-1", result.get("details"));
        assertEquals("mall-1", context.resourceResolver().getResource(ROOT + "/merchants/venues/mall-1/jcr:content")
                .getValueMap().get("jcr:title"));
        assertEquals("Main street", scbMaster(context, ROOT + "/merchants/venues/mall-1").get("address"));
    }

    @Test
    void createFailsWhenTheFragmentExists() throws Exception {
        scbFragment(context, ScbOfferListingModel.OFFER_CTA, "apply", "label", "Apply");
        scbSheet("offer-cta", "action", "offerCtaSlug", "label");
        scbRow("offer-cta", 2, "action", "CREATE", "offerCtaSlug", "apply", "label", "Apply again");

        Map<String, Object> result = scbRun(true).get(0);

        assertEquals("FAIL", result.get("result"));
        assertEquals("Fragment: already exists at " + ROOT + "/offer-cta/apply", result.get("details"));
    }

    @Test
    void invalidOrMissingSlugFails() {
        scbSheet("offer-detail", "action", "offerSlug", "offerTitle");
        scbRow("offer-detail", 2, "action", "CREATE", "offerSlug", "Summer Sale", "offerTitle", "S",
                "offerImage", IMAGE, "alternateText", "Alt text");
        scbRow("offer-detail", 3, "action", "UPDATE", "offerTitle", "S");

        List<Map<String, Object>> results = scbRun(true);

        assertEquals("offerSlug: \"Summer Sale\" must be lowercase letters, digits, - or _",
                results.get(0).get("details"));
        assertEquals("FAIL", results.get(1).get("result"));
        assertEquals("offerSlug: \"\" must be lowercase letters, digits, - or _", results.get(1).get("details"));
        assertEquals("UPDATE", results.get(1).get("action"));
    }

    @Test
    void columnThatIsNotAModelFieldFails() {
        scbSheet("offer-detail", "action", "offerSlug", "offerTitle", "colour");
        scbRow("offer-detail", 2, "action", "CREATE", "offerSlug", "o1", "offerTitle", "One", "colour", "red",
                "offerImage", IMAGE, "alternateText", "Alt text");

        assertEquals("colour: not a field of model offer-detail", scbRun(true).get(0).get("details"));
    }

    @Test
    void requiredFieldsMustBeFilledOnCreate() {
        scbSheet("offer-detail", "action", "offerSlug", "priority");
        scbRow("offer-detail", 2, "action", "CREATE", "offerSlug", "o1", "priority", "1");

        assertEquals("offerTitle, offerImage, alternateText: required", scbRun(true).get(0).get("details"));
    }

    @Test
    void requiredFieldsCannotBeEmptiedOnUpdateButTheIdCanBeLeftEmpty() throws Exception {
        scbFragment(context, ScbOfferListingModel.OFFER_DETAIL, "o1", "offerID", "100001", "offerTitle", "One");
        scbSheet("offer-detail", "action", "offerSlug", "offerID", "offerTitle");
        scbRow("offer-detail", 2, "action", "UPDATE", "offerSlug", "o1");
        scbRow("offer-detail", 3, "action", "UPDATE", "offerSlug", "o1", "offerTitle", "Renamed");

        List<Map<String, Object>> results = scbRun(true);

        assertEquals("offerTitle: required", results.get(0).get("details"));
        assertEquals("PASS", results.get(1).get("result"));
    }

    @Test
    void updateListsClearedFieldsInTheDryRunAndChangesNothing() throws Exception {
        scbFragment(context, ScbOfferListingModel.OFFER_DETAIL, "o1", "offerID", "100001", "offerTitle", "Old",
                "priority", 3L, "featured", true);
        scbSheet("offer-detail", "action", "offerSlug", "offerID", "offerTitle", "priority", "score", "featured");
        scbRow("offer-detail", 2, "action", "UPDATE", "offerSlug", "o1", "offerTitle", "New", "featured", "no");

        Map<String, Object> result = scbRun(true).get(0);

        assertEquals("PASS", result.get("result"));
        assertEquals(OFFERS + "/o1 (cleared: priority)", result.get("details"));
        ValueMap master = scbMaster(context, OFFERS + "/o1");
        assertEquals("Old", master.get("offerTitle"));
        assertEquals(3L, master.get("priority"));
        assertEquals(Boolean.TRUE, master.get("featured"));
    }

    @Test
    void finalRunUpdateWritesClearsBlankCellsKeepsTheIdAndLeavesMissingColumns() throws Exception {
        scbFragment(context, ScbOfferListingModel.OFFER_DETAIL, "o1", "offerID", "100001", "offerTitle", "Old",
                "priority", 3L, "featured", true, "score", 2.5d);
        scbSheet("offer-detail", "action", "offerSlug", "offerID", "offerTitle", "priority", "featured");
        scbRow("offer-detail", 2, "action", "UPDATE", "offerSlug", "o1", "offerTitle", "New", "featured", "0");

        Map<String, Object> result = scbRun(false).get(0);

        assertEquals("PASS", result.get("result"));
        ValueMap master = scbMaster(context, OFFERS + "/o1");
        assertEquals("New", master.get("offerTitle"));
        assertEquals("100001", master.get("offerID"));
        assertFalse(master.containsKey("priority"));
        assertEquals(Boolean.FALSE, master.get("featured"));
        assertEquals(2.5d, master.get("score"));
        assertEquals("New", context.resourceResolver().getResource(OFFERS + "/o1/jcr:content").getValueMap()
                .get("jcr:title"));
    }

    @Test
    void updateFailsForAMissingFragmentOrAFragmentOfAnotherModel() throws Exception {
        String wrong = scbFragment(context, ScbOfferListingModel.OFFER_DETAIL, "wrong", "offerTitle", "W");
        context.resourceResolver().getResource(wrong + "/jcr:content/data").adaptTo(ModifiableValueMap.class)
                .put("cq:model", ScbOfferListingModel.CATEGORY.getModelPath());
        context.resourceResolver().commit();
        scbSheet("offer-detail", "action", "offerSlug", "offerTitle");
        scbRow("offer-detail", 2, "action", "UPDATE", "offerSlug", "missing", "offerTitle", "M");
        scbRow("offer-detail", 3, "action", "UPDATE", "offerSlug", "wrong", "offerTitle", "W");

        List<Map<String, Object>> results = scbRun(true);

        assertEquals("Fragment: not found at " + OFFERS + "/missing", results.get(0).get("details"));
        assertEquals("Fragment: uses model " + ScbOfferListingModel.CATEGORY.getModelPath() + ", not "
                + ScbOfferListingModel.OFFER_DETAIL.getModelPath(), results.get(1).get("details"));
    }

    @Test
    void valuesThatDoNotFitTheFieldTypeFail() {
        scbSheet("offer-detail", "action", "offerSlug", "offerTitle", "priority", "score", "featured",
                "offerEndDate");
        scbRow("offer-detail", 2, "action", "CREATE", "offerSlug", "a", "offerTitle", "A", "priority", "five",
                "offerImage", IMAGE, "alternateText", "Alt text");
        scbRow("offer-detail", 3, "action", "CREATE", "offerSlug", "b", "offerTitle", "B", "score", "x",
                "offerImage", IMAGE, "alternateText", "Alt text");
        scbRow("offer-detail", 4, "action", "CREATE", "offerSlug", "c", "offerTitle", "C", "featured", "maybe",
                "offerImage", IMAGE, "alternateText", "Alt text");
        scbRow("offer-detail", 5, "action", "CREATE", "offerSlug", "d", "offerTitle", "D",
                "offerEndDate", "31/12/2026",
                "offerImage", IMAGE, "alternateText", "Alt text");
        scbRow("offer-detail", 6, "action", "CREATE", "offerSlug", "e",
                "offerTitle", Arrays.asList("/content/a", "/content/b"),
                "offerImage", IMAGE, "alternateText", "Alt text");

        List<String> details = scbRun(true).stream().map(result -> (String) result.get("details"))
                .collect(Collectors.toList());

        assertIterableEquals(Arrays.asList(
                "priority: \"five\" is not a whole number",
                "score: \"x\" is not a number",
                "featured: \"maybe\" must be true/false, 1/0 or yes/no",
                "offerEndDate: \"31/12/2026\" is not a date (use e.g. 2026-10-01T00:00:00.000+05:30)",
                "offerTitle: takes one value, but the cell has several lines"), details);
    }

    @Test
    void failedRowsDoNotUseUpAnId() {
        scbSheet("offer-detail", "action", "offerSlug", "offerTitle", "priority");
        scbRow("offer-detail", 2, "action", "CREATE", "offerSlug", "a", "offerTitle", "A", "priority", "five",
                "offerImage", IMAGE, "alternateText", "Alt text");
        scbRow("offer-detail", 3, "action", "CREATE", "offerSlug", "b", "offerTitle", "B",
                "offerImage", IMAGE, "alternateText", "Alt text");

        assertEquals(OFFERS + "/b (offerID 100001)", scbRun(true).get(1).get("details"));
    }

    @Test
    void dryRunDeleteKeepsTheFragment() throws Exception {
        scbFragment(context, ScbOfferListingModel.OFFER_CTA, "apply", "label", "Apply");
        scbSheet("offer-cta", "action", "offerCtaSlug");
        scbRow("offer-cta", 2, "action", "DELETE", "offerCtaSlug", "apply");

        Map<String, Object> result = scbRun(true).get(0);

        assertEquals("PASS", result.get("result"));
        assertEquals(ROOT + "/offer-cta/apply (to be deleted)", result.get("details"));
        assertNotNull(context.resourceResolver().getResource(ROOT + "/offer-cta/apply"));
    }

    @Test
    void finalRunDeleteRemovesTheFragment() throws Exception {
        scbFragment(context, ScbOfferListingModel.OFFER_CTA, "apply", "label", "Apply");
        scbSheet("offer-cta", "action", "offerCtaSlug");
        scbRow("offer-cta", 2, "action", "DELETE", "offerCtaSlug", "apply");

        Map<String, Object> result = scbRun(false).get(0);

        assertEquals(ROOT + "/offer-cta/apply (deleted)", result.get("details"));
        assertNull(context.resourceResolver().getResource(ROOT + "/offer-cta/apply"));
    }

    @Test
    void deleteFailsForAMissingFragmentOrABadSlug() {
        scbSheet("offer-cta", "action", "offerCtaSlug");
        scbRow("offer-cta", 2, "action", "DELETE", "offerCtaSlug", "missing");
        scbRow("offer-cta", 3, "action", "DELETE", "offerCtaSlug", "Bad Slug");

        List<Map<String, Object>> results = scbRun(true);

        assertEquals("Fragment: not found at " + ROOT + "/offer-cta/missing", results.get(0).get("details"));
        assertEquals("offerCtaSlug: \"Bad Slug\" must be lowercase letters, digits, - or _",
                results.get(1).get("details"));
    }

    @Test
    void deleteWarnsWhileAnotherFragmentPointsToIt() throws Exception {
        String card = scbFragment(context, ScbOfferListingModel.OFFER_CARD, "gold", "ID", "1001");
        String offer = scbFragment(context, ScbOfferListingModel.OFFER_DETAIL, "o1", "offerTitle", "One",
                "offerCards", new String[] {card});
        scbSheet("offer-card", "action", "offerCardSlug");
        scbRow("offer-card", 2, "action", "DELETE", "offerCardSlug", "gold");

        Map<String, Object> result = scbRun(false).get(0);

        assertEquals("WARN", result.get("result"));
        assertEquals("Not deleted: referenced by " + offer + " (remove the references first)", result.get("details"));
        assertNotNull(context.resourceResolver().getResource(card));
    }

    @Test
    void deletingTheReferrerInTheSameRunLetsTheReferencedFragmentGo() throws Exception {
        String cta = scbFragment(context, ScbOfferListingModel.OFFER_CTA, "apply", "label", "Apply");
        scbFragment(context, ScbOfferListingModel.OFFER_DETAIL, "o1", "offerTitle", "One", "offerCta", cta);
        scbSheet("offer-cta", "action", "offerCtaSlug");
        scbRow("offer-cta", 2, "action", "DELETE", "offerCtaSlug", "apply");
        scbSheet("offer-detail", "action", "offerSlug");
        scbRow("offer-detail", 2, "action", "DELETE", "offerSlug", "o1");

        List<Map<String, Object>> dryRun = scbRun(true);

        assertEquals("offer-detail", dryRun.get(0).get("sheet"));
        assertEquals("offer-cta", dryRun.get(1).get("sheet"));
        assertEquals("PASS", dryRun.get(1).get("result"));

        List<Map<String, Object>> finalRun = scbRun(false);

        assertEquals("PASS", finalRun.get(1).get("result"));
        assertNull(context.resourceResolver().getResource(cta));
        assertNull(context.resourceResolver().getResource(OFFERS + "/o1"));
    }

    @Test
    void deleteWarnsForPageReferencesAndPublishedFragmentsAndShowsAtMostFiveReferences() throws Exception {
        String cta = scbFragment(context, ScbOfferListingModel.OFFER_CTA, "apply", "label", "Apply");
        ScbOfferListingTestContent.scbPublish(context, cta);
        Set<String> pages = new LinkedHashSet<>();
        for (int i = 1; i <= 6; i++) {
            pages.add("/content/site/page" + i);
        }
        pageReferences.put(cta, pages);
        scbSheet("offer-cta", "action", "offerCtaSlug");
        scbRow("offer-cta", 2, "action", "DELETE", "offerCtaSlug", "apply");

        Map<String, Object> result = scbRun(false).get(0);

        assertEquals("WARN", result.get("result"));
        assertEquals("Not deleted: referenced by /content/site/page1, /content/site/page2, /content/site/page3, "
                + "/content/site/page4, /content/site/page5 and 1 more (remove the references first); "
                + "still published (unpublish it first)", result.get("details"));
    }

    @Test
    void deletesRunAfterCreatesAndUpdatesInReverseModelOrder() throws Exception {
        scbFragment(context, ScbOfferListingModel.MERCHANT_VENUE, "v1", "name", "V1");
        scbFragment(context, ScbOfferListingModel.OFFER_DETAIL, "o1", "offerTitle", "One");
        scbSheet("merchant-venue", "action", "merchantVenueSlug");
        scbRow("merchant-venue", 2, "action", "DELETE", "merchantVenueSlug", "v1");
        scbSheet("offer-detail", "action", "offerSlug", "offerTitle");
        scbRow("offer-detail", 2, "action", "DELETE", "offerSlug", "o1");
        scbRow("offer-detail", 3, "action", "CREATE", "offerSlug", "o2", "offerTitle", "Two",
                "offerImage", IMAGE, "alternateText", "Alt text");

        List<String> order = scbRun(true).stream()
                .map(result -> result.get("sheet") + ":" + result.get("action") + ":" + result.get("row"))
                .collect(Collectors.toList());

        assertIterableEquals(Arrays.asList("offer-detail:CREATE:3", "offer-detail:DELETE:2",
                "merchant-venue:DELETE:2"), order);
    }

    private List<Map<String, Object>> scbRun(boolean dryRun) {
        return writer.scbWriteFragments(context.resourceResolver(), sheets, ROOT, dryRun);
    }

    private void scbSheet(String name, String... headers) {
        ScbSheetData data = new ScbSheetData();
        data.getHeaders().addAll(Arrays.asList(headers));
        sheets.put(name, data);
    }

    private void scbRow(String sheet, int rowNumber, Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            row.put((String) values[i], values[i + 1]);
        }
        sheets.get(sheet).getRows().add(row);
        sheets.get(sheet).getRowNumbers().add(rowNumber);
    }
}
