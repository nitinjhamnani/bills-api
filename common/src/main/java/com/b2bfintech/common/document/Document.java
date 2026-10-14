package com.b2bfintech.common.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic document metadata (KYC, client agreements, offline deposit receipts, ...) - tenant
 * schema table, resolved via the same schema-per-tenant routing as every other tenant entity.
 * The file itself lives on local disk under {@code app.documents.storage-dir} for now; swap
 * {@link DocumentStorageService} for a GCS/S3-backed implementation later without touching this
 * table or any caller.
 */
@Entity
@Table(name = "document")
@Getter
@Setter
@NoArgsConstructor
public class Document {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "owner_type", nullable = false)
    private String ownerType;

    @Column(name = "owner_id")
    private String ownerId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status = DocumentStatus.PENDING_REVIEW;

    @Column(name = "uploaded_by")
    private String uploadedBy;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "review_notes")
    private String reviewNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum DocumentStatus {
        PENDING_REVIEW, APPROVED, REJECTED
    }
}
