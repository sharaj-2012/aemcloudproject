package com.aemcloudproject.core.models;

import com.aemcloudproject.core.dto.UserDTO;
import java.util.List;

public interface UserInfoModel {

    List<UserDTO> getUserDTOList();

    String getCurrentPage();

    int getTotalPageVal();
}
