package com.aemcloudproject.core.services;

import java.io.IOException;

public interface HttpConnectionService {

    String getReqResResponse() throws IOException;

    String getReqResResponse(String pageParam) throws IOException;
}
