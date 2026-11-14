package com.b2bfintech.organisation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A person who can sign in. Paytrix, a corporate, or a vendor, told apart by {@link #userType}. */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    public static final String PAYTRIX = "PAYTRIX";

    @Id
    @Column(name = "user_id", nullable = false, length = 6)
    private String userId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(unique = true, length = 150)
    private String email;

    @Column(nullable = false, unique = true, length = 15)
    private String phone;

    @Column(name = "created_by_type", length = 32)
    private String createdByType;

    @Column(name = "created_by_id", length = 6)
    private String createdById;

    @Column(name = "created_on", nullable = false, updatable = false)
    private Instant createdOn;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 32)
    private UserType userType;

    public boolean hasUserType(UserType type) {
        return type != null && type == userType;
    }

    /** {@code PAYTRIX}, or the id of the corporate or vendor this person belongs to. */
    @Column(name = "user_org_id", nullable = false, length = 64)
    private String userOrgId;

    @Column(nullable = false)
    private String password;

    @PrePersist
    void onCreate() {
        if (createdOn == null) {
            createdOn = Instant.now();
        }
    }

    public boolean isPaytrix() {
        return userOrgId == null || PAYTRIX.equals(userOrgId);
    }

    /** The corporate or vendor id. Empty for a Paytrix person. */
    public String organisationId() {
        return isPaytrix() ? null : userOrgId;
    }

    /** Screens and the login token still use the previous role names. */
    public LegacyRole getRole() {
        return userType == null ? null : new LegacyRole(UserTypes.legacyRole(userType));
    }

    public UserStatus getStatus() {
        return active ? UserStatus.ACTIVE : UserStatus.SUSPENDED;
    }

    public void setStatus(UserStatus status) {
        this.active = status != UserStatus.SUSPENDED;
    }

    public String getId() {
        return userId;
    }

    public String getUserCode() {
        return userId;
    }

    public String getFullName() {
        return name;
    }

    public void setFullName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phone;
    }

    public void setPhoneNumber(String phone) {
        this.phone = phone;
    }

    public enum UserType {
        ORG_SUPERADMIN,
        ORG_ADMIN,
        ORG_USER,
        CORP_ADMIN,
        CORP_USER,
        VENDOR_ADMIN,
        VENDOR_USER,
        ORG_MAKER,
        ORG_CHECKER,
        ORG_SALES
    }

    public enum UserStatus {
        ACTIVE, SUSPENDED
    }

    public static final class LegacyRole {
        private final String name;

        public LegacyRole(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
