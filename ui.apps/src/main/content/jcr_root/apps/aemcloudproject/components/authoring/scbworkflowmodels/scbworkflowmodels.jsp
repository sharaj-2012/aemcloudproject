<%--
    SCB workflow model datasource.

    Same item shape as the OOTB models datasource
    (/libs/cq/gui/components/coral/common/admin/timeline/events/workflow/datasources/models):
    value = model id, text = model title, granite:data multiResourceSupport. The OOTB Assets
    workflow script reads that flag to decide whether folders and collections are expanded
    into one payload per asset.

    Unlike the OOTB datasource it returns only the model named by the `model` request
    parameter, preselected, and only if that model is listed in the `models` property of the datasource node.
    Optional `expandFolders` (Boolean) on the datasource node: see below.
--%><%
%><%@include file="/libs/granite/ui/global.jsp" %><%
%><%@page session="false"
          import="java.util.Arrays,
                  java.util.Collections,
                  java.util.HashMap,
                  com.adobe.granite.ui.components.Config,
                  com.adobe.granite.ui.components.ds.DataSource,
                  com.adobe.granite.ui.components.ds.EmptyDataSource,
                  com.adobe.granite.ui.components.ds.SimpleDataSource,
                  com.adobe.granite.ui.components.ds.ValueMapResource,
                  com.adobe.granite.workflow.WorkflowException,
                  com.adobe.granite.workflow.WorkflowSession,
                  com.adobe.granite.workflow.model.WorkflowModel,
                  org.apache.sling.api.resource.Resource,
                  org.apache.sling.api.resource.ValueMap,
                  org.apache.sling.api.wrappers.ValueMapDecorator" %><%

    request.setAttribute(DataSource.class.getName(), EmptyDataSource.instance());

    String modelId = request.getParameter("model");
    // In a datasource script `resource` is the field being rendered (the select), so the
    // allowlist is read from its datasource child, where it is configured.
    Resource datasource = resource.getChild(Config.DATASOURCE);
    String[] allowedModels = datasource != null ?
            datasource.getValueMap().get("models", new String[0]) : new String[0];
    WorkflowSession wfSession = resourceResolver.adaptTo(WorkflowSession.class);

    if (modelId == null || wfSession == null || !Arrays.asList(allowedModels).contains(modelId)) {
        return;
    }

    WorkflowModel model;
    try {
        model = wfSession.getModel(modelId);
    } catch (WorkflowException e) {
        log.warn("SCB workflow models: cannot read workflow model {}", modelId, e);
        return;
    }
    if (model == null) {
        return;
    }

    ValueMap dataVM = new ValueMapDecorator(new HashMap<String, Object>());
    // expandFolders=true reports the model as single-resource, so the OOTB script expands selected
    // folders/collections into their assets: it then confirms "... on N asset(s)" and enforces the
    // multiple-workflow limit, the same as for OOTB single-resource models.
    boolean expandFolders = datasource.getValueMap().get("expandFolders", false);
    boolean multiResourceSupport = "true".equals(model.getMetaDataMap().get("multiResourceSupport", "false"));
    dataVM.put("multiResourceSupport", multiResourceSupport && !expandFolders);
    Resource data = new ValueMapResource(resourceResolver, model.getId() + "/granite:data", "nt:unstructured", dataVM);

    ValueMap vm = new ValueMapDecorator(new HashMap<String, Object>());
    vm.put("value", model.getId());
    vm.put("text", model.getTitle() != null ? model.getTitle() : model.getId());
    vm.put("selected", true);

    Resource item = new ValueMapResource(resourceResolver, model.getId(), "nt:unstructured", vm,
            Collections.singletonList(data));

    request.setAttribute(DataSource.class.getName(),
            new SimpleDataSource(Collections.singletonList(item).iterator()));
%>
