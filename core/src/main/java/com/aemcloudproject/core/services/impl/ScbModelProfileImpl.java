package com.aemcloudproject.core.services.impl;

import com.aemcloudproject.core.config.ScbModelProfileConfiguration;
import com.aemcloudproject.core.services.ScbModelProfile;
import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.Designate;

/**
 * One model profile, created by OSGi for each {@link ScbModelProfileConfiguration} factory configuration.
 */
@Component(service = ScbModelProfile.class, configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = ScbModelProfileConfiguration.class, factory = true)
public class ScbModelProfileImpl implements ScbModelProfile {

    private String model;
    private String folder;
    private String nameField;
    private String titleField;
    private String idField;
    private long startId;
    private int order;

    /**
     * Reads the profile from its configuration.
     *
     * @param config the model profile configuration
     * @throws IllegalArgumentException if the model, folder or name field is empty; the profile is then not registered
     */
    @Activate
    protected void activate(ScbModelProfileConfiguration config) {
        model = StringUtils.trimToNull(config.model());
        folder = StringUtils.strip(StringUtils.trimToNull(config.folder()), "/");
        nameField = StringUtils.trimToNull(config.nameField());
        if (model == null || StringUtils.isEmpty(folder) || nameField == null) {
            throw new IllegalArgumentException("SCB CF model profile \"" + config.model()
                    + "\" needs a model, a folder and a name field");
        }
        titleField = StringUtils.trimToNull(config.titleField());
        idField = StringUtils.trimToNull(config.idField());
        startId = config.startId();
        order = config.order();
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public String getFolder() {
        return folder;
    }

    @Override
    public String getNameField() {
        return nameField;
    }

    @Override
    public String getTitleField() {
        return titleField;
    }

    @Override
    public String getIdField() {
        return idField;
    }

    @Override
    public long getStartId() {
        return startId;
    }

    @Override
    public int getOrder() {
        return order;
    }
}
