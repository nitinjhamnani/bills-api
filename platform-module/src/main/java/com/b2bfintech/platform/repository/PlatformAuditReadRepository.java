package com.b2bfintech.platform.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class PlatformAuditReadRepository {

    public record Entry(String actorUserId, String actorRole, String action, String entityType,
                         String entityId, String details, Instant createdAt) {
    }

    private static final RowMapper<Entry> MAPPER = (rs, rowNum) -> new Entry(
            rs.getString("actor_user_id"), rs.getString("actor_role"), rs.getString("action"),
            rs.getString("entity_type"), rs.getString("entity_id"), rs.getString("details"),
            rs.getTimestamp("created_at").toInstant()
    );

    private final JdbcTemplate jdbcTemplate;

    public PlatformAuditReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Entry> findRecent() {
        return jdbcTemplate.query("SELECT * FROM platform.audit_log ORDER BY created_at DESC LIMIT 500", MAPPER);
    }
}
