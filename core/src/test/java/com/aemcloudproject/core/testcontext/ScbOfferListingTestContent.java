package com.aemcloudproject.core.testcontext;

import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.cq.dam.cfm.ContentFragmentException;
import com.adobe.cq.dam.cfm.DataType;
import com.adobe.cq.dam.cfm.ElementTemplate;
import com.adobe.cq.dam.cfm.FragmentData;
import com.adobe.cq.dam.cfm.FragmentTemplate;
import com.aemcloudproject.core.config.ScbOfferListingModel;
import com.day.cq.replication.ReplicationStatus;
import io.wcm.testing.mock.aem.junit5.AemContext;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Offer-listing test content for {@link AemContext}: the six models, the offer-listing folder with its
 * model folders, and fragments. The Content Fragment API is adapted from the mock repository, so every
 * value a test reads or the code writes lives in {@code jcr:content/data/master} of the fragment.
 */
public final class ScbOfferListingTestContent {

    /** The picked offer-listing folder used by the tests. */
    public static final String ROOT = "/content/dam/test/offer-listing";

    private static final String MASTER = "jcr:content/data/master";
    private static final String FOLDER_TYPE = "sling:OrderedFolder";
    private static final String STRING = "string";
    private static final Map<ScbOfferListingModel, Map<String, String>> FIELDS = new HashMap<>();

    static {
        FIELDS.put(ScbOfferListingModel.MERCHANT_VENUE, scbFields("name", STRING, "address", STRING));
        FIELDS.put(ScbOfferListingModel.MERCHANT_DETAILS, scbFields("merchantName", STRING, "merchantID", STRING,
                "merchantVenues", "string[]"));
        FIELDS.put(ScbOfferListingModel.CATEGORY, scbFields("name", STRING, "ID", STRING, "slug", STRING));
        FIELDS.put(ScbOfferListingModel.OFFER_CARD, scbFields("name", STRING, "ID", STRING, "slug", STRING));
        FIELDS.put(ScbOfferListingModel.OFFER_CTA, scbFields("label", STRING, "link", STRING));
        FIELDS.put(ScbOfferListingModel.OFFER_DETAIL, scbFields("offerTitle", STRING, "offerID", STRING,
                "offerEndDate", "calendar", "featured", "boolean", "priority", "long", "score", "double",
                "offerCards", "string[]", "offerCta", STRING, "offerImage", STRING, "alternateText", STRING));
    }

    private ScbOfferListingTestContent() {
    }

    /**
     * Creates the models, the offer-listing folder and its model folders, registers the Content Fragment,
     * model and replication adapters, and commits.
     *
     * @param context the AEM context
     * @throws PersistenceException if the commit fails
     */
    public static void scbSetUp(AemContext context) throws PersistenceException {
        for (ScbOfferListingModel model : ScbOfferListingModel.values()) {
            context.create().resource(model.getModelPath(), "jcr:primaryType", "cq:Template");
        }
        scbFolder(context, ROOT);
        for (ScbOfferListingModel model : ScbOfferListingModel.values()) {
            String path = ROOT;
            for (String part : model.getFolder().split("/")) {
                path += "/" + part;
                if (context.resourceResolver().getResource(path) == null) {
                    scbFolder(context, path);
                }
            }
        }
        context.registerAdapter(Resource.class, ContentFragment.class,
                (Function<Resource, ContentFragment>) ScbOfferListingTestContent::scbAdaptFragment);
        context.registerAdapter(Resource.class, FragmentTemplate.class,
                (Function<Resource, FragmentTemplate>) ScbOfferListingTestContent::scbAdaptTemplate);
        context.registerAdapter(Resource.class, ReplicationStatus.class,
                (Function<Resource, ReplicationStatus>) ScbOfferListingTestContent::scbAdaptReplication);
        context.resourceResolver().commit();
    }

    /**
     * Creates a folder.
     *
     * @param context the AEM context
     * @param path    the folder path
     * @return the folder
     */
    public static Resource scbFolder(AemContext context, String path) {
        return context.create().resource(path, "jcr:primaryType", FOLDER_TYPE);
    }

    /**
     * Creates a fragment of a model in its model folder and commits.
     *
     * @param context the AEM context
     * @param model   the model
     * @param name    the fragment name
     * @param fields  field names and values, alternating
     * @return the fragment's path
     * @throws PersistenceException if the commit fails
     */
    public static String scbFragment(AemContext context, ScbOfferListingModel model, String name, Object... fields)
            throws PersistenceException {
        String path = ROOT + "/" + model.getFolder() + "/" + name;
        scbCreateFragment(context.resourceResolver(), path, model.getModelPath(), name);
        ModifiableValueMap master = context.resourceResolver().getResource(path + "/" + MASTER)
                .adaptTo(ModifiableValueMap.class);
        for (int i = 0; i < fields.length; i += 2) {
            master.put((String) fields[i], fields[i + 1]);
        }
        context.resourceResolver().commit();
        return path;
    }

    /**
     * Marks a fragment as published and commits.
     *
     * @param context the AEM context
     * @param path    the fragment path
     * @throws PersistenceException if the commit fails
     */
    public static void scbPublish(AemContext context, String path) throws PersistenceException {
        context.resourceResolver().getResource(path + "/jcr:content").adaptTo(ModifiableValueMap.class)
                .put("cq:lastReplicationAction", "Activate");
        context.resourceResolver().commit();
    }

