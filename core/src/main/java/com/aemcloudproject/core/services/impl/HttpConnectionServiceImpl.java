package com.aemcloudproject.core.services.impl;

import com.aemcloudproject.core.services.HttpConnectionService;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.apache.http.util.EntityUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;


@Designate(ocd = HttpConnectionServiceImpl.Config.class)
@Component(service = {HttpConnectionService.class})
public class HttpConnectionServiceImpl implements HttpConnectionService {

    @Reference
    private HttpClientBuilderFactory httpClientBuilderFactory;

    private static final Logger log = LoggerFactory.getLogger(HttpConnectionServiceImpl.class);

    @ObjectClassDefinition(name = "HTTP Connection Config", description = "Base API URLs and timeouts")
    public @interface Config {

        @AttributeDefinition(name = "ReqRes Get URL")
        String reqResGetUrl() default "https://api.example.com/auth";

        @AttributeDefinition(name = "Connect Timeout (ms)")
        int connectTimeout() default 5000;

        @AttributeDefinition(name = "Socket Timeout (ms)")
        int socketTimeout() default 5000;
    }

    private String reqResGetUrl;
    private int connectTimeout;
    private int socketTimeout;

    @Activate
    @Modified
    protected void activate(Config config) {
        this.reqResGetUrl = config.reqResGetUrl();
        this.connectTimeout = config.connectTimeout();
        this.socketTimeout = config.socketTimeout();

        log.info("HTTP config activated: authBaseUrl={}, assetBaseUrl={}, timeouts={}", reqResGetUrl, connectTimeout, socketTimeout);
    }

    @Override
    public String getReqResResponse() throws IOException {
        String responseString;
        try(CloseableHttpClient client = createHttpClient()){
            HttpGet getRequest = new HttpGet(reqResGetUrl);

            Map<String, String> headers = Map.of(
                    "Accept", "application/json",
                    "x-api-key","reqres-free-v1",
                    "Accept-Encoding", "compress, gzip"
            );
            headers.forEach(getRequest::setHeader);

            try (CloseableHttpResponse httpResponse = client.execute(getRequest)) {
                HttpEntity responseEntity = httpResponse.getEntity();

                if (httpResponse.getStatusLine().getStatusCode() == HttpServletResponse.SC_OK || httpResponse.getStatusLine().getStatusCode() == HttpServletResponse.SC_CREATED) {
                    responseString = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);
                    log.info("inside Service :: {}", responseString);
                    return responseString;
                } else {
                    log.error("Failed to retrieve access token, status code: {}", httpResponse.getStatusLine().getStatusCode());
                    return "Failed to retrieve access token, status code";
                }
            }
        }
    }

    @Override
    public String getReqResResponse(String pageParam) throws IOException {
        String responseString;

        String finalReqResUrlEndpoint = new StringBuilder().append(reqResGetUrl).append("?page=").append(pageParam).toString();

        try(CloseableHttpClient client = createHttpClient()){
            HttpGet getRequest = new HttpGet(finalReqResUrlEndpoint);

            Map<String, String> headers = Map.of(
                    "Accept", "application/json",
                    "x-api-key","reqres-free-v1",
                    "Accept-Encoding", "compress, gzip"
            );
            headers.forEach(getRequest::setHeader);

            try (CloseableHttpResponse httpResponse = client.execute(getRequest)) {
                HttpEntity responseEntity = httpResponse.getEntity();

                if (httpResponse.getStatusLine().getStatusCode() == HttpServletResponse.SC_OK || httpResponse.getStatusLine().getStatusCode() == HttpServletResponse.SC_CREATED) {
                    responseString = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);
                    log.info("inside Service :: {}", responseString);
                    return responseString;
                } else {
                    log.error("Failed to retrieve access token, status code: {}", httpResponse.getStatusLine().getStatusCode());
                    return "Failed to retrieve access token, status code";
                }
            }
        }
    }

    private CloseableHttpClient createHttpClient() {
        return httpClientBuilderFactory.newBuilder().setDefaultRequestConfig(RequestConfig.custom()
                                                        .setConnectTimeout(connectTimeout)
                                                        .setSocketTimeout(socketTimeout)
                                                        .build()).build();
    }
}
