package com.b2bfintech.platform.repository;

import com.b2bfintech.platform.model.UserDirectoryEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Login directory. The {@code tenantSchema} argument on insert is ignored; every user shares one schema. */
@Repository
public class UserDirectoryRepository {

    private static final RowMapper<UserDirectoryEntry> MAPPER = (rs, rowNum) -> new UserDirectoryEntry(
            UUID.fromString(rs.getString("id")),
            rs.getString("phone_number"),
            rs.getString("user_code"),
            UUID.fromString(rs.getString("user_id")),
            null,
            rs.getString("role"),
            rs.getString("display_name"),
            rs.getString("client_id") == null ? null : UUID.fromString(rs.getString("client_id")),
            rs.getString("branch_id") == null ? null : UUID.fromString(rs.getString("branch_id")),
            rs.getString("status"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getTimestamp("created_at").toInstant()
    );

    private final JdbcTemplate jdbcTemplate;

    public UserDirectoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<UserDirectoryEntry> findByRole(String role) {
        return jdbcTemplate.query("SELECT * FROM user_directory WHERE role = ?", MAPPER, role);
    }

    public Optional<UserDirectoryEntry> findByPhoneNumber(String phoneNumber) {
        return jdbcTemplate.query(
                "SELECT * FROM user_directory WHERE phone_number = ?",
                MAPPER, phoneNumber
        ).stream().findFirst();
    }

    public boolean existsByRole(String role) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_directory WHERE role = ?",
                Integer.class, role
        );
        return count != null && count > 0;
    }

    public boolean existsByPhoneNumber(String phoneNumber) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_directory WHERE phone_number = ?",
                Integer.class, phoneNumber
        );
        return count != null && count > 0;
    }

    public boolean existsByEmail(String email) {
        if (email == null || email.isBlank()) return false;
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_directory WHERE email = ?",
                Integer.class, email
        );
        return count != null && count > 0;
    }

    /** Same as {@link #existsByEmail}, ignoring the given user - for "is this email taken by someone else" checks on edit. */
    public boolean existsByEmailExcludingUser(String email, UUID excludeUserId) {
        if (email == null || email.isBlank()) return false;
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_directory WHERE email = ? AND user_id <> ?",
                Integer.class, email, excludeUserId
        );
        return count != null && count > 0;
    }

    /** Password login (&sect;8.1 extension) resolves by either identifier - phone or email. */
    public Optional<UserDirectoryEntry> findByPhoneNumberOrEmail(String identifier) {
        return jdbcTemplate.query(
                "SELECT * FROM user_directory WHERE phone_number = ? OR email = ?",
                MAPPER, identifier, identifier
        ).stream().findFirst();
    }

    public void insert(UUID userId, String phoneNumber, String tenantSchema, String role, String displayName, String userCode) {
        insert(userId, phoneNumber, tenantSchema, role, displayName, null, null, null, userCode);
    }

    public void insert(UUID userId, String phoneNumber, String tenantSchema, String role, String displayName,
                        UUID clientId, String userCode) {
        insert(userId, phoneNumber, tenantSchema, role, displayName, clientId, null, null, userCode);
    }

    /** @param email optional alternate login identifier. @param passwordHash optional BCrypt hash enabling password login. */
    public void insert(UUID userId, String phoneNumber, String tenantSchema, String role, String displayName,
                        UUID clientId, String email, String passwordHash, String userCode) {
        jdbcTemplate.update(
                "INSERT INTO user_directory (phone_number, user_id, role, display_name, client_id, email, password_hash, user_code, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', now(), now())",
                phoneNumber, userId, role, displayName, clientId, email, passwordHash, userCode
        );
    }

    /** Best-effort cleanup used by provisioning rollback if a later step fails. */
    public void deleteByPhoneNumber(String phoneNumber) {
        jdbcTemplate.update("DELETE FROM user_directory WHERE phone_number = ?", phoneNumber);
    }

    /**
     * Keeps this the source of truth for login in sync with a tenant-side profile edit - role and
     * display name are baked into the JWT at login time, so a stale row here would let an edited
     * user's old role/name keep showing up in their token until this is called.
     */
    public void updateProfile(UUID userId, String displayName, String role, String email) {
        jdbcTemplate.update(
                "UPDATE user_directory SET display_name = ?, role = ?, email = ?, updated_at = now() WHERE user_id = ?",
                displayName, role, email, userId
        );
    }

    /**
     * Suspending/reactivating a user must flip this row too - {@code loginWithPassword}
     * gates on this table's status, not the tenant schema's app_user status, so skipping
     * this update would leave a "suspended" user still able to log in.
     */
    public void updateStatus(UUID userId, String status) {
        jdbcTemplate.update(
                "UPDATE user_directory SET status = ?, updated_at = now() WHERE user_id = ?",
                status, userId
        );
    }

    public void updatePasswordHash(UUID userId, String passwordHash) {
        jdbcTemplate.update(
                "UPDATE user_directory SET password_hash = ?, updated_at = now() WHERE user_id = ?",
                passwordHash, userId
        );
    }

    public boolean hasPassword(UUID userId) {
        String hash = jdbcTemplate.query(
                "SELECT password_hash FROM user_directory WHERE user_id = ?",
                rs -> rs.next() ? rs.getString("password_hash") : null,
                userId);
        return hash != null && !hash.isBlank();
    }

    public void updateBranchId(UUID userId, UUID branchId) {
        jdbcTemplate.update(
                "UPDATE user_directory SET branch_id = ?, updated_at = now() WHERE user_id = ?",
                branchId, userId
        );
    }
}
