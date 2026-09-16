<%--
    Activate Workflow dialog.

    Our own Start Workflow dialog, mounted alongside AEM's native startworkflowmodal
    rather than overlaying it, so the native Start Workflow action keeps its stock
    behaviour. The workflow model is fixed by the action's granite:data and filled in
    by clientlib-authoring-workflow, so no model picker is rendered here.

    Posts to the same workflow instance endpoint the native dialog uses:
        POST /var/workflow/instances
             payloadType=JCR_PATH payload=<path> model=<model> workflowTitle=<text>
--%>
<%@include file="/libs/granite/ui/global.jsp"%>
<%@page session="false"
        import="org.apache.commons.lang3.StringUtils"%>
<%
    String suffix = slingRequest.getRequestPathInfo().getSuffix();
    String payloadPath = StringUtils.defaultString(suffix);
    if (payloadPath.endsWith(".html")) {
        payloadPath = payloadPath.substring(0, payloadPath.length() - ".html".length());
    }
%>
<coral-dialog class="aemcloudproject-activate-workflow-dialog"
              closable="on"
              backdrop="modal">
    <form class="coral-Form coral-Form--vertical"
          action="/var/workflow/instances"
          method="post">
        <coral-dialog-header><%= xssAPI.encodeForHTML(i18n.get("Activate Workflow")) %></coral-dialog-header>
        <coral-dialog-content>
            <input type="hidden" name="_charset_" value="utf-8">
            <input type="hidden" name="payloadType" value="JCR_PATH">
            <input type="hidden" name="payload" value="<%= xssAPI.encodeForHTMLAttr(payloadPath) %>">
            <input type="hidden" name="model" value="">
            <div class="coral-Form-fieldwrapper">
                <label class="coral-Form-fieldlabel"><%= xssAPI.encodeForHTML(i18n.get("Workflow")) %></label>
                <input is="coral-textfield"
                       class="coral-Form-field aemcloudproject-activate-workflow-modelname aemcloudproject-activate-workflow-locked"
                       type="text"
                       readonly
                       tabindex="-1"
                       value="">
            </div>
            <div class="coral-Form-fieldwrapper">
                <label class="coral-Form-fieldlabel"><%= xssAPI.encodeForHTML(i18n.get("Title")) %></label>
                <input is="coral-textfield"
                       class="coral-Form-field aemcloudproject-activate-workflow-title"
                       type="text"
                       name="workflowTitle"
                       value=""
                       placeholder="<%= xssAPI.encodeForHTMLAttr(i18n.get("Enter title of workflow")) %>">
            </div>
        </coral-dialog-content>
        <coral-dialog-footer>
            <button is="coral-button" variant="default" type="button" coral-close><%= xssAPI.encodeForHTML(i18n.get("Cancel")) %></button>
            <button is="coral-button" variant="cta" type="submit" class="aemcloudproject-activate-workflow-submit"><%= xssAPI.encodeForHTML(i18n.get("Activate")) %></button>
        </coral-dialog-footer>
    </form>
</coral-dialog>
