package com.b2bfintech.common.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * DigitalOcean Spaces speaks the S3 API. The public object host is
 * {@code https://<bucket>.<region>.digitaloceanspaces.com}; the client talks to the regional
 * endpoint and selects the bucket separately.
 */
@Configuration
public class SpacesClientConfig {

    @Bean(destroyMethod = "close")
    S3Client spacesS3Client(
            @Value("${app.documents.spaces-endpoint}") String endpoint,
            @Value("${app.documents.spaces-region}") String region,
            @Value("${app.documents.spaces-bucket}") String bucket,
            @Value("${app.documents.spaces-access-key}") String accessKey,
            @Value("${app.documents.spaces-secret-key}") String secretKey) {
        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .endpointOverride(URI.create(regionalEndpoint(endpoint, region, bucket)))
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(false).build())
                .build();
    }

    /**
     * Accepts either the regional host or the bucket host the Spaces console shows
     * ({@code https://paytrix.sgp1.digitaloceanspaces.com}).
     */
    static String regionalEndpoint(String endpoint, String region, String bucket) {
        String trimmed = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        String bucketHost = bucket + "." + region + ".digitaloceanspaces.com";
        try {
            String host = URI.create(trimmed).getHost();
            if (host != null && host.equalsIgnoreCase(bucketHost)) {
                return "https://" + region + ".digitaloceanspaces.com";
            }
        } catch (IllegalArgumentException ignored) {
            // Fall through and let the S3 client report a bad endpoint.
        }
        return trimmed;
    }
}