    /**
     * Returns the stored field values of a fragment.
     *
     * @param context the AEM context
     * @param path    the fragment path
     * @return the values, or {@code null} when the fragment does not exist
     */
    public static ValueMap scbMaster(AemContext context, String path) {
        Resource master = context.resourceResolver().getResource(path + "/" + MASTER);
        return master == null ? null : master.getValueMap();
    }

    private static Map<String, String> scbFields(String... namesAndTypes) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < namesAndTypes.length; i += 2) {
            fields.put(namesAndTypes[i], namesAndTypes[i + 1]);
        }
        return fields;
    }

    private static void scbCreateFragment(ResourceResolver resolver, String path, String modelPath, String title)
            throws PersistenceException {
        int slash = path.lastIndexOf('/');
        Resource parent = resolver.getResource(path.substring(0, slash));
        Resource asset = resolver.create(parent, path.substring(slash + 1),
                Collections.singletonMap("jcr:primaryType", "dam:Asset"));
        Map<String, Object> contentProps = new HashMap<>();
        contentProps.put("jcr:primaryType", "dam:AssetContent");
        contentProps.put("jcr:title", title);
        Resource content = resolver.create(asset, "jcr:content", contentProps);
        Resource data = resolver.create(content, "data", Collections.singletonMap("cq:model", modelPath));
        resolver.create(data, "master", Collections.emptyMap());
    }

    private static ContentFragment scbAdaptFragment(Resource asset) {
        if (asset.getChild(MASTER) == null) {
            return null;
        }
        ResourceResolver resolver = asset.getResourceResolver();
        String path = asset.getPath();
        ContentFragment fragment = mock(ContentFragment.class);
        when(fragment.getName()).thenReturn(asset.getName());
        when(fragment.getElement(anyString()))
                .thenAnswer(call -> scbElement(resolver, path, call.getArgument(0)));
        try {
            doAnswer(call -> {
                resolver.getResource(path + "/jcr:content").adaptTo(ModifiableValueMap.class)
                        .put("jcr:title", call.getArgument(0));
                return null;
            }).when(fragment).setTitle(anyString());
        } catch (ContentFragmentException e) {
            throw new IllegalStateException(e);
        }
        return fragment;
    }

    private static ContentElement scbElement(ResourceResolver resolver, String path, String name)
            throws ContentFragmentException {
        ContentElement element = mock(ContentElement.class);
        when(element.getName()).thenReturn(name);
        when(element.getValue()).thenAnswer(call -> scbData(
                resolver.getResource(path + "/" + MASTER).getValueMap().get(name)));
        doAnswer(call -> scbStore(resolver, path, name, ((FragmentData) call.getArgument(0)).getValue()))
                .when(element).setValue(any());
        doAnswer(call -> scbStore(resolver, path, name, call.getArgument(0)))
                .when(element).setContent(any(), any());
        return element;
    }

    private static FragmentData scbData(Object value) throws ContentFragmentException {
        Object[] holder = {value};
        FragmentData data = mock(FragmentData.class);
        when(data.getValue()).thenAnswer(call -> holder[0]);
        doAnswer(call -> {
            holder[0] = call.getArgument(0);
            return null;
        }).when(data).setValue(any());
        return data;
    }

    private static Object scbStore(ResourceResolver resolver, String path, String name, Object value) {
        ModifiableValueMap master = resolver.getResource(path + "/" + MASTER).adaptTo(ModifiableValueMap.class);
        if (value == null) {
            master.remove(name);
        } else {
            master.put(name, value);
        }
        return null;
    }

    private static FragmentTemplate scbAdaptTemplate(Resource resource) {
        ScbOfferListingModel model = ScbOfferListingModel.scbFromSheet(resource.getName());
        if (model == null || !model.getModelPath().equals(resource.getPath())) {
            return null;
        }
        List<ElementTemplate> elements = new ArrayList<>();
        FIELDS.get(model).forEach((name, type) -> {
            DataType dataType = mock(DataType.class);
            when(dataType.getTypeString()).thenReturn(type.replace("[]", ""));
            when(dataType.isMultiValue()).thenReturn(type.endsWith("[]"));
            ElementTemplate element = mock(ElementTemplate.class);
            when(element.getName()).thenReturn(name);
            when(element.getDataType()).thenReturn(dataType);
            elements.add(element);
        });
        FragmentTemplate template = mock(FragmentTemplate.class);
        when(template.getElements()).thenAnswer(call -> elements.iterator());
        try {
            when(template.createFragment(any(Resource.class), anyString(), anyString())).thenAnswer(call -> {
                Resource folder = call.getArgument(0);
                String path = folder.getPath() + "/" + call.getArgument(1);
                scbCreateFragment(folder.getResourceResolver(), path, model.getModelPath(), call.getArgument(2));
                return folder.getResourceResolver().getResource(path).adaptTo(ContentFragment.class);
            });
        } catch (ContentFragmentException e) {
            throw new IllegalStateException(e);
        }
        return template;
    }

    private static ReplicationStatus scbAdaptReplication(Resource resource) {
        Resource content = resource.getChild("jcr:content");
        if (content == null || !"Activate".equals(content.getValueMap().get("cq:lastReplicationAction"))) {
            return null;
        }
        ReplicationStatus status = mock(ReplicationStatus.class);
        when(status.isActivated()).thenReturn(true);
        return status;
    }
}
