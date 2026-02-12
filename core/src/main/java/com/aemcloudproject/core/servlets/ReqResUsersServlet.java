package com.aemcloudproject.core.servlets;

import com.aemcloudproject.core.dto.UserDTO;
import com.aemcloudproject.core.dto.UserResponseDTO;
import com.aemcloudproject.core.services.HttpConnectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component(service = {Servlet.class})
@SlingServletResourceTypes(
        resourceTypes = ReqResUsersServlet.COMPONENT,
        methods = HttpConstants.METHOD_GET,
        extensions = "json",
        selectors = "regres")
public class ReqResUsersServlet extends SlingSafeMethodsServlet {
    public static final String COMPONENT = "aemcloudproject/components/customcomponents/reqresusersnew";
    private static final Logger log = LoggerFactory.getLogger(ReqResUsersServlet.class);

    @Reference
    transient HttpConnectionService httpConnectionService;



    @Override
    protected void doGet(SlingHttpServletRequest request,SlingHttpServletResponse response) throws IOException {

        List<UserDTO> userDTOList ;

        String pageParam = request.getParameter("page");

        String userData = httpConnectionService.getReqResResponse(pageParam);

        ObjectMapper mapper = new ObjectMapper();
        UserResponseDTO userResponseDTO = mapper.readValue(userData,UserResponseDTO.class);

        userDTOList = userResponseDTO.getData();

        Map<String, Object> result = new HashMap<>();
        result.put("page", userResponseDTO.getPage());
        result.put("total_pages", userResponseDTO.getTotalPages());
        result.put("users", userDTOList);

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        log.info("inside servlet::{}",result);
        mapper.writeValue(response.getWriter(),result);
    }
}
