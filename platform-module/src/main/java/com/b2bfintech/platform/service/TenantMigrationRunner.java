package com.b2bfintech.platform.service;

import com.b2bfintech.platform.model.Organisation;
import com.b2bfintech.platform.repository.OrganisationRepository;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Runs at every app startup, after the platform-schema Flyway migration: re-applies the tenant
 * Flyway migration set (db/migration/tenant) against every already-onboarded organisation's schema,
 * so a new release's tenant migrations reach every existing tenant without a separate rollout
 * step. New tenants get the full set at onboarding time via {@link OrganisationProvisioningService}.
 */
@Component
@Order(Integer.MAX_VALUE - 1)
public class TenantMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantMigrationRunner.class);

    private final DataSource dataSource;
    private final OrganisationRepository organisationRepository;

    public TenantMigrationRunner(DataSource dataSource, OrganisationRepository organisationRepository) {
        this.dataSource = dataSource;
        this.organisationRepository = organisationRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Organisation organisation : organisationRepository.findAll()) {
            try {
                Flyway.configure()
                        .dataSource(dataSource)
                        .schemas(organisation.tenantSchema())
                        .locations("classpath:db/migration/tenant")
                        .baselineOnMigrate(false)
                        .load()
                        .migrate();
            } catch (RuntimeException ex) {
                log.error("Tenant migration failed for organisation '{}' (schema '{}') - it may be running " +
                        "on an older schema version until this is fixed and the app restarts.",
                        organisation.organisationCode(), organisation.tenantSchema(), ex);
            }
        }
        log.info("Tenant migration check complete for {} organisations", organisationRepository.findAll().size());
    }
}
