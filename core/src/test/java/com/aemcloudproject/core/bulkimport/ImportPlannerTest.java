package com.aemcloudproject.core.bulkimport;

import com.aemcloudproject.core.testcontext.AppAemContext;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(AemContextExtension.class)
class ImportPlannerTest {

    private static final String ROOT = "/content/dam/test/cfs";
    private static final String OFFERS = ROOT + "/offer-listing";
    private static final String CARDS = OFFERS + "/cards";
    private static final String CATEGORIES = OFFERS + "/categories";

    private final AemContext context = AppAemContext.newAemContext();

    @BeforeEach
    void setUp() {
        for (ModelSpec model : ModelSpec.values()) {
            installModel(model);
        }
        context.create().resource(ROOT + "/unrelated.jpg");
    }

    @Test
    void createsANewOfferAtItsSlugAndKeepsTheSuppliedId() {
        ImportPlan plan = plan(true, offer(2, 1067987L, "mount-faber"));

        WriteOp op = only(plan, ModelSpec.OFFER_DETAIL);
        assertTrue(op.isCreate());
        assertEquals(OFFERS + "/mount-faber", op.getPath());
        assertEquals(1067987L, op.getValues().get("offerID"));
        assertEquals("Mount Faber offer", op.getTitle());
    }

    @Test
    void updatesAFragmentFoundBySlugAndNeverChangesItsId() {
        existing(ModelSpec.OFFER_CARD, CARDS, "visa", master("offerCardSlug", "visa", "id", 7L));

        ImportPlan plan = plan(true, row(ModelSpec.OFFER_CARD, 2, "offerCardSlug", "visa", "name", "Visa"));

        WriteOp op = only(plan, ModelSpec.OFFER_CARD);
        assertFalse(op.isCreate());
        assertEquals(CARDS + "/visa", op.getPath());
        assertFalse(op.getValues().containsKey("id"));
    }

    @Test
    void findsAnOfferByIdWhenTheSlugIsBlank() {
        existing(ModelSpec.OFFER_DETAIL, OFFERS, "old-name", master("offerSlug", "old-name", "offerID", 42L));

        ImportPlan plan = plan(true, offer(2, 42L, null));

        WriteOp op = only(plan, ModelSpec.OFFER_DETAIL);
        assertFalse(op.isCreate());
        assertEquals(OFFERS + "/old-name", op.getPath());
    }

    @Test
    void findsAFragmentWhoseNodeNameDiffersFromItsSlug() {
        existing(ModelSpec.OFFER_CARD, CARDS, "master-card-legacy", master("offerCardSlug", "mastercard", "id", 3L));

        ImportPlan plan = plan(true, row(ModelSpec.OFFER_CARD, 2, "offerCardSlug", "mastercard"));

        assertEquals(CARDS + "/master-card-legacy", only(plan, ModelSpec.OFFER_CARD).getPath());
    }

    @Test
    void resolvesReferenceSlugsToPathsAcrossSheetsAndExistingFragments() {
        existing(ModelSpec.OFFER_CARD, CARDS, "visa", master("offerCardSlug", "visa", "id", 7L));
        ParsedRow offer = offer(2, null, "duty-free");
        offer.getValues().put("offerCards", Arrays.asList("visa", "visa"));
        offer.getValues().put("offerCta", Collections.singletonList("register-now"));

        ImportPlan plan = plan(true, offer, row(ModelSpec.OFFER_CTA, 2, "offerCtaSlug", "register-now"));

        WriteOp op = only(plan, ModelSpec.OFFER_DETAIL);
        assertArrayEquals(new String[] {CARDS + "/visa"}, (String[]) op.getValues().get("offerCards"), "duplicates collapse");
        assertArrayEquals(new String[] {OFFERS + "/offer-cta/register-now"}, (String[]) op.getValues().get("offerCta"));
    }

    @Test
    void createsAStubForAnUndefinedSlugWithAGeneratedIdAndTitle() {
        existing(ModelSpec.CATEGORY, CATEGORIES, "travel", master("offerCategorySlug", "travel", "ID", 7.0d));
        ParsedRow offer = offer(3, null, "marathon");
        offer.getValues().put("offerCategories", Collections.singletonList("health-fitness"));

        ImportPlan plan = plan(true, offer);

        WriteOp stub = only(plan, ModelSpec.CATEGORY);
        assertTrue(stub.isStub());
        assertEquals(CATEGORIES + "/health-fitness", stub.getPath());
        assertEquals("Health Fitness", stub.getTitle());
        assertEquals(8L, stub.getValues().get("ID"), "above the highest existing ID, even one stored as a double");
        assertEquals("OfferDetail", stub.getSheet());
        assertEquals(3, stub.getRow());
        assertFalse(only(plan, ModelSpec.OFFER_DETAIL).hasErrors());
    }

