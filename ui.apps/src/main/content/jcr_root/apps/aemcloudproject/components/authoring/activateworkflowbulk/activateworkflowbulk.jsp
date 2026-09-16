<%--
    Activate Workflow bulk dialog for the Sites/Assets/Experience Fragments consoles.

    Granite's own foundation.dialog action mechanism fetches this URL on click and
    injects/opens the returned markup -- the same mechanism AEM's own multi-select
    dialogs (e.g. DAM's createworkflowdialog.html) already use, so no selection-reading
    JS of our own is needed to get the picked paths here: they arrive as repeated
    `item` request parameters via the action node's src.uritemplate="...{?item*}".

    Granite's client-side URI-template expansion only accepts a pure template with no
    literal query string ahead of it (every native src.uritemplate follows this, and
    mixing one in makes the action silently unavailable) -- so the workflow model is
    not passed through the URL. It comes from this resource's own `model` property
    instead.

    One workflow instance is started per selected item (POST /var/workflow/instances,
    payloadType=JCR_PATH), not one plural request, because the configured workflow
    model is not marked multi-resource-safe in AEM's own model list
    (data-multiResourceSupport="false" on PracticeWorkflowModel). Each item gets its
    own progress row so a partial failure is visible instead of silently swallowed.
--%>
<%@include file="/libs/granite/ui/global.jsp"%>
<%@page session="false"
        import="org.apache.commons.lang3.StringUtils,
                java.util.ArrayList,
                java.util.List"%>
<%
    final int MAX_ITEMS = 15;
    String[] items = request.getParameterValues("item");
    String model = resource.getValueMap().get("model", String.class);
    List<String> validItems = new ArrayList<String>();
    if (items != null) {
        for (String item : items) {
            if (StringUtils.isNotBlank(item)) {
                validItems.add(item);
            }
        }
    }
%>
<coral-dialog class="aemcloudproject-activate-workflow-dialog aemcloudproject-activate-workflow-bulk-dialog foundation-toggleable"
              closable="on"
              backdrop="modal">
<% if (validItems.isEmpty()) { %>
    <coral-dialog-header><%= xssAPI.encodeForHTML(i18n.get("Activate Workflow")) %></coral-dialog-header>
    <coral-dialog-content>
        <p><%= xssAPI.encodeForHTML(i18n.get("No items are selected.")) %></p>
    </coral-dialog-content>
    <coral-dialog-footer>
        <button is="coral-button" variant="primary" type="button" coral-close><%= xssAPI.encodeForHTML(i18n.get("Close")) %></button>
    </coral-dialog-footer>
<% } else if (validItems.size() > MAX_ITEMS || StringUtils.isBlank(model)) { %>
    <coral-dialog-header><%= xssAPI.encodeForHTML(i18n.get("Activate Workflow")) %></coral-dialog-header>
    <coral-dialog-content>
        <p>
            <% if (validItems.size() > MAX_ITEMS) { %>
                <%= xssAPI.encodeForHTML(i18n.get("You can activate a workflow for a maximum of")) %>
                <%= MAX_ITEMS %>
                <%= xssAPI.encodeForHTML(i18n.get("items at a time.")) %>
                <%= validItems.size() %>
                <%= xssAPI.encodeForHTML(i18n.get("items are selected.")) %>
            <% } else { %>
                <%= xssAPI.encodeForHTML(i18n.get("No workflow is configured for this action.")) %>
            <% } %>
        </p>
    </coral-dialog-content>
    <coral-dialog-footer>
        <button is="coral-button" variant="primary" type="button" coral-close><%= xssAPI.encodeForHTML(i18n.get("Close")) %></button>
    </coral-dialog-footer>
<% } else { %>
    <form class="coral-Form coral-Form--vertical" data-aemcloudproject-workflow-model="<%= xssAPI.encodeForHTMLAttr(model) %>">
        <coral-dialog-header><%= xssAPI.encodeForHTML(i18n.get("Activate Workflow")) %></coral-dialog-header>
        <coral-dialog-content>
            <p class="aemcloudproject-activate-workflow-count">
                <%= validItems.size() %> <%= xssAPI.encodeForHTML(i18n.get("item(s) selected.")) %>
            </p>
            <div class="coral-Form-fieldwrapper">
                <label class="coral-Form-fieldlabel"><%= xssAPI.encodeForHTML(i18n.get("Workflow")) %></label>
                <input is="coral-textfield"
                       class="coral-Form-field aemcloudproject-activate-workflow-modelname aemcloudproject-activate-workflow-locked"
                       type="text"
                       readonly
                       tabindex="-1"
                       value="<%= xssAPI.encodeForHTMLAttr(model.substring(model.lastIndexOf('/') + 1)) %>">
            </div>
            <div class="coral-Form-fieldwrapper">
                <label class="coral-Form-fieldlabel"><%= xssAPI.encodeForHTML(i18n.get("Title")) %></label>
                <input is="coral-textfield"
                       class="coral-Form-field aemcloudproject-activate-workflow-title"
                       type="text"
                       placeholder="<%= xssAPI.encodeForHTMLAttr(i18n.get("Enter title of workflow")) %>">
            </div>
            <ul class="aemcloudproject-activate-workflow-results" hidden>
<%          for (String item : validItems) { %>
                <li class="aemcloudproject-activate-workflow-result" data-path="<%= xssAPI.encodeForHTMLAttr(item) %>">
                    <span class="aemcloudproject-activate-workflow-result-label"><%= xssAPI.encodeForHTML(item) %></span>
                    <span class="aemcloudproject-activate-workflow-result-status"></span>
                </li>
<%          } %>
            </ul>
        </coral-dialog-content>
        <coral-dialog-footer>
            <button is="coral-button" variant="default" type="button" class="aemcloudproject-activate-workflow-cancel" coral-close><%= xssAPI.encodeForHTML(i18n.get("Cancel")) %></button>
            <button is="coral-button" variant="cta" type="button" class="aemcloudproject-activate-workflow-submit"><%= xssAPI.encodeForHTML(i18n.get("Activate")) %></button>
        </coral-dialog-footer>
    </form>
<% } %>
</coral-dialog>
