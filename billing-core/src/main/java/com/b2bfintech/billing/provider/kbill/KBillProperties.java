package com.b2bfintech.billing.provider.kbill;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * KBill BBPS connection settings. The API token has no default and must come from the environment;
 * it is never written into application.yml.
 */
@ConfigurationProperties(prefix = "app.billing.kbill")
public class KBillProperties {

    private String baseUrl = "https://development.kbillpay.com/api/kbill/v3/customer/bbps";
    private String apiToken = "";
    private String userAgent = "paytrix-b2bfintech/1.0";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 20000;
    private long catalogRefreshMs = 7_200_000L;
    private long parameterCacheTtlMs = 7_200_000L;
    private boolean logPayloads = false;
    private int maxLoggedBodyChars = 4096;
    private long slowCallThresholdMs = 5000;
    private Paths paths = new Paths();

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

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
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

    public long getCatalogRefreshMs() {
        return catalogRefreshMs;
    }

    public void setCatalogRefreshMs(long catalogRefreshMs) {
        this.catalogRefreshMs = catalogRefreshMs;
    }

    public long getParameterCacheTtlMs() {
        return parameterCacheTtlMs;
    }

    public void setParameterCacheTtlMs(long parameterCacheTtlMs) {
        this.parameterCacheTtlMs = parameterCacheTtlMs;
    }

    public boolean isLogPayloads() {
        return logPayloads;
    }

    public void setLogPayloads(boolean logPayloads) {
        this.logPayloads = logPayloads;
    }

    public int getMaxLoggedBodyChars() {
        return maxLoggedBodyChars;
    }

    public void setMaxLoggedBodyChars(int maxLoggedBodyChars) {
        this.maxLoggedBodyChars = maxLoggedBodyChars;
    }

    public long getSlowCallThresholdMs() {
        return slowCallThresholdMs;
    }

    public void setSlowCallThresholdMs(long slowCallThresholdMs) {
        this.slowCallThresholdMs = slowCallThresholdMs;
    }

    public Paths getPaths() {
        return paths;
    }

    public void setPaths(Paths paths) {
        this.paths = paths;
    }

    public static class Paths {
        private String category = "/category";
        private String location = "/location";
        private String operatorList = "/operator-list";
        private String operatorParameters = "/operator-parameters";
        private String billFetch = "/bill-fetched";

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getOperatorList() {
            return operatorList;
        }

        public void setOperatorList(String operatorList) {
            this.operatorList = operatorList;
        }

        public String getOperatorParameters() {
            return operatorParameters;
        }

        public void setOperatorParameters(String operatorParameters) {
            this.operatorParameters = operatorParameters;
        }

        public String getBillFetch() {
            return billFetch;
        }

        public void setBillFetch(String billFetch) {
            this.billFetch = billFetch;
        }
    }
}
