package com.b2bfintech.billing.provider.branchx;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 10X FI BBPS connection. The API token has no default and must come from the environment. */
@ConfigurationProperties(prefix = "app.billing.branchx")
public class BranchXProperties {

    private String baseUrl = "https://10x.api.branchx.in";
    private String apiToken = "";
    private int connectTimeoutMs = 5_000;
    private int readTimeoutMs = 25_000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
