package com.b2bfintech.platform.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Platform-schema equivalent of {@code common.audit.AuditService}, for actions that happen before any tenant exists. */
@Repository
public class PlatformAuditRepository {

    private final JdbcTemplate jdbcTemplate;

    public PlatformAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(String actorUserId, String actorRole, String action, String entityType, String entityId, String details) {
        jdbcTemplate.update(
                "INSERT INTO platform.audit_log (actor_user_id, actor_role, action, entity_type, entity_id, details, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, now())",
                actorUserId, actorRole, action, entityType, entityId, details
        );
    }
}