    @Test
    void failsTheReferencingRowWhenStubsAreOff() {
        ParsedRow offer = offer(2, null, "marathon");
        offer.getValues().put("offerCategories", Collections.singletonList("health-fitness"));

        ImportPlan plan = plan(false, offer);

        WriteOp op = only(plan, ModelSpec.OFFER_DETAIL);
        assertTrue(op.getErrors().get(0).contains("'health-fitness', which does not exist"));
        assertTrue(ops(plan, ModelSpec.CATEGORY).isEmpty());
    }

    @Test
    void rejectsDuplicateSlugsButKeepsTheFirstRow() {
        ImportPlan plan = plan(true,
                row(ModelSpec.OFFER_CTA, 2, "offerCtaSlug", "find-out-more"),
                row(ModelSpec.OFFER_CTA, 3, "offerCtaSlug", "find-out-more"));

        List<WriteOp> ctas = ops(plan, ModelSpec.OFFER_CTA);
        assertFalse(ctas.get(0).hasErrors());
        assertTrue(ctas.get(1).getErrors().get(0).startsWith("duplicate offerCtaSlug"));
    }

    @Test
    void rejectsAnOfferWhoseIdAndSlugPointAtDifferentFragments() {
        existing(ModelSpec.OFFER_DETAIL, OFFERS, "a", master("offerSlug", "a", "offerID", 1L));
        existing(ModelSpec.OFFER_DETAIL, OFFERS, "b", master("offerSlug", "b", "offerID", 2L));

        ImportPlan plan = plan(true, offer(2, 1L, "b"));

        assertTrue(only(plan, ModelSpec.OFFER_DETAIL).getErrors().get(0).contains("but offerSlug 'b' is"));
    }

    @Test
    void rejectsInvalidSlugs() {
        ImportPlan plan = plan(true, row(ModelSpec.OFFER_CTA, 2, "offerCtaSlug", "Find Out More"));

        assertTrue(only(plan, ModelSpec.OFFER_CTA).getErrors().get(0).startsWith("invalid offerCtaSlug"));
    }

    @Test
    void detectsParentLoopsAndFailsOffersThatDependOnThem() {
        ParsedRow a = row(ModelSpec.CATEGORY, 2, "offerCategorySlug", "a");
        a.getValues().put("parent", Collections.singletonList("b"));
        ParsedRow b = row(ModelSpec.CATEGORY, 3, "offerCategorySlug", "b");
        b.getValues().put("parent", Collections.singletonList("a"));
        ParsedRow offer = offer(2, null, "o");
        offer.getValues().put("offerCategories", Collections.singletonList("a"));

        ImportPlan plan = plan(true, a, b, offer);

        ops(plan, ModelSpec.CATEGORY).forEach(op -> assertTrue(op.getErrors().get(0).contains("makes a loop")));
        assertTrue(only(plan, ModelSpec.OFFER_DETAIL).getErrors().get(0).contains("will not be created"));
    }

    @Test
    void doesNotStubASlugWhoseOwnRowFailed() {
        ParsedRow broken = row(ModelSpec.CATEGORY, 2, "offerCategorySlug", "travel");
        broken.getErrors().add("'name': something wrong");
        ParsedRow offer = offer(2, null, "o");
        offer.getValues().put("offerCategories", Collections.singletonList("travel"));

        ImportPlan plan = plan(true, broken, offer);

        assertEquals(1, ops(plan, ModelSpec.CATEGORY).size(), "no stub alongside the failed row");
        assertTrue(only(plan, ModelSpec.OFFER_DETAIL).getErrors().get(0).contains("whose own row has errors"));
    }

    @Test
    void linksToAnExistingFragmentEvenWhenItsUpdateRowFailed() {
        existing(ModelSpec.CATEGORY, CATEGORIES, "travel", master("offerCategorySlug", "travel", "ID", 1L));
        ParsedRow broken = row(ModelSpec.CATEGORY, 2, "offerCategorySlug", "travel");
        broken.getErrors().add("'name': something wrong");
        ParsedRow offer = offer(2, null, "o");
        offer.getValues().put("offerCategories", Collections.singletonList("travel"));

        ImportPlan plan = plan(true, broken, offer);

        assertFalse(only(plan, ModelSpec.OFFER_DETAIL).hasErrors());
    }

