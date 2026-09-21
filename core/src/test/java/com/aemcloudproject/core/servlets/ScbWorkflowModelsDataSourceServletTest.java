package com.aemcloudproject.core.servlets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.adobe.granite.ui.components.ds.DataSource;
import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.adobe.granite.workflow.model.WorkflowModel;
import com.aemcloudproject.core.testcontext.AppAemContext;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;

/**
 * Tests for {@link ScbWorkflowModelsDataSourceServlet}, the datasource behind the SCB workflow
 * model selects in the Assets dialog and the Sites/XF bulk workflow wizard.
 */
@ExtendWith(AemContextExtension.class)
class ScbWorkflowModelsDataSourceServletTest {

    private static final String ACTIVATION = "/var/workflow/models/request_for_activation";
    private static final String NOT_ALLOWED = "/var/workflow/models/dam/update_asset";
    private static final String FIELD_PATH = "/apps/dialog/items/model";
    private static final String MULTI_RESOURCE_SUPPORT = "multiResourceSupport";

    private final AemContext context = AppAemContext.newAemContext();

    private ScbWorkflowModelsDataSourceServlet servlet;
    private WorkflowSession workflowSession;

    @BeforeEach
    void setUp() {
        servlet = new ScbWorkflowModelsDataSourceServlet();
        workflowSession = mock(WorkflowSession.class);
        context.registerAdapter(ResourceResolver.class, WorkflowSession.class, workflowSession);
    }

    @Test
    void returnsOnlyTheRequestedModelPreselected() throws WorkflowException {
        mockModel(ACTIVATION, "Request for Activation", true);
        prepareRequest(ACTIVATION, allowing(ACTIVATION));

        servlet.doGet(context.request(), context.response());

        ValueMap properties = singleItem().getValueMap();
        assertEquals(ACTIVATION, properties.get("value", String.class));
        assertEquals("Request for Activation", properties.get("text", String.class));
        assertTrue(properties.get("selected", false));
    }

    @Test
    void reportsTheModelsOwnMultiResourceSupport() throws WorkflowException {
        mockModel(ACTIVATION, "Request for Activation", true);
        prepareRequest(ACTIVATION, allowing(ACTIVATION));

        servlet.doGet(context.request(), context.response());

        assertTrue(graniteData(singleItem()).get(MULTI_RESOURCE_SUPPORT, false));
    }

    @Test
    void reportsSingleResourceWhenFoldersShouldBeExpanded() throws WorkflowException {
        mockModel(ACTIVATION, "Request for Activation", true);

        Map<String, Object> datasourceProperties = allowing(ACTIVATION);
        // Assets dialog: expanding folders into assets is what produces the OOTB confirmation
        // and multiple-workflow-limit prompts.
        datasourceProperties.put("expandFolders", true);
        prepareRequest(ACTIVATION, datasourceProperties);

        servlet.doGet(context.request(), context.response());

        assertFalse(graniteData(singleItem()).get(MULTI_RESOURCE_SUPPORT, false));
    }

    @Test
    void ignoresModelsThatAreNotAllowlisted() throws WorkflowException {
        mockModel(NOT_ALLOWED, "Update Asset", false);
        prepareRequest(NOT_ALLOWED, allowing(ACTIVATION));

        servlet.doGet(context.request(), context.response());

        assertNoItems();
    }

    @Test
    void ignoresARequestWithoutAModelParameter() {
        prepareRequest(null, allowing(ACTIVATION));

        servlet.doGet(context.request(), context.response());

        assertNoItems();
    }

    @Test
    void ignoresAFieldWithoutADatasourceNode() {
        Resource field = context.create().resource(FIELD_PATH);
        context.request().setResource(field);
        context.request().setParameterMap(Collections.singletonMap("model", ACTIVATION));

        servlet.doGet(context.request(), context.response());

        assertNoItems();
    }

    @Test
    void ignoresModelsThatFailToResolve() throws WorkflowException {
        when(workflowSession.getModel(anyString())).thenThrow(new WorkflowException("boom"));
        prepareRequest(ACTIVATION, allowing(ACTIVATION));

        servlet.doGet(context.request(), context.response());

        assertNoItems();
    }

    @Test
    void ignoresModelsTheUserCannotRead() throws WorkflowException {
        when(workflowSession.getModel(eq(ACTIVATION))).thenReturn(null);
        prepareRequest(ACTIVATION, allowing(ACTIVATION));

        servlet.doGet(context.request(), context.response());

        assertNoItems();
    }

    private Map<String, Object> allowing(String... models) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("models", models);
        return properties;
    }

    private void prepareRequest(String modelParameter, Map<String, Object> datasourceProperties) {
        Resource field = context.create().resource(FIELD_PATH);
        context.create().resource(FIELD_PATH + "/datasource", datasourceProperties);

        // Granite dispatches the datasource on the field resource, with the datasource's
        // resource type forced, so the servlet sees the field and reads its datasource child.
        context.request().setResource(field);
        if (modelParameter != null) {
            context.request().setParameterMap(Collections.singletonMap("model", modelParameter));
        }
    }

    private void mockModel(String id, String title, boolean multiResourceSupport) throws WorkflowException {
        MetaDataMap metaDataMap = mock(MetaDataMap.class);
        when(metaDataMap.get(MULTI_RESOURCE_SUPPORT, "false"))
                .thenReturn(Boolean.toString(multiResourceSupport));

        WorkflowModel workflowModel = mock(WorkflowModel.class);
        when(workflowModel.getId()).thenReturn(id);
        when(workflowModel.getTitle()).thenReturn(title);
        when(workflowModel.getMetaDataMap()).thenReturn(metaDataMap);

        when(workflowSession.getModel(id)).thenReturn(workflowModel);
    }

    private DataSource dataSource() {
        Object attribute = context.request().getAttribute(DataSource.class.getName());
        assertNotNull(attribute, "the servlet must always provide a data source");
        return (DataSource) attribute;
    }

    private Resource singleItem() {
        Iterator<Resource> items = dataSource().iterator();
        assertTrue(items.hasNext(), "expected exactly one item");
        Resource item = items.next();
        assertFalse(items.hasNext(), "expected exactly one item");
        return item;
    }

    private void assertNoItems() {
        assertFalse(dataSource().iterator().hasNext(), "expected an empty data source");
    }

    private ValueMap graniteData(Resource item) {
        Iterator<Resource> children = item.listChildren();
        assertTrue(children.hasNext(), "expected a granite:data child");
        return children.next().getValueMap();
    }
}
