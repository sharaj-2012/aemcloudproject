package com.aemcloudproject.core.models.impl;

import com.aemcloudproject.core.models.GenericAemModel;
import com.day.cq.wcm.api.Page;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Via;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.eclipse.jetty.security.LoggedOutAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.*;


@Model(adaptables = SlingHttpServletRequest.class, adapters = GenericAemModel.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class GenericAemModelImpl implements GenericAemModel {

    private static final Logger log = LoggerFactory.getLogger(GenericAemModelImpl.class);

    @SlingObject
    ResourceResolver resourceResolver;

    @Inject
    Page page;

    String pageTitle;
    String parentPath;

    // when adaptales is SlingHttpServletRequest
    @Self
    @Via("resource")
    Resource resource;

    List<Map<String, String>> tabList;

    // when adaptales is SlingHttpServletRequest
//    @Self
//    SlingHttpServletRequest request;

    @ValueMapValue
    private String title;

    @ValueMapValue
    private String linkurl;

    String slingResType;

    // When adaptable is Resource.class
//    @Self
//    Resource resource;

    // this can also be used
//    @SlingObject
//    Resource resource;

    @ValueMapValue
    private boolean hideText;

    @PostConstruct
    public void initModel() {

        tabList = new ArrayList<>();
        // Resource resource = request.getResource();

        //  PageManager pageManager = resourceResolver.adaptTo(PageManager.class);
        //  Page page = pageManager.getPage(linkurl);

        parentPath = page.getAbsoluteParent(2).getPath();

        pageTitle = page.getTitle();

        Resource tabResource = resource.getChild("tabsList");

        if (tabResource != null && tabResource.hasChildren()) {

            for (Resource tab : tabResource.getChildren()) {
                Map<String, String> tabListMap = new HashMap<>();
                tabListMap.put(tab.getValueMap().get("anchorTitle", String.class), tab.getValueMap().get("anchorId", String.class));
                tabList.add(tabListMap);
            }
        }

        Resource linkResource = resourceResolver.getResource(linkurl);
        Resource jcrResource = linkResource.getChild("jcr:content");
        Iterator<Resource> children = jcrResource.listChildren();
        while (children.hasNext()) {
            Resource res = children.next();
            ValueMap resValueMap = res.adaptTo(ValueMap.class);
            slingResType = resValueMap.get("sling:resourceType", String.class);
            log.info("Sling resourceType Value ::{}",slingResType);
        }
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getLinkurl() {
        return linkurl;
    }

    @Override
    public boolean isHideText() {
        return hideText;
    }

    @Override
    public List<Map<String, String>> getTabList() {
        return tabList;
    }

    @Override
    public String getPageTitle() {
        return pageTitle;
    }

    @Override
    public String getParentPath() {
        return parentPath;
    }

    @Override
    public String getSlingResType() {
        return slingResType;
    }
}


