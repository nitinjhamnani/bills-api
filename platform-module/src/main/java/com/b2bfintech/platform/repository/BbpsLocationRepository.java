package com.b2bfintech.platform.repository;

import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.b2bfintech.platform.model.BbpsLocation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BbpsLocationRepository {

    private static final RowMapper<BbpsLocation> MAPPER = (rs, rowNum) -> new BbpsLocation(
            UUID.fromString(rs.getString("id")),
            rs.getString("provider_code"),
            rs.getString("location_id"),
            rs.getString("name"),
            rs.getString("abbreviation"),
            rs.getBoolean("active"),
            rs.getTimestamp("synced_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public BbpsLocationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BbpsLocation> findAllActive() {
        return jdbcTemplate.query(
                "SELECT * FROM platform.bbps_location WHERE active = TRUE ORDER BY name", MAPPER);
    }

    public Optional<BbpsLocation> findActiveByLocationId(String locationId) {
        return jdbcTemplate.query(
                "SELECT * FROM platform.bbps_location WHERE location_id = ? AND active = TRUE",
                MAPPER, locationId).stream().findFirst();
    }

    public void deactivateAll(String providerCode) {
        jdbcTemplate.update("UPDATE platform.bbps_location SET active = FALSE WHERE provider_code = ?", providerCode);
    }

    public void upsert(String providerCode, BbpsProvider.ProviderLocation location) {
        jdbcTemplate.update("""
                INSERT INTO platform.bbps_location
                    (id, provider_code, location_id, name, abbreviation, active, synced_at)
                VALUES (?, ?, ?, ?, ?, TRUE, ?)
                ON CONFLICT (provider_code, location_id) DO UPDATE SET
                    name = EXCLUDED.name,
                    abbreviation = EXCLUDED.abbreviation,
                    active = TRUE,
                    synced_at = EXCLUDED.synced_at
                """,
                UUID.randomUUID(), providerCode, location.locationId(), location.name(),
                location.abbreviation(), Timestamp.from(Instant.now()));
    }
}
