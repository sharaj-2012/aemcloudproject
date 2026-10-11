package com.aemcloudproject.core.utils;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reads Content Fragment model settings that the Content Fragment API does not expose.
 */
public final class ScbCfModelUtil {

    private ScbCfModelUtil() {
    }

    /**
     * Returns the fields marked Required in the model, read from the model's dialog definition.
     *
     * @param resolver  the resource resolver
     * @param modelPath the model path, e.g. {@code /conf/aemcloudproject/settings/dam/cfm/models/offer-detail}
     * @return the mandatory field names in model order; empty if the model cannot be read
     */
    public static Set<String> scbRequiredFields(ResourceResolver resolver, String modelPath) {
        Set<String> required = new LinkedHashSet<>();
        Resource items = resolver.getResource(modelPath + "/jcr:content/model/cq:dialog/content/items");
        if (items == null) {
            return required;
        }
        for (Resource item : items.getChildren()) {
            String name = item.getValueMap().get("name", String.class);
            String flag = item.getValueMap().get("required", String.class);
            if (name != null && ("on".equals(flag) || "true".equals(flag))) {
                required.add(name);
            }
        }
        return required;
    }
}
