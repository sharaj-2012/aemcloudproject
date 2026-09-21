package com.aemcloudproject.core.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.model.WorkflowModel;
import com.day.cq.wcm.api.Page;

/**
 * Backing model for the SCB Start Workflow dialogs rendered in the Sites page editor and the
 * Experience Fragment editor (scbstartworkflow.html).
 *
 * <p>One dialog is rendered per workflow model listed in the {@code models} property of the
 * component node, each preselecting only that model. The payload is the page currently open in
 * the editor, resolved from the request suffix exactly as the OOTB dialog
 * ({@code /libs/cq/gui/components/authoring/workflow/startworkflow}) does.</p>
 */
@Model(adaptables = SlingHttpServletRequest.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class ScbStartWorkflowModel {

    private static final Logger LOG = LoggerFactory.getLogger(ScbStartWorkflowModel.class);

    private static final String WORKFLOW_INSTANCES_PATH = "/var/workflow/instances";

    /** Workflow models to render a dialog for, configured on the component node. */
    @ValueMapValue
    private String[] models;

    @SlingObject
    private SlingHttpServletRequest request;

    @SlingObject
    private ResourceResolver resourceResolver;

    private String payloadPath;

    private final List<WorkflowModelItem> workflowModels = new ArrayList<>();

    @PostConstruct
    protected void init() {
        Page targetPage = Optional.ofNullable(request.getRequestPathInfo().getSuffix())
                .map(resourceResolver::resolve)
                .map(resource -> resource.adaptTo(Page.class))
                .orElse(null);

        WorkflowSession workflowSession = resourceResolver.adaptTo(WorkflowSession.class);

        if (targetPage == null || workflowSession == null || models == null) {
            return;
        }

        payloadPath = targetPage.getPath();

        for (String modelId : models) {
            resolveModel(workflowSession, modelId)
                    .ifPresent(model -> workflowModels.add(
                            new WorkflowModelItem(model.getId(),
                                    StringUtils.defaultIfEmpty(model.getTitle(), model.getId()))));
        }
    }

    private Optional<WorkflowModel> resolveModel(WorkflowSession workflowSession, String modelId) {
        try {
            // Returns empty when the model is missing or the current user cannot read it, so no
            // dialog is rendered for it and the action cannot start it.
            return Optional.ofNullable(workflowSession.getModel(modelId));
        } catch (WorkflowException e) {
            LOG.warn("SCB Start Workflow: cannot read workflow model {}", modelId, e);
            return Optional.empty();
        }
    }

    /** {@code true} when at least one dialog can be rendered for the current page. */
    public boolean isReady() {
        return payloadPath != null && !workflowModels.isEmpty();
    }

    public String getPayloadPath() {
        return payloadPath;
    }

    /** Form action of the dialogs, context path aware like the OOTB dialog. */
    public String getFormAction() {
        return request.getContextPath() + WORKFLOW_INSTANCES_PATH;
    }

    public List<WorkflowModelItem> getWorkflowModels() {
        return Collections.unmodifiableList(workflowModels);
    }

    /** A single workflow model offered by a dialog. */
    public static final class WorkflowModelItem {

        private final String id;
        private final String title;

        WorkflowModelItem(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }
    }
}
