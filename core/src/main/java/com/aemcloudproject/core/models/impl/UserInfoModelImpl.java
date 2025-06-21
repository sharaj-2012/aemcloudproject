package com.aemcloudproject.core.models.impl;

import com.aemcloudproject.core.dto.UserDTO;
import com.aemcloudproject.core.dto.UserResponseDTO;
import com.aemcloudproject.core.models.UserInfoModel;
import com.aemcloudproject.core.services.HttpConnectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.osgi.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Model(adaptables={SlingHttpServletRequest.class,Resource.class},adapters = UserInfoModel.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class UserInfoModelImpl implements UserInfoModel {

    private static final Logger log = LoggerFactory.getLogger(UserInfoModelImpl.class);

    private List<UserDTO> userDTOList;

    @Self
    private SlingHttpServletRequest request;

    @OSGiService
    private HttpConnectionService httpConnectionService;

    private String currentPage;

    private int totalPageVal;

    @PostConstruct
    private void init() throws IOException {

        currentPage = request.getResource().getPath();

        try {
            String userData =  httpConnectionService.getReqResResponse();

            ObjectMapper mapper = new ObjectMapper();
            UserResponseDTO userResponseDTO = mapper.readValue(userData, UserResponseDTO.class);

            totalPageVal = userResponseDTO.getTotal();
            this.userDTOList = userResponseDTO.getData();
        } catch (IOException e) {
            log.error("Error in the Model",e);
        }
    }

    @Override
    public List<UserDTO> getUserDTOList() {
        return userDTOList != null ? userDTOList : Collections.emptyList();
    }

    @Override
    public String getCurrentPage() {
        return currentPage;
    }

    @Override
    public int getTotalPageVal() {
        return totalPageVal;
    }
}
