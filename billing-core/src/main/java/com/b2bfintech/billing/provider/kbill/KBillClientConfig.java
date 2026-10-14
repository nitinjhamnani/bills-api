package com.b2bfintech.billing.provider.kbill;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(KBillProperties.class)
@ConditionalOnProperty(name = "app.billing.provider", havingValue = "kbill")
public class KBillClientConfig {

    @Bean
    RestClient kbillRestClient(KBillProperties properties, ObjectMapper objectMapper, KBillCallLoggingInterceptor interceptor) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        RestTemplate restTemplate = new RestTemplate(new BufferingClientHttpRequestFactory(requestFactory));
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return RestClient.builder(restTemplate)
                .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiToken())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.getUserAgent())
                .requestInterceptor(interceptor)
                .build();
    }

    @Bean
    KBillCallLoggingInterceptor kbillCallLoggingInterceptor(
            KBillProperties properties,
            ObjectMapper objectMapper,
            org.springframework.beans.factory.ObjectProvider<com.b2bfintech.common.spi.bbps.ProviderCallRecorder> recorder) {
        return new KBillCallLoggingInterceptor(properties, objectMapper, recorder);
    }

    private static String trimTrailingSlash(String baseUrl) {
        if (baseUrl == null) return "";
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
