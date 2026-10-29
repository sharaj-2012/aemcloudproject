package com.aemcloudproject.core.services.impl;

import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.ResourceResolver;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;

/**
 * Checks that the requesting user may rewrite and version a DAM asset.
 */
class AssetPrivileges {

    private static final String REP_WRITE = "rep:write";
    private static final String[] WRITTEN_DESCENDANTS = {
            JcrConstants.JCR_CONTENT,
            JcrConstants.JCR_CONTENT + "/metadata",
            JcrConstants.JCR_CONTENT + "/renditions/original"
    };

    /**
     * @param resolver  requesting user's resolver
     * @param assetPath asset path
     * @return true when the user holds rep:write on the asset and its written descendants, and jcr:versionManagement on the asset
     */
    boolean canEdit(ResourceResolver resolver, String assetPath) {
        Session session = resolver.adaptTo(Session.class);
        if (session == null) {
            return false;
        }
        try {
            AccessControlManager acm = session.getAccessControlManager();
            Privilege write = acm.privilegeFromName(REP_WRITE);
            Privilege version = acm.privilegeFromName(Privilege.JCR_VERSION_MANAGEMENT);
            if (!acm.hasPrivileges(assetPath, new Privilege[]{write, version})) {
                return false;
            }
            for (String descendant : WRITTEN_DESCENDANTS) {
                String path = assetPath + "/" + descendant;
                if (session.nodeExists(path) && !acm.hasPrivileges(path, new Privilege[]{write})) {
                    return false;
                }
            }
            return true;
        } catch (RepositoryException e) {
            return false;
        }
    }
}
