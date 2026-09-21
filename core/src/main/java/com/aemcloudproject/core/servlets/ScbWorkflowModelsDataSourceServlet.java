package com.aemcloudproject.core.servlets;

import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;

import javax.servlet.Servlet;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.ui.components.Config;
import com.adobe.granite.ui.components.ds.DataSource;
import com.adobe.granite.ui.components.ds.EmptyDataSource;
import com.adobe.granite.ui.components.ds.SimpleDataSource;
import com.adobe.granite.ui.components.ds.ValueMapResource;
import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.model.WorkflowModel;
import com.day.cq.commons.jcr.JcrConstants;

/**
 * Granite UI datasource for the SCB workflow model selects (Assets dialog and Sites/XF bulk
 * workflow wizard).
 *
 * <p>Items have the same shape as the OOTB models datasource
 * ({@code /libs/cq/gui/components/coral/common/admin/timeline/events/workflow/datasources/models}):
 * value = model id, text = model title, {@code granite:data} multiResourceSupport. The OOTB
 * scripts read that flag to decide whether folders and collections are expanded into one payload
 * per asset, and whether "Keep workflow package" is available.</p>
 *
 * <p>Unlike the OOTB datasource it returns only the model named by the {@code model} request
 * parameter, preselected, and only when that model is listed in the {@code models} property of the
 * datasource node.</p>
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
        resourceTypes = ScbWorkflowModelsDataSourceServlet.RESOURCE_TYPE,
        methods = HttpConstants.METHOD_GET)
@ServiceDescription("SCB Workflows: single preselected workflow model datasource")
public class ScbWorkflowModelsDataSourceServlet extends SlingSafeMethodsServlet {

    public static final String RESOURCE_TYPE = "aemcloudproject/components/authoring/scbworkflowmodels";

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(ScbWorkflowModelsDataSourceServlet.class);

    private static final String PN_MODELS = "models";
    private static final String PN_EXPAND_FOLDERS = "expandFolders";
    private static final String RP_MODEL = "model";
    private static final String MULTI_RESOURCE_SUPPORT = "multiResourceSupport";

    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) {

        request.setAttribute(DataSource.class.getName(), EmptyDataSource.instance());

        String modelId = request.getParameter(RP_MODEL);
        ResourceResolver resourceResolver = request.getResourceResolver();
        WorkflowSession workflowSession = resourceResolver.adaptTo(WorkflowSession.class);

        // Granite dispatches the datasource on the field resource with the datasource's resource
        // type forced, so the configuration is read from the field's datasource child.
        Resource datasource = request.getResource().getChild(Config.DATASOURCE);

        if (StringUtils.isBlank(modelId) || workflowSession == null || datasource == null) {
            return;
        }

        ValueMap datasourceProperties = datasource.getValueMap();

        if (!isAllowed(datasourceProperties, modelId)) {
            return;
        }

        resolveModel(workflowSession, modelId).ifPresent(model ->
                request.setAttribute(DataSource.class.getName(),
                        toDataSource(resourceResolver, model, datasourceProperties)));
    }

    private boolean isAllowed(ValueMap datasourceProperties, String modelId) {
        for (String allowedModel : datasourceProperties.get(PN_MODELS, new String[0])) {
            if (StringUtils.equals(allowedModel, modelId)) {
                return true;
            }
        }
        return false;
    }

    private Optional<WorkflowModel> resolveModel(WorkflowSession workflowSession, String modelId) {
        try {
            return Optional.ofNullable(workflowSession.getModel(modelId));
        } catch (WorkflowException e) {
            LOG.warn("SCB workflow models: cannot read workflow model {}", modelId, e);
            return Optional.empty();
        }
    }

    private DataSource toDataSource(ResourceResolver resourceResolver, WorkflowModel model,
                                    ValueMap datasourceProperties) {

        // expandFolders=true reports the model as single-resource, so the OOTB script expands
        // selected folders/collections into their assets: it then confirms "... on N asset(s)" and
        // enforces the multiple-workflow limit, the same as for OOTB single-resource models.
        boolean expandFolders = datasourceProperties.get(PN_EXPAND_FOLDERS, false);
        boolean multiResourceSupport = Boolean.parseBoolean(
                model.getMetaDataMap().get(MULTI_RESOURCE_SUPPORT, "false"));

        ValueMap dataProperties = new ValueMapDecorator(new HashMap<>());
        dataProperties.put(MULTI_RESOURCE_SUPPORT, multiResourceSupport && !expandFolders);
        Resource data = new ValueMapResource(resourceResolver, model.getId() + "/granite:data",
                JcrConstants.NT_UNSTRUCTURED, dataProperties);

        ValueMap itemProperties = new ValueMapDecorator(new HashMap<>());
        itemProperties.put("value", model.getId());
        itemProperties.put("text", StringUtils.defaultIfEmpty(model.getTitle(), model.getId()));
        itemProperties.put("selected", true);

        Resource item = new ValueMapResource(resourceResolver, model.getId(),
                JcrConstants.NT_UNSTRUCTURED, itemProperties, Collections.singletonList(data));

        return new SimpleDataSource(Collections.singletonList(item).iterator());
    }
}
