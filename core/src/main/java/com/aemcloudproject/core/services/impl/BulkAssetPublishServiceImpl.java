package com.aemcloudproject.core.services.impl;

import com.aemcloudproject.core.services.BulkAssetPublishService;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.event.jobs.JobManager;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component(service = BulkAssetPublishService.class)
public class BulkAssetPublishServiceImpl implements BulkAssetPublishService {

    private static final String JOB_TOPIC = "aemproject/assetreplication/job";

    @Reference
    JobManager jobManager;

    @Override
    public void processAssets(List<String> assetPaths, ResourceResolver resourceResolver){

        Map<String,Object> jobProperties = new HashMap<>();
        jobProperties.put("assetPaths",assetPaths);

        jobManager.addJob(JOB_TOPIC,jobProperties);
    }
}
