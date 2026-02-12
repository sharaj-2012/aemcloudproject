package com.aemcloudproject.core.servlets;

import com.aemcloudproject.core.services.BulkAssetPublishService;
import com.day.text.csv.Csv;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static com.aemcloudproject.core.constants.MyProjectConstants.BULK;
import static com.aemcloudproject.core.constants.MyProjectConstants.JSON;

@Component(service = Servlet.class)
@SlingServletResourceTypes(resourceTypes = BulkAssetPublishServlet.BULK_PUBLISH_RESOURCE,methods = HttpConstants.METHOD_POST,selectors = BULK, extensions = JSON )
public class BulkAssetPublishServlet extends SlingAllMethodsServlet {

    public static final String BULK_PUBLISH_RESOURCE = "aemcloudproject/components/customcomponents/bulkpublish";
    private static final String ASSETPATH = "assetPath";
    public static final Logger log = LoggerFactory.getLogger(BulkAssetPublishServlet.class);

    @Reference
    transient BulkAssetPublishService bulkAssetPublishService;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {

        Map<String, RequestParameter[]> params = request.getRequestParameterMap();
        for (Map.Entry<String, RequestParameter[]> entry : params.entrySet()) {// "pagePath" or "file"
            RequestParameter param = entry.getValue()[0]; // first value

            if (!param.isFormField()) {
                InputStream fileStream = param.getInputStream(); // for files

                Iterator<String[]> iterator = new Csv().read(fileStream, null);

                List<String> assetPaths = getAssetPaths(iterator);// for text inputs

                bulkAssetPublishService.processAssets(assetPaths,request.getResourceResolver());

            }
        }

   }

    private List<String> getAssetPaths(Iterator<String[]> iterator) {
        List<String> assetsPaths = new ArrayList<>();
        while(iterator.hasNext()){
           String[] row = iterator.next();
           String element = row[0];
           if(element.equalsIgnoreCase(ASSETPATH)){
               continue;
           }
           assetsPaths.add(element);
        }
        return assetsPaths;
    }
}
