<%--
    SCB Start Workflow dialogs.

    Mirrors the OOTB page editor dialog (/libs/cq/gui/components/authoring/workflow/startworkflow),
    but renders one dialog per model listed in the `models` property, each with only that model
    preselected. The SCB Workflows header bar actions open the dialog whose
    data-scb-workflow-model matches their data-workflow-model (clientlib-authoring-workflow).

    Mounted next to the native startworkflowmodal rather than overlaying it, so the native
    Start Workflow action keeps its stock behaviour.
--%><%
%><%@include file="/libs/granite/ui/global.jsp" %><%
%><%@page session="false"
          import="com.adobe.granite.workflow.WorkflowException,
                  com.adobe.granite.workflow.WorkflowSession,
                  com.adobe.granite.workflow.model.WorkflowModel,
                  com.day.cq.wcm.api.Page,
                  org.apache.sling.api.resource.Resource" %><%

    // Same payload resolution as the OOTB dialog: the page path is the editor.html suffix.
    Page targetPage = null;
    String pagePath = slingRequest.getRequestPathInfo().getSuffix();
    if (pagePath != null) {
        Resource pageResource = resourceResolver.resolve(pagePath);
        targetPage = pageResource.adaptTo(Page.class);
    }

    WorkflowSession wfSession = resourceResolver.adaptTo(WorkflowSession.class);
    String[] models = resource.getValueMap().get("models", new String[0]);

    if (targetPage == null || wfSession == null) {
        return;
    }

    for (String modelId : models) {
        WorkflowModel model;
        try {
            model = wfSession.getModel(modelId);
        } catch (WorkflowException e) {
            log.warn("SCB Start Workflow: cannot read workflow model {}", modelId, e);
            continue;
        }
        if (model == null) {
            // Missing model, or the user cannot read it: no dialog, so the action cannot start it.
            continue;
        }
        String modelTitle = i18n.getVar(model.getTitle() != null ? model.getTitle() : modelId);
%>
<coral-dialog class="scb-WorkflowStart" closable="on" data-scb-workflow-model="<%= xssAPI.encodeForHTMLAttr(model.getId()) %>">
    <form action="<%= request.getContextPath() %>/var/workflow/instances" method="post" class="coral-Form coral-Form--vertical">
        <coral-dialog-header>
            <%= xssAPI.encodeForHTML(i18n.get("SCB Start Workflow")) %>
        </coral-dialog-header>
        <coral-dialog-content>
            <input type="hidden" name="_charset_" value="utf-8">
            <input type="hidden" name=":status" value="browser">
            <input type="hidden" name="payloadType" value="JCR_PATH">
            <input type="hidden" name="payload" value="<%= xssAPI.encodeForHTMLAttr(targetPage.getPath()) %>">
            <coral-select name="model" class="scb-WorkflowStart-select coral-Form-field" placeholder="<%= xssAPI.encodeForHTMLAttr(i18n.get("Select a Workflow Model")) %>">
                <coral-select-item value="<%= xssAPI.encodeForHTMLAttr(model.getId()) %>" selected><%= xssAPI.encodeForHTML(modelTitle) %></coral-select-item>
            </coral-select>
            <input is="coral-textfield" type="text" name="workflowTitle" class="scb-WorkflowStart-title coral-Form-field" placeholder="<%= xssAPI.encodeForHTMLAttr(i18n.get("Enter title of workflow")) %>">
        </coral-dialog-content>
        <coral-dialog-footer>
            <button is="coral-button" type="reset" class="scb-WorkflowStart-reset" coral-close><%= xssAPI.encodeForHTML(i18n.get("Close")) %></button>
            <button is="coral-button" type="button" variant="primary" class="scb-WorkflowStart-submit"><%= xssAPI.encodeForHTML(i18n.get("Start Workflow")) %></button>
        </coral-dialog-footer>
    </form>
</coral-dialog><%
    }
%>
