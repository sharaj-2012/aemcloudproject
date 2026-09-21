package com.aemcloudproject.core.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.model.WorkflowModel;
import com.aemcloudproject.core.testcontext.AppAemContext;
import com.day.cq.wcm.api.Page;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;

/**
 * Tests for {@link ScbStartWorkflowModel}, which backs the SCB Start Workflow dialogs in the
 * Sites page editor and the Experience Fragment editor.
 */
@ExtendWith(AemContextExtension.class)
class ScbStartWorkflowModelTest {

    private static final String ACTIVATION = "/var/workflow/models/request_for_activation";
    private static final String DEACTIVATION = "/var/workflow/models/request_for_deactivation";
    private static final String PAGE_PATH = "/content/mypage";
    private static final String COMPONENT_PATH = "/apps/scbstartworkflowmodal";

    private final AemContext context = AppAemContext.newAemContext();

    private WorkflowSession workflowSession;

    @BeforeEach
    void setUp() {
        context.addModelsForClasses(ScbStartWorkflowModel.class);

        Page page = context.create().page(PAGE_PATH);
        assertNotNull(page);

        workflowSession = mock(WorkflowSession.class);
        context.registerAdapter(ResourceResolver.class, WorkflowSession.class, workflowSession);
    }

    @Test
    void rendersOneItemPerConfiguredModel() throws WorkflowException {
        mockModel(ACTIVATION, "Request for Activation");
        mockModel(DEACTIVATION, "Request for Deactivation");

        ScbStartWorkflowModel model = adaptRequest(ACTIVATION, DEACTIVATION);

        assertTrue(model.isReady());
        assertEquals(PAGE_PATH, model.getPayloadPath());

        List<ScbStartWorkflowModel.WorkflowModelItem> items = model.getWorkflowModels();
        assertEquals(2, items.size());
        assertEquals(ACTIVATION, items.get(0).getId());
        assertEquals("Request for Activation", items.get(0).getTitle());
        assertEquals(DEACTIVATION, items.get(1).getId());
        assertEquals("Request for Deactivation", items.get(1).getTitle());
    }

    @Test
    void postsToTheWorkflowInstancesEndpoint() throws WorkflowException {
        mockModel(ACTIVATION, "Request for Activation");

        ScbStartWorkflowModel model = adaptRequest(ACTIVATION);

        assertTrue(model.getFormAction().endsWith("/var/workflow/instances"));
    }

    @Test
    void skipsModelsTheUserCannotRead() throws WorkflowException {
        mockModel(ACTIVATION, "Request for Activation");
        // A model that is missing, or that the current user cannot read, resolves to null.
        when(workflowSession.getModel(DEACTIVATION)).thenReturn(null);

        ScbStartWorkflowModel model = adaptRequest(ACTIVATION, DEACTIVATION);

        assertEquals(1, model.getWorkflowModels().size());
        assertEquals(ACTIVATION, model.getWorkflowModels().get(0).getId());
    }

    @Test
    void skipsModelsThatFailToResolve() throws WorkflowException {
        when(workflowSession.getModel(anyString())).thenThrow(new WorkflowException("boom"));

        ScbStartWorkflowModel model = adaptRequest(ACTIVATION);

        assertFalse(model.isReady());
        assertTrue(model.getWorkflowModels().isEmpty());
    }

    @Test
    void fallsBackToTheModelIdWhenTheTitleIsBlank() throws WorkflowException {
        mockModel(ACTIVATION, "");

        ScbStartWorkflowModel model = adaptRequest(ACTIVATION);

        assertEquals(ACTIVATION, model.getWorkflowModels().get(0).getTitle());
    }

    @Test
    void isNotReadyWithoutAPageInTheSuffix() throws WorkflowException {
        mockModel(ACTIVATION, "Request for Activation");

        Resource component = context.create().resource(COMPONENT_PATH, "models", new String[] { ACTIVATION });
        context.request().setResource(component);
        // No suffix: the editor has no page open, so there is nothing to start a workflow on.
        context.requestPathInfo().setSuffix(null);

        ScbStartWorkflowModel model = context.request().adaptTo(ScbStartWorkflowModel.class);

        assertNotNull(model);
        assertFalse(model.isReady());
        assertTrue(model.getWorkflowModels().isEmpty());
    }

    @Test
    void isNotReadyWithoutConfiguredModels() {
        Resource component = context.create().resource(COMPONENT_PATH);
        context.request().setResource(component);
        context.requestPathInfo().setSuffix(PAGE_PATH);

        ScbStartWorkflowModel model = context.request().adaptTo(ScbStartWorkflowModel.class);

        assertNotNull(model);
        assertFalse(model.isReady());
    }

    private void mockModel(String id, String title) throws WorkflowException {
        WorkflowModel workflowModel = mock(WorkflowModel.class);
        when(workflowModel.getId()).thenReturn(id);
        when(workflowModel.getTitle()).thenReturn(title);
        when(workflowSession.getModel(id)).thenReturn(workflowModel);
    }

    private ScbStartWorkflowModel adaptRequest(String... models) {
        Resource component = context.create().resource(COMPONENT_PATH, "models", models);
        context.request().setResource(component);
        context.requestPathInfo().setSuffix(PAGE_PATH);

        ScbStartWorkflowModel model = context.request().adaptTo(ScbStartWorkflowModel.class);
        assertNotNull(model);
        return model;
    }
}
