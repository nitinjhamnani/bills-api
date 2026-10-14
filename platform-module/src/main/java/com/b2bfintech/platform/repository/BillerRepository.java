package com.b2bfintech.platform.repository;

import com.b2bfintech.platform.model.Biller;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BillerRepository {

    private static final RowMapper<Biller> MAPPER = (rs, rowNum) -> new Biller(
            UUID.fromString(rs.getString("id")),
            rs.getString("biller_id"),
            rs.getString("biller_name"),
            rs.getString("category"),
            rs.getString("state"),
            rs.getBoolean("active")
    );

    private final JdbcTemplate jdbcTemplate;

    public BillerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Biller> findAllActive() {
        return jdbcTemplate.query(
                "SELECT * FROM platform.biller WHERE active = TRUE ORDER BY category, biller_name", MAPPER);
    }

    public List<Biller> findActiveByCategory(String category) {
        return jdbcTemplate.query(
                "SELECT * FROM platform.biller WHERE active = TRUE AND category = ? ORDER BY biller_name",
                MAPPER, category);
    }

    public Optional<Biller> findByBillerId(String billerId) {
        return jdbcTemplate.query(
                "SELECT * FROM platform.biller WHERE biller_id = ?", MAPPER, billerId
        ).stream().findFirst();
    }
}
