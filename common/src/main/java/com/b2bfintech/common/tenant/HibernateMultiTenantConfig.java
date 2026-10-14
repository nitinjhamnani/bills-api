package com.b2bfintech.common.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Wires the schema-per-tenant {@link SchemaMultiTenantConnectionProvider} and
 * {@link TenantIdentifierResolver} into Hibernate. All tenant schemas share the same entity
 * mappings (unqualified table names) - only the JDBC connection's search_path differs per
 * request, so a single {@code EntityManagerFactory} serves every tenant.
 */
@Configuration
public class HibernateMultiTenantConfig {

    @Bean
    public TenantIdentifierResolver tenantIdentifierResolver() {
        return new TenantIdentifierResolver();
    }

    @Bean
    public SchemaMultiTenantConnectionProvider multiTenantConnectionProvider(DataSource dataSource) {
        return new SchemaMultiTenantConnectionProvider(dataSource);
    }

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer(
            SchemaMultiTenantConnectionProvider connectionProvider,
            TenantIdentifierResolver identifierResolver) {
        return hibernateProperties -> {
            hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, identifierResolver);
        };
    }
}
