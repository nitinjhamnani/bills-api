package com.b2bfintech.common.document;

import com.b2bfintech.common.exception.ApiException;
import com.b2bfintech.common.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Local-disk file storage for Phase 1-4, namespaced by tenant schema so one tenant's uploads
 * can never collide with (or be served from) another's directory. Swap for a GCS/S3-backed
 * implementation later - callers only ever see a logical {@code storagePath} string.
 */
@Component
public class DocumentStorageService {

    private final Path baseDir;

    public DocumentStorageService(@Value("${app.documents.storage-dir}") String storageDir) {
        this.baseDir = Path.of(storageDir);
    }

    public String store(MultipartFile file) {
        try {
            Path tenantDir = baseDir.resolve(TenantContext.getCurrentTenant());
            Files.createDirectories(tenantDir);
            String safeName = UUID.randomUUID() + "-" + sanitize(file.getOriginalFilename());
            Path target = tenantDir.resolve(safeName);
            file.transferTo(target);
            return baseDir.relativize(target).toString();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "Could not store the uploaded file");
        }
    }

    public InputStream load(String storagePath) {
        try {
            return Files.newInputStream(baseDir.resolve(storagePath));
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Stored file not found");
        }
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) return "file";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
