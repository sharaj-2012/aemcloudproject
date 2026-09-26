package com.aemcloudproject.core.bulkimport;

import com.adobe.acs.commons.mcp.AuthorizedGroupProcessDefinitionFactory;
import com.adobe.acs.commons.mcp.ControlledProcessManager;
import com.adobe.acs.commons.mcp.ProcessDefinitionFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * Registers the Offer Bulk Import with ACS Commons MCP (Tools &gt; ACS AEM Commons &gt; Manage Controlled Processes).
 */
@Component(service = ProcessDefinitionFactory.class)
@Designate(ocd = OfferBulkImportFactory.Config.class)
public class OfferBulkImportFactory extends AuthorizedGroupProcessDefinitionFactory<OfferBulkImport> {

    private static final Logger LOG = LoggerFactory.getLogger(OfferBulkImportFactory.class);
    private static final String DEFAULT_ZONE = "Asia/Singapore";

    @ObjectClassDefinition(name = "Offer Bulk Import",
            description = "Who can run the offer workbook import, and how it reads dates")
    public @interface Config {

        @AttributeDefinition(name = "Authorized groups",
                description = "Groups that can see and run the import. Administrators always can.")
        String[] authorized_groups() default {"administrators"};

        @AttributeDefinition(name = "Time zone",
                description = "Zone for workbook dates that carry no offset, e.g. Asia/Singapore")
        String time_zone() default DEFAULT_ZONE;
    }

    /* Dynamic and optional: the process manager also binds every factory, including this one. */
    @Reference(policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.OPTIONAL,
            policyOption = ReferencePolicyOption.GREEDY)
    private volatile ControlledProcessManager processManager;

    private String[] authorizedGroups = new String[0];
    private ZoneId zone = ZoneId.of(DEFAULT_ZONE);

    @Activate
    @Modified
    protected void activate(Config config) {
        authorizedGroups = config.authorized_groups() == null ? new String[0] : config.authorized_groups();
        try {
            zone = ZoneId.of(config.time_zone());
        } catch (DateTimeException e) {
            LOG.error("Invalid time zone '{}', using {}", config.time_zone(), DEFAULT_ZONE);
            zone = ZoneId.of(DEFAULT_ZONE);
        }
    }

    @Override
    public String getName() {
        return OfferBulkImport.NAME;
    }

    @Override
    protected String[] getAuthorizedGroups() {
        return authorizedGroups.clone();
    }

    @Override
    protected OfferBulkImport createProcessDefinitionInstance() {
        return new OfferBulkImport(processManager, zone);
    }
}
