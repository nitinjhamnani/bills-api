package com.b2bfintech.platform.entity;

import com.b2bfintech.platform.model.BbpsCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bbps_category")
@Getter
@Setter
@NoArgsConstructor
public class BbpsCategoryEntity {

    @Id
    private UUID id;

    @Column(name = "provider_code", nullable = false, length = 32)
    private String providerCode;

    @Column(name = "category_id", nullable = false, length = 64)
    private String categoryId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "category_group", length = 32)
    private String categoryGroup;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    public BbpsCategory toModel() {
        return new BbpsCategory(id, providerCode, categoryId, name, categoryGroup, active, syncedAt);
    }
}
