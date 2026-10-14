package com.b2bfintech.platform.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.tenant.TenantContext;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.service.TenantUserService;
import com.b2bfintech.platform.model.Organisation;
import com.b2bfintech.platform.repository.OrganisationRepository;
import com.b2bfintech.platform.repository.PlatformAuditRepository;
import com.b2bfintech.platform.repository.UserDirectoryRepository;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.regex.Pattern;

/**
 * Onboards a new organisation (tenant): creates its dedicated PostgreSQL schema, runs the tenant
 * Flyway migration against it, seeds default RBAC roles (via that migration), creates the first
 * Organisation Admin user in the new schema, and registers the phone-&gt;tenant mapping in
 * {@code platform.user_directory} so that admin can log in. See implementation plan &sect;2.
 *
 * <p>Steps 3-5 below are not wrapped in one cross-schema database transaction (JPA-on-tenant-
 * schema and JdbcTemplate-on-platform-schema acquire connections through different paths under
 * Hibernate's multi-tenant routing, so a single {@code @Transactional} would not actually be
 * atomic across both). Failure after the organisation registry row is inserted triggers a best-effort
 * compensating delete of that row; a partially-created tenant schema is left in place (harmless -
 * it's simply not referenced by any {@code platform.organisation} row) for manual/administrative
 * cleanup rather than auto-dropped.
 */
@Service
public class OrganisationProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(OrganisationProvisioningService.class);
    private static final Pattern ORGANISATION_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{2,29}$");
    private static final String DEFAULT_ADMIN_ROLE = "ORGANISATION_ADMIN";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final OrganisationRepository organisationRepository;
    private final UserDirectoryRepository userDirectoryRepository;
    private final TenantUserService tenantUserService;
    private final PlatformAuditRepository platformAuditRepository;

    public OrganisationProvisioningService(
            DataSource dataSource,
            JdbcTemplate jdbcTemplate,
            OrganisationRepository organisationRepository,
            UserDirectoryRepository userDirectoryRepository,
            TenantUserService tenantUserService,
            PlatformAuditRepository platformAuditRepository) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.organisationRepository = organisationRepository;
        this.userDirectoryRepository = userDirectoryRepository;
        this.tenantUserService = tenantUserService;
        this.platformAuditRepository = platformAuditRepository;
    }

    public Organisation provision(String organisationCode, String organisationName, String adminPhoneNumber, String adminName) {
        validate(organisationCode, organisationName, adminPhoneNumber, adminName);

        String normalizedCode = organisationCode.toLowerCase();
        if (organisationRepository.existsByOrganisationCode(normalizedCode)) {
            throw new BadRequestException("A organisation with code '" + normalizedCode + "' already exists");
        }
        if (userDirectoryRepository.existsByPhoneNumber(adminPhoneNumber)) {
            throw new BadRequestException("Phone number " + adminPhoneNumber + " is already registered to another user");
        }

        String tenantSchema = "tenant_" + normalizedCode;
        organisationRepository.insert(normalizedCode, organisationName, tenantSchema);

        try {
            createSchema(tenantSchema);
            runTenantMigrations(tenantSchema);

            AppUser admin = createAdminUser(tenantSchema, adminPhoneNumber, adminName);

            userDirectoryRepository.insert(admin.getId(), adminPhoneNumber, tenantSchema, DEFAULT_ADMIN_ROLE, adminName);

            log.info("Provisioned organisation '{}' -> schema '{}', admin user {}", normalizedCode, tenantSchema, admin.getId());
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            String actor = (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) ? u.phoneNumber() : "SYSTEM";
            platformAuditRepository.record(actor, "PLATFORM_ADMIN", "ORGANISATION_PROVISIONED", "Organisation", normalizedCode,
                    "tenant schema " + tenantSchema + ", admin " + adminPhoneNumber);
            return organisationRepository.findByTenantSchema(tenantSchema).orElseThrow();
        } catch (RuntimeException ex) {
            log.error("Provisioning failed for organisation '{}' after schema '{}' was created/partially migrated; " +
                    "rolling back the organisation registry row. The schema itself is left in place for manual review.",
                    normalizedCode, tenantSchema, ex);
            organisationRepository.deleteByOrganisationCode(normalizedCode);
            userDirectoryRepository.deleteByPhoneNumber(adminPhoneNumber);
            throw ex;
        }
    }

    private void createSchema(String tenantSchema) {
        // Safe to string-concatenate: tenantSchema is derived from organisationCode, which was
        // already validated against ORGANISATION_CODE_PATTERN above.
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + tenantSchema);
    }

    private void runTenantMigrations(String tenantSchema) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(tenantSchema)
                .locations("classpath:db/migration/tenant")
                .baselineOnMigrate(false)
                .load();
        flyway.migrate();
    }

    private AppUser createAdminUser(String tenantSchema, String adminPhoneNumber, String adminName) {
        TenantContext.setCurrentTenant(tenantSchema);
        try {
            return tenantUserService.createUser(adminPhoneNumber, adminName, DEFAULT_ADMIN_ROLE);
        } finally {
            TenantContext.clear();
        }
    }

    private void validate(String organisationCode, String organisationName, String adminPhoneNumber, String adminName) {
        if (organisationCode == null || !ORGANISATION_CODE_PATTERN.matcher(organisationCode.toLowerCase()).matches()) {
            throw new BadRequestException(
                    "organisationCode must be 3-30 lowercase letters/digits/underscores, starting with a letter");
        }
        if (organisationName == null || organisationName.isBlank()) {
            throw new BadRequestException("organisationName is required");
        }
        if (adminPhoneNumber == null || !adminPhoneNumber.matches("\\d{10}")) {
            throw new BadRequestException("adminPhoneNumber must be a 10-digit Indian mobile number");
        }
        if (adminName == null || adminName.isBlank()) {
            throw new BadRequestException("adminName is required");
        }
    }
}
