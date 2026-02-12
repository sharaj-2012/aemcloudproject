package com.aemcloudproject.core.schedulers;

import com.aemcloudproject.core.config.MoveAssetJobConfiguration;
import org.apache.sling.event.jobs.JobManager;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

@Component(immediate = true)
@Designate(ocd = MoveAssetJobConfiguration.class)
public class MoveAssetJobScheduler {

    @Reference
    private JobManager jobManager;

}
