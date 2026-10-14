package com.b2bfintech.common.tenant;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Hibernate schema-per-tenant strategy: every tenant shares one physical {@link DataSource}
 * (one PostgreSQL database), and a session's "tenant" is realised by switching the JDBC
 * connection's current schema (search_path) before handing it to Hibernate, and resetting it
 * back to a safe default before the connection is released to the pool.
 *
 * <p>This is what makes schema-per-tenant multi-tenancy work with a single, pooled
 * {@link DataSource} instead of one connection pool per tenant.
 */
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;

    public SchemaMultiTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        resetSchema(connection);
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setSchema(tenantIdentifier);
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        resetSchema(connection);
        connection.close();
    }

    private void resetSchema(Connection connection) throws SQLException {
        if (!connection.isClosed()) {
            connection.setSchema(TenantContext.DEFAULT_SCHEMA);
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return null;
    }
}
