package com.b2bfintech.platform.repository;

import com.b2bfintech.platform.model.Organisation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class OrganisationRepository {

    private static final RowMapper<Organisation> MAPPER = (rs, rowNum) -> new Organisation(
            UUID.fromString(rs.getString("id")),
            rs.getString("organisation_code"),
            rs.getString("organisation_name"),
            rs.getString("tenant_schema"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant()
    );

    private final JdbcTemplate jdbcTemplate;

    public OrganisationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsByOrganisationCode(String organisationCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform.organisation WHERE organisation_code = ?", Integer.class, organisationCode);
        return count != null && count > 0;
    }

    public UUID insert(String organisationCode, String organisationName, String tenantSchema) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO platform.organisation (organisation_code, organisation_name, tenant_schema, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'ACTIVE', now(), now()) RETURNING id",
                UUID.class, organisationCode, organisationName, tenantSchema
        );
    }

    public void deleteByOrganisationCode(String organisationCode) {
        jdbcTemplate.update("DELETE FROM platform.organisation WHERE organisation_code = ?", organisationCode);
    }

    public List<Organisation> findAll() {
        return jdbcTemplate.query("SELECT * FROM platform.organisation ORDER BY created_at DESC", MAPPER);
    }

    public Optional<Organisation> findByTenantSchema(String tenantSchema) {
        return jdbcTemplate.query(
                "SELECT * FROM platform.organisation WHERE tenant_schema = ?", MAPPER, tenantSchema
        ).stream().findFirst();
    }

    public Optional<Organisation> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT * FROM platform.organisation WHERE id = ?", MAPPER, id
        ).stream().findFirst();
    }

    public void updateStatus(UUID id, String status) {
        jdbcTemplate.update(
                "UPDATE platform.organisation SET status = ?, updated_at = now() WHERE id = ?",
                status, id
        );
    }
}
