package com.aemcloudproject.core.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link ScbOfferListingModel}.
 */
class ScbOfferListingModelTest {

    @Test
    void scbFromSheetFindsEveryModelByItsSheetName() {
        for (ScbOfferListingModel model : ScbOfferListingModel.values()) {
            assertSame(model, ScbOfferListingModel.scbFromSheet(model.getModel()));
        }
    }

    @Test
    void scbFromSheetReturnsNullForUnknownOrMiscasedSheets() {
        assertNull(ScbOfferListingModel.scbFromSheet("unknown"));
        assertNull(ScbOfferListingModel.scbFromSheet("Offer-Detail"));
        assertNull(ScbOfferListingModel.scbFromSheet(null));
    }

    @Test
    void sheetOrderIsTheEnumOrder() {
        assertIterableEquals(Arrays.asList("merchant-venue", "merchant-details", "category", "offer-card",
                "offer-cta", "offer-detail"),
                Arrays.stream(ScbOfferListingModel.values()).map(ScbOfferListingModel::getModel)
                        .collect(Collectors.toList()));
    }

    @Test
    void offerDetailHasItsFolderPathsColumnsAndSortField() {
        ScbOfferListingModel model = ScbOfferListingModel.OFFER_DETAIL;
        assertEquals("/conf/aemcloudproject/settings/dam/cfm/models/offer-detail", model.getModelPath());
        assertEquals("offers", model.getFolder());
        assertEquals("offerSlug", model.getSlugColumn());
        assertEquals("offerTitle", model.getTitleField());
        assertEquals("offerID", model.getIdField());
        assertEquals(100001, model.getStartId());
        assertEquals("offerEndDate", model.getSortField());
        assertIterableEquals(Arrays.asList("offerID", "offerTitle", "offerImage", "alternateText"),
                model.getRequiredFields());
    }

    @Test
    void modelsWithoutAnIdHaveNoIdFieldAndStartAtZero() {
        for (ScbOfferListingModel model : new ScbOfferListingModel[] {
            ScbOfferListingModel.MERCHANT_VENUE, ScbOfferListingModel.OFFER_CTA}) {
            assertNull(model.getIdField());
            assertEquals(0, model.getStartId());
            assertNull(model.getSortField());
            assertTrue(model.getRequiredFields().isEmpty());
        }
    }

    @Test
    void offerCardRequiresItsId() {
        assertIterableEquals(Arrays.asList("ID"), ScbOfferListingModel.OFFER_CARD.getRequiredFields());
        assertEquals("offerCardSlug", ScbOfferListingModel.OFFER_CARD.getSlugColumn());
        assertEquals("cards", ScbOfferListingModel.OFFER_CARD.getFolder());
    }

    @Test
    void merchantVenuesLiveUnderTheMerchantsFolder() {
        assertEquals("merchants/venues", ScbOfferListingModel.MERCHANT_VENUE.getFolder());
        assertEquals("merchantVenueSlug", ScbOfferListingModel.MERCHANT_VENUE.getSlugColumn());
    }

    @Test
    void slugColumnsAreUniqueAndNeverAPlainSlugField() {
        Set<String> columns = new HashSet<>();
        for (ScbOfferListingModel model : ScbOfferListingModel.values()) {
            assertTrue(columns.add(model.getSlugColumn()), model.getSlugColumn());
            assertTrue(model.getSlugColumn().endsWith("Slug"));
        }
    }

    @Test
    void requiredFieldsCannotBeChanged() {
        Set<String> required = ScbOfferListingModel.CATEGORY.getRequiredFields();
        assertThrows(UnsupportedOperationException.class, () -> required.add("name"));
    }
}
