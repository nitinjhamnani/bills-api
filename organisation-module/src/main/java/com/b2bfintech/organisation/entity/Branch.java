package com.b2bfintech.organisation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A branch of one corporate or one vendor. Managed bills belong to a branch. */
@Entity
@Table(name = "branch")
@Getter
@Setter
@NoArgsConstructor
public class Branch {

    @Id
    @Column(name = "branch_id", nullable = false, length = 8)
    private String branchId;

    @Column(name = "branch_name", nullable = false, length = 150)
    private String name;

    @Column(name = "branch_code", length = 40)
    private String code;

    @Column(name = "branch_address", length = 500)
    private String address;

    @Column(name = "branch_state", length = 100)
    private String state;

    @Column(name = "branch_location", length = 150)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "branch_source_type", nullable = false, length = 16)
    private SourceType sourceType;

    /** Corporate or vendor id. Still the client uuid until that table is redesigned. */
    @Column(name = "branch_source_id", nullable = false, length = 64)
    private String sourceId;

    public String getId() {
        return branchId;
    }

    public boolean ownedBy(String ownerId) {
        return ownerId != null && ownerId.equals(sourceId);
    }

    public enum SourceType {
        CORP, VENDOR
    }
}
