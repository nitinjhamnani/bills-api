package com.b2bfintech.common.document;

import com.b2bfintech.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.util.UUID;

/**
 * Stores uploaded files in DigitalOcean Spaces. The object key is
 * {@code <prefix>/<client code>/<id>-<file name>}, for example {@code client/PXACM482/….png}.
 */
@Component
public class DocumentStorageService {

    private final S3Client spaces;
    private final String bucket;
    private final String prefix;

    public DocumentStorageService(
            S3Client spacesS3Client,
            @Value("${app.documents.spaces-bucket}") String bucket,
            @Value("${app.documents.spaces-prefix:client}") String prefix) {
        this.spaces = spacesS3Client;
        this.bucket = bucket;
        this.prefix = prefix == null || prefix.isBlank() ? "client" : prefix.replaceAll("[^a-zA-Z0-9_-]", "");
    }

    public String store(MultipartFile file, String clientCode) {
        if (clientCode == null || !clientCode.matches("[A-Za-z0-9]{6}|[A-Za-z0-9]{8}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "A client code is required to store the file");
        }
        String key = prefix + "/" + clientCode + "/" + UUID.randomUUID() + "-" + sanitize(file.getOriginalFilename());
        String contentType = file.getContentType() == null || file.getContentType().isBlank()
                ? "application/octet-stream"
                : file.getContentType();
        try {
            spaces.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            return key;
        } catch (S3Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "STORAGE_ERROR", "Could not store the uploaded file");
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "Could not store the uploaded file");
        }
    }

    public InputStream load(String storagePath) {
        try {
            return spaces.getObject(GetObjectRequest.builder().bucket(bucket).key(storagePath).build());
        } catch (S3Exception ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Stored file not found");
        }
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) return "file";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
