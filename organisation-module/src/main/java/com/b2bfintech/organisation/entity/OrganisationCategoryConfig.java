package com.b2bfintech.organisation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Organisation-level on/off switch for one BBPS category. No row means the category is disabled. */
@Entity
@Table(name = "organisation_category_config")
@Getter
@Setter
@NoArgsConstructor
public class OrganisationCategoryConfig {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "category_id", nullable = false, unique = true)
    private String categoryId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "enabled_by")
    private String enabledBy;

    @Column(name = "enabled_at")
    private Instant enabledAt;

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
}
