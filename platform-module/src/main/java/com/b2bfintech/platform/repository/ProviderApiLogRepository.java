package com.b2bfintech.platform.repository;

import com.b2bfintech.common.spi.bbps.ProviderCallRecorder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class ProviderApiLogRepository implements ProviderCallRecorder {

    private final JdbcTemplate jdbcTemplate;

    public ProviderApiLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(ProviderCallRecord record) {
        jdbcTemplate.update("""
                INSERT INTO platform.provider_api_log
                    (id, provider, operation, tenant, bill_id, client_ref_id, http_status, response_code,
                     duration_ms, masked_request, truncated_response)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), record.provider(), record.operation(), record.tenant(), record.billId(),
                record.clientRefId(), record.httpStatus(), record.responseCode(), record.durationMs(),
                record.maskedRequest(), record.truncatedResponse());
    }
}
