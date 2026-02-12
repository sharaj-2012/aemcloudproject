package com.aemcloudproject.core.models;

import java.util.List;
import java.util.Map;

public interface GenericAemModel {
    String getTitle();

    String getLinkurl();

    boolean isHideText();

    List<Map<String, String>> getTabList();

    String getPageTitle();

    String getParentPath();

    String getSlingResType();
}
