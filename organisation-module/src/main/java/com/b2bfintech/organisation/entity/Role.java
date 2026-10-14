package com.b2bfintech.organisation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A tenant-schema role (ORGANISATION_ADMIN, MAKER, CHECKER, VIEWER, CLIENT_ADMIN, CLIENT_OPERATOR -
 * seeded per tenant by the Flyway tenant migration). Table name is unqualified: Hibernate
 * resolves it against whichever tenant schema {@link com.b2bfintech.common.tenant.TenantContext}
 * points the current connection at.
 */
@Entity
@Table(name = "role")
@Getter
@Setter
@NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;
}
