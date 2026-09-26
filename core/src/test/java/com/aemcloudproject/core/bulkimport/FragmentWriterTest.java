package com.aemcloudproject.core.bulkimport;

import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.cq.dam.cfm.ContentFragmentException;
import com.adobe.cq.dam.cfm.FragmentData;
import com.adobe.cq.dam.cfm.FragmentTemplate;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FragmentWriterTest {

    private static final String PATH = "/content/dam/test/offer-listing/cards/visa";

    private final ResourceResolver resolver = mock(ResourceResolver.class);
    private final Map<String, Object> stored = new HashMap<>();
    private final Map<String, ContentElement> elements = new HashMap<>();
    private final ContentFragment fragment = mock(ContentFragment.class);

    @BeforeEach
    void setUp() throws ContentFragmentException {
        Resource resource = mock(Resource.class);
        when(resolver.getResource(PATH)).thenReturn(resource);
        when(resource.adaptTo(ContentFragment.class)).thenReturn(fragment);
        when(fragment.getTitle()).thenReturn("Visa");
        when(fragment.hasElement(anyString())).thenReturn(true);
        when(fragment.getElement(anyString())).thenAnswer(i -> element(i.getArgument(0)));
    }

    @Test
    void writesOnlyTheFieldsThatChangedAfterSavingAVersion() throws Exception {
        stored.put("name", "Visa");
        stored.put("logo", "/content/dam/old.png");
        WriteOp op = update(ModelSpec.OFFER_CARD);
        op.getValues().put("name", "Visa");
        op.getValues().put("logo", "/content/dam/new.png");

        ReportRow row = new FragmentWriter(false, true).write(resolver, op);

        assertEquals(Outcome.UPDATED, row.getOutcome());
        assertEquals("logo", row.getFieldsChanged());
        assertEquals("/content/dam/new.png", stored.get("logo"));
        verify(fragment).createVersion(anyString(), anyString());
        verify(resolver).commit();
    }

    @Test
    void reportsUnchangedWithoutVersioningWhenNothingDiffers() throws Exception {
        stored.put("offerCards", new String[] {"/a"});
        WriteOp op = update(ModelSpec.OFFER_DETAIL);
        op.getValues().put("offerID", 1067987L);
        op.getValues().put("offerCards", new String[] {"/a"});
        stored.put("offerID", 1067987.0d);

        ReportRow row = new FragmentWriter(false, true).write(resolver, op);

        assertEquals(Outcome.UNCHANGED, row.getOutcome());
        verify(fragment, never()).createVersion(anyString(), anyString());
    }

    @Test
    void replacesMultiValueReferencesAsATypedArray() {
        stored.put("merchantVenues", new String[] {"/v/a"});
        WriteOp op = update(ModelSpec.MERCHANT_DETAILS);
        op.getValues().put("merchantVenues", new String[] {"/v/a", "/v/b"});

        ReportRow row = new FragmentWriter(false, false).write(resolver, op);

        assertEquals("merchantVenues", row.getFieldsChanged());
        assertArrayEquals(new String[] {"/v/a", "/v/b"}, (String[]) stored.get("merchantVenues"));
    }

    @Test
    void writesMultilineTextAsPlainTextSoLineBreaksSurvive() throws Exception {
        WriteOp op = update(ModelSpec.MERCHANT_VENUE);
        op.getValues().put("address", "542 Balestier Rd\nSingapore");

        new FragmentWriter(false, false).write(resolver, op);

        assertEquals("542 Balestier Rd\nSingapore", stored.get("address"));
        verify(fragment.getElement("address")).setContent("542 Balestier Rd\nSingapore", "text/plain");
    }

    @Test
    void dryRunReportsTheSameDiffButWritesNothing() throws Exception {
        stored.put("label", "Old");
        WriteOp op = update(ModelSpec.OFFER_CTA);
        op.getValues().put("label", "New");
        op.setTitle("New");

        ReportRow row = new FragmentWriter(true, true).write(resolver, op);

        assertEquals(Outcome.UPDATED, row.getOutcome());
        assertEquals("label, (title)", row.getFieldsChanged());
        assertEquals("Old", stored.get("label"));
        verify(fragment, never()).createVersion(anyString(), anyString());
        verify(fragment, never()).setTitle(anyString());
        verify(resolver, never()).commit();
    }

    @Test
    void createsAFragmentNamedAfterThePathAndSetsEveryValue() throws Exception {
        Resource parent = mock(Resource.class);
        Resource modelResource = mock(Resource.class);
        FragmentTemplate template = mock(FragmentTemplate.class);
        when(resolver.getResource("/content/dam/test/offer-listing/categories")).thenReturn(parent);
        when(resolver.getResource(ModelSpec.CATEGORY.getModelPath())).thenReturn(modelResource);
        when(modelResource.adaptTo(FragmentTemplate.class)).thenReturn(template);
        when(template.createFragment(parent, "health-fitness", "Health Fitness")).thenReturn(fragment);
        WriteOp stub = new WriteOp(ModelSpec.CATEGORY, "OfferDetail", 3, "health-fitness", true);
        stub.setPath("/content/dam/test/offer-listing/categories/health-fitness");
        stub.setCreate(true);
        stub.setTitle("Health Fitness");
        stub.getValues().put("offerCategorySlug", "health-fitness");
        stub.getValues().put("ID", 8L);
        stub.getValues().put("parent", null);

        ReportRow row = new FragmentWriter(false, true).write(resolver, stub);

        assertEquals(Outcome.STUB_CREATED, row.getOutcome());
        assertEquals(8L, stored.get("ID"));
        assertFalse(stored.containsKey("parent"), "nothing to clear on a new fragment");
        verify(resolver).commit();
    }

    @Test
    void dryRunCreateTouchesNothing() {
        WriteOp op = new WriteOp(ModelSpec.OFFER_CTA, "OfferCta", 2, "go", false);
        op.setPath("/content/dam/test/offer-listing/offer-cta/go");
        op.setCreate(true);
        op.getValues().put("offerCtaSlug", "go");

        ReportRow row = new FragmentWriter(true, true).write(resolver, op);

        assertEquals(Outcome.CREATED, row.getOutcome());
        assertEquals("offerCtaSlug", row.getFieldsChanged());
        verifyNoInteractions(resolver);
    }

    @Test
    void rollsBackAndReportsAFailedWrite() throws Exception {
        WriteOp op = update(ModelSpec.OFFER_CTA);
        op.getValues().put("label", "New");
        ContentElement broken = mock(ContentElement.class);
        FragmentData data = mock(FragmentData.class);
        when(broken.getValue()).thenReturn(data);
        doThrow(new ContentFragmentException("locked")).when(broken).setValue(any());
        when(fragment.getElement("label")).thenReturn(broken);

        ReportRow row = new FragmentWriter(false, false).write(resolver, op);

        assertEquals(Outcome.ERROR, row.getOutcome());
        assertTrue(row.getMessage().contains("locked"));
        verify(resolver).revert();
        verify(resolver, never()).commit();
    }

    @Test
    void reportsPlanningErrorsWithoutTouchingTheRepository() {
        WriteOp op = update(ModelSpec.OFFER_CTA);
        op.getErrors().add("first");
        op.getErrors().add("second");

        ReportRow row = new FragmentWriter(false, true).write(resolver, op);

        assertEquals(Outcome.ERROR, row.getOutcome());
        assertEquals("first; second", row.getMessage());
        verifyNoInteractions(resolver);
    }

    @Test
    void comparesValuesAcrossStorageTypeDrift() {
        assertTrue(FragmentWriter.same(FieldType.LONG, 42.0d, 42L));
        assertTrue(FragmentWriter.same(FieldType.TEXT, new String[] {"a"}, "a"));
        assertTrue(FragmentWriter.same(FieldType.FRAGMENTS, new String[0], new String[0]));
        assertTrue(FragmentWriter.same(FieldType.FRAGMENT, null, null));
        assertTrue(FragmentWriter.same(FieldType.FRAGMENTS, null, new String[0]));
        assertTrue(FragmentWriter.same(FieldType.BOOLEAN, "true", Boolean.TRUE));
        assertTrue(FragmentWriter.same(FieldType.DATETIME, new GregorianCalendar(2026, 3, 1), new GregorianCalendar(2026, 3, 1)));
        assertFalse(FragmentWriter.same(FieldType.FRAGMENTS, new String[] {"a", "b"}, new String[] {"b", "a"}), "order matters");
        assertFalse(FragmentWriter.same(FieldType.TEXT, "a", null));
        assertFalse(FragmentWriter.same(FieldType.DOUBLE, 1.0d, 1.5d));
    }

    private WriteOp update(ModelSpec model) {
        WriteOp op = new WriteOp(model, model.getSheetName(), 2, "visa", false);
        op.setPath(PATH);
        return op;
    }

    private ContentElement element(String name) throws ContentFragmentException {
        if (elements.containsKey(name)) {
            return elements.get(name);
        }
        ContentElement element = mock(ContentElement.class);
        elements.put(name, element);
        FragmentData data = mock(FragmentData.class);
        when(data.getValue()).thenAnswer(i -> stored.get(name));
        doAnswer(i -> stored.put(name, i.getArgument(0))).when(data).setValue(any());
        when(element.getValue()).thenReturn(data);
        when(element.getContent()).thenAnswer(i -> (String) stored.get(name));
        doAnswer(i -> stored.put(name, i.getArgument(0))).when(element).setContent(anyString(), eq("text/plain"));
        return element;
    }
}
