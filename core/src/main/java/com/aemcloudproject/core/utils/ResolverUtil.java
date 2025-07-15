package com.aemcloudproject.core.utils;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;

import java.util.HashMap;
import java.util.Map;

public final class ResolverUtil {
    public static final String MY_AEMPROJECT_SERVICE_USER = "myaemproject-serviceuser";

    private ResolverUtil() {}

    /**
     * @param resourceResolverFactory factory
     * @return new resource resolver for TP service user
     * @throws LoginException if problems
     */
    public static ResourceResolver newResolver(ResourceResolverFactory resourceResolverFactory) throws LoginException {
        final Map<String, Object> paramMap = new HashMap<>();
        paramMap.put(ResourceResolverFactory.SUBSERVICE, MY_AEMPROJECT_SERVICE_USER);

        return resourceResolverFactory.getServiceResourceResolver(paramMap);
    }

    /**
     * closes resourceResolver
     *
     * @param resourceResolver
     */
    public static void closeResourceResolver(ResourceResolver resourceResolver) {
        if (resourceResolver != null && resourceResolver.isLive()) {
            resourceResolver.close();
        }
    }

}