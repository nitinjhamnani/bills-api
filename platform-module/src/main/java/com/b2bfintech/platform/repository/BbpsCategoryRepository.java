package com.b2bfintech.platform.repository;

import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.b2bfintech.platform.model.BbpsCategory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BbpsCategoryRepository {

    private static final RowMapper<BbpsCategory> MAPPER = (rs, rowNum) -> new BbpsCategory(
            UUID.fromString(rs.getString("id")),
            rs.getString("provider_code"),
            rs.getString("category_id"),
            rs.getString("name"),
            rs.getString("category_group"),
            rs.getBoolean("active"),
            rs.getTimestamp("synced_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public BbpsCategoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BbpsCategory> findAllActive() {
        return jdbcTemplate.query(
                "SELECT * FROM platform.bbps_category WHERE active = TRUE ORDER BY name", MAPPER);
    }

    public Optional<BbpsCategory> findActiveByCategoryId(String categoryId) {
        return jdbcTemplate.query(
                "SELECT * FROM platform.bbps_category WHERE category_id = ? AND active = TRUE",
                MAPPER, categoryId).stream().findFirst();
    }

    public Optional<BbpsCategory> findActive(String providerCode, String categoryId) {
        return jdbcTemplate.query(
                "SELECT * FROM platform.bbps_category WHERE provider_code = ? AND category_id = ? AND active = TRUE",
                MAPPER, providerCode, categoryId).stream().findFirst();
    }

    public Map<String, String> namesById() {
        Map<String, String> names = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT category_id, name FROM platform.bbps_category", rs -> {
            names.put(rs.getString("category_id"), rs.getString("name"));
        });
        return names;
    }

    public void deactivateAll(String providerCode) {
        jdbcTemplate.update("UPDATE platform.bbps_category SET active = FALSE WHERE provider_code = ?", providerCode);
    }

    public void upsert(String providerCode, BbpsProvider.ProviderCategory category) {
        jdbcTemplate.update("""
                INSERT INTO platform.bbps_category
                    (id, provider_code, category_id, name, category_group, active, synced_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (provider_code, category_id) DO UPDATE SET
                    name = EXCLUDED.name,
                    category_group = EXCLUDED.category_group,
                    active = EXCLUDED.active,
                    synced_at = EXCLUDED.synced_at
                """,
                UUID.randomUUID(), providerCode, category.categoryId(), category.name(), category.group(),
                category.active(), Timestamp.from(java.time.Instant.now()));
    }
}
