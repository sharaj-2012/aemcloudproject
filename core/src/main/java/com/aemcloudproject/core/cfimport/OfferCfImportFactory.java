package com.aemcloudproject.core.cfimport;

import com.adobe.acs.commons.mcp.AdministratorsOnlyProcessDefinitionFactory;
import com.adobe.acs.commons.mcp.ControlledProcessManager;
import com.adobe.acs.commons.mcp.ProcessDefinitionFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Registers "Offer CF Import" with ACS Commons MCP. MCP lists every ProcessDefinitionFactory
 * service in its Start Process dialog, and calls {@link #createProcessDefinition()} once per run,
 * so each run gets its own {@link OfferCfImport} with its own state.
 */
@Component(service = ProcessDefinitionFactory.class)
public class OfferCfImportFactory extends AdministratorsOnlyProcessDefinitionFactory<OfferCfImport> {

    static final String PROCESS_NAME = "Offer CF Import";

    /** Passed to each run so it can refuse to start while another import runs. */
    @Reference
    private ControlledProcessManager processManager;

    @Override
    public String getName() {
        return PROCESS_NAME;
    }

    @Override
    protected OfferCfImport createProcessDefinitionInstance() {
        return new OfferCfImport(processManager);
    }
}
