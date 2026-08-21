package com.aemcloudproject.core.postprocessors;

import java.util.Calendar;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.SlingPostProcessor;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aemcloudproject.core.commons.AbstractDelegatedImageModel;
import com.aemcloudproject.core.models.ResponsiveImage;
import com.day.cq.wcm.api.components.ComponentManager;

/**
 * Mirrors the custom responsive-image properties ({@code desktopImagePath},
 * {@code mobileImagePath}, alt properties) into
 * {@code desktopImage}/{@code mobileImage}
 * child nodes whenever they are posted (dialog save or editor drop target).
 * <p>
 * The mirror nodes carry the standard {@code fileReference}/{@code alt}/
 * {@code altValueFromDAM} properties and the delegate image resource type. They
 * give
 * the Core Components Adaptive Image Servlet a real, addressable JCR resource
 * per
 * variant — the servlet resolves {@code .coreimg.} URLs against the raw node
 * and only
 * understands the standard property names, so the flat custom properties alone
 * cannot
 * serve two different images from one component node.
 */
@Component(service = SlingPostProcessor.class)
public class ResponsiveImagePostProcessor implements SlingPostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(ResponsiveImagePostProcessor.class);

    private static final String PN_FILE_REFERENCE = "fileReference";
    private static final String PN_ALT = "alt";
    private static final String PN_ALT_VALUE_FROM_DAM = "altValueFromDAM";
    private static final String PN_SLING_RESOURCE_TYPE = "sling:resourceType";
    private static final String PN_CQ_LAST_MODIFIED = "cq:lastModified";

    @Override
    public void process(SlingHttpServletRequest request, List<Modification> changes) {

        LOG.info("inside the ResponsiveImagePostProcessor class ");

        if (!isResponsiveImagePost(request)) {
            return;
        }
        Resource resource = request.getResource();
        if (resource == null) {
            return;
        }
        try {
            String delegateType = resolveDelegateType(request, resource);
            LOG.info("inside process method of ResponsiveImagePostProcessor class, delegateType ::{}", delegateType);

            mirror(resource, changes, ResponsiveImage.NN_DESKTOP_IMAGE, delegateType,
                    ResponsiveImage.PN_DESKTOP_IMAGE_PATH, ResponsiveImage.PN_DESKTOP_ALT_TEXT,
                    ResponsiveImage.PN_DESKTOP_ALT_FROM_DAM);
            mirror(resource, changes, ResponsiveImage.NN_MOBILE_IMAGE, delegateType,
                    ResponsiveImage.PN_MOBILE_IMAGE_PATH, ResponsiveImage.PN_MOBILE_ALT_TEXT,
                    ResponsiveImage.PN_MOBILE_ALT_FROM_DAM);
        } catch (Exception e) {
            LOG.error("Unable to mirror responsive image properties on {}", resource.getPath(), e);
        }
    }

    private boolean isResponsiveImagePost(SlingHttpServletRequest request) {
        for (String parameterName : request.getParameterMap().keySet()) {
            if (parameterName.startsWith("./" + ResponsiveImage.PN_DESKTOP_IMAGE_PATH)
                    || parameterName.startsWith("./" + ResponsiveImage.PN_MOBILE_IMAGE_PATH)) {
                return true;
            }
        }
        return false;
    }

    private String resolveDelegateType(SlingHttpServletRequest request, Resource resource) {
        ComponentManager componentManager = request.getResourceResolver().adaptTo(ComponentManager.class);
        if (componentManager != null) {
            com.day.cq.wcm.api.components.Component component = componentManager.getComponentOfResource(resource);
            if (component != null) {
                String delegate = component.getProperties()
                        .get(AbstractDelegatedImageModel.PN_IMAGE_DELEGATE, String.class);
                if (StringUtils.isNotBlank(delegate)) {
                    return delegate;
                }
            }
        }
        return AbstractDelegatedImageModel.CORE_IMAGE_V3_RT;
    }

    private void mirror(Resource resource, List<Modification> changes, String childName, String delegateType,
                        String pathProperty, String altProperty, String altFromDamProperty) throws Exception {
        ValueMap properties = resource.getValueMap();
        ResourceResolver resolver = resource.getResourceResolver();
        String imagePath = properties.get(pathProperty, String.class);
        Resource child = resource.getChild(childName);

        if (StringUtils.isBlank(imagePath)) {
            if (child != null) {
                String childPath = child.getPath();
                resolver.delete(child);
                changes.add(Modification.onDeleted(childPath));
            }
            return;
        }

        if (child == null) {
            child = resolver.create(resource, childName, null);
            changes.add(Modification.onCreated(child.getPath()));
        }
        ModifiableValueMap childProperties = child.adaptTo(ModifiableValueMap.class);
        if (childProperties == null) {
            LOG.warn("Cannot modify mirror node {}", child.getPath());
            return;
        }
        childProperties.put(PN_SLING_RESOURCE_TYPE, delegateType);
        childProperties.put(PN_FILE_REFERENCE, imagePath);
        childProperties.put(PN_ALT_VALUE_FROM_DAM, properties.get(altFromDamProperty, Boolean.TRUE));
        String altText = properties.get(altProperty, String.class);
        if (StringUtils.isNotBlank(altText)) {
            childProperties.put(PN_ALT, altText);
        } else {
            childProperties.remove(PN_ALT);
        }
        childProperties.put(PN_CQ_LAST_MODIFIED, Calendar.getInstance());
        changes.add(Modification.onModified(child.getPath()));
    }
}
