package com.b2bfintech.platform.repository;

import com.b2bfintech.platform.model.BillerService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class BillerServiceCatalogRepository {

    private static final RowMapper<BillerService> MAPPER = (rs, rowNum) -> new BillerService(
            UUID.fromString(rs.getString("id")),
            rs.getString("service_code"),
            rs.getString("service_name"),
            rs.getString("category"),
            rs.getBoolean("active")
    );

    private final JdbcTemplate jdbcTemplate;

    public BillerServiceCatalogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BillerService> findAllActive() {
        return jdbcTemplate.query(
                "SELECT * FROM platform.biller_service_catalog WHERE active = TRUE ORDER BY service_name", MAPPER);
    }
}
