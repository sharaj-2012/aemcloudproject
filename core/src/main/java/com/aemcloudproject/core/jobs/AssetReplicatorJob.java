package com.aemcloudproject.core.jobs;

import com.aemcloudproject.core.utils.ResolverUtil;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.Replicator;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Session;
import java.util.List;

import static org.apache.sling.event.jobs.consumer.JobConsumer.PROPERTY_TOPICS;
import static org.osgi.framework.Constants.SERVICE_DESCRIPTION;

@Component(service = JobConsumer.class,immediate = true,
property = {
            SERVICE_DESCRIPTION +"= This job replicates the payload",
            PROPERTY_TOPICS + "=" + AssetReplicatorJob.ASSET_REPLICATION_TOPIC,
})
public class AssetReplicatorJob implements JobConsumer {

    private static final Logger log = LoggerFactory.getLogger(AssetReplicatorJob.class);
    protected static final String ASSET_REPLICATION_TOPIC = "aemproject/assetreplication/job";

    @Reference
    private Replicator replicator;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Override
    public JobResult process(Job job) {
        log.info("Jobprocess Started");

        List<String> assetsPaths = (List<String>) job.getProperty("assetPaths");

        try(ResourceResolver resourceResolver = ResolverUtil.newResolver(resourceResolverFactory)){
            Session session = resourceResolver.adaptTo(Session.class);
            for(String path:assetsPaths){
                replicator.replicate(session, ReplicationActionType.ACTIVATE, path);
            }
        } catch (Exception e) {
            return JobResult.FAILED;
        }
        return JobResult.OK;
    }
}