    @Test
    void warnsAboutMissingAssetPathsButNotExternalUrls() {
        ParsedRow cta = row(ModelSpec.OFFER_CTA, 2, "offerCtaSlug", "go", "url", "https://www.example.com/");
        ParsedRow offer = offer(2, null, "o");
        offer.getValues().put("offerImage", "/content/dam/missing.jpg");

        ImportPlan plan = plan(true, cta, offer);

        assertTrue(only(plan, ModelSpec.OFFER_CTA).getWarnings().isEmpty());
        assertTrue(only(plan, ModelSpec.OFFER_DETAIL).getWarnings().get(0).contains("/content/dam/missing.jpg"));
    }

    @Test
    void refusesToPlanAgainstAnOutOfDateModel() throws PersistenceException {
        context.resourceResolver().delete(context.resourceResolver().getResource(
                ModelSpec.OFFER_DETAIL.getModelPath() + "/jcr:content/model/cq:dialog/content/items/f1"));

        ImportPlan plan = plan(true, offer(2, null, "o"));

        assertTrue(plan.isFatal());
        assertTrue(plan.getFatalErrors().get(0).contains("[offerSlug]"));
        assertTrue(plan.getOps().isEmpty());
    }

    @Test
    void refusesATargetOutsideTheDam() {
        ImportPlan plan = new ImportPlanner(context.resourceResolver(), "/content/site", true).plan(new ParsedWorkbook());

        assertTrue(plan.isFatal());
    }

    @Test
    void refusesToCreateOverANodeThatIsNotAFragment() {
        context.create().resource(OFFERS + "/offer-cta/go");

        ImportPlan plan = plan(true, row(ModelSpec.OFFER_CTA, 2, "offerCtaSlug", "go"));

        assertTrue(only(plan, ModelSpec.OFFER_CTA).getErrors().get(0).contains("is not a offer-cta fragment"));
        assertNull(only(plan, ModelSpec.OFFER_CTA).getPath());
    }

    private ImportPlan plan(boolean createStubs, ParsedRow... rows) {
        ParsedWorkbook workbook = new ParsedWorkbook();
        for (ParsedRow row : rows) {
            workbook.add(row);
        }
        return new ImportPlanner(context.resourceResolver(), ROOT + "/", createStubs).plan(workbook);
    }

    private static ParsedRow offer(int rowNumber, Long id, String slug) {
        ParsedRow row = new ParsedRow(ModelSpec.OFFER_DETAIL, "OfferDetail", rowNumber);
        if (id != null) {
            row.getValues().put("offerID", id);
        }
        if (slug != null) {
            row.getValues().put("offerSlug", slug);
            row.getValues().put("offerTitle", "Mount Faber offer");
        }
        return row;
    }

    private static ParsedRow row(ModelSpec model, int rowNumber, Object... keysAndValues) {
        ParsedRow row = new ParsedRow(model, model.getSheetName(), rowNumber);
        for (int i = 0; i < keysAndValues.length; i += 2) {
            row.getValues().put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return row;
    }

    private static Map<String, Object> master(Object... keysAndValues) {
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            values.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return values;
    }

    private void existing(ModelSpec model, String folder, String name, Map<String, Object> master) {
        String path = folder + "/" + name;
        context.create().resource(path, "jcr:primaryType", "dam:Asset");
        context.create().resource(path + "/jcr:content/data", "cq:model", model.getModelPath());
        context.create().resource(path + "/jcr:content/data/master", master);
    }

    private void installModel(ModelSpec model) {
        String items = model.getModelPath() + "/jcr:content/model/cq:dialog/content/items";
        int i = 0;
        for (FieldSpec field : model.getFields()) {
            context.create().resource(items + "/f" + i++, "name", field.getProperty());
        }
        if (model.getIdProperty() != null && model.getField(model.getIdProperty()) == null) {
            context.create().resource(items + "/f" + i, "name", model.getIdProperty());
        }
    }

    private static List<WriteOp> ops(ImportPlan plan, ModelSpec model) {
        List<WriteOp> ops = new java.util.ArrayList<>();
        for (WriteOp op : plan.getOps()) {
            if (op.getModel() == model) {
                ops.add(op);
            }
        }
        return ops;
    }

    private static WriteOp only(ImportPlan plan, ModelSpec model) {
        List<WriteOp> ops = ops(plan, model);
        assertEquals(1, ops.size(), "ops for " + model);
        return ops.get(0);
    }
}
