package com.trustvault.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Pre-JPA database schema migrator.
 * Runs directly on DataSource initialization before Hibernate/JPA builds its metamodel.
 * Ensures the persistent schema (e.g. H2 file database or PostgreSQL) is always synchronized
 * with required columns and constraints.
 */
@Component
public class DatabaseSchemaMigrator implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaMigrator.class);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource dataSource) {
            migrateSchema(dataSource);
        }
        return bean;
    }

    private void migrateSchema(DataSource dataSource) {
        log.info("Checking and verifying database schema synchronization...");
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // Check if users table exists
            boolean usersTableExists = false;
            try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if ("users".equalsIgnoreCase(tableName)) {
                        usersTableExists = true;
                        break;
                    }
                }
            }

            if (usersTableExists) {
                // Check if status column exists in users table
                boolean statusColumnExists = false;
                try (ResultSet rs = metaData.getColumns(null, null, "%", "%")) {
                    while (rs.next()) {
                        String tableName = rs.getString("TABLE_NAME");
                        String colName = rs.getString("COLUMN_NAME");
                        if ("users".equalsIgnoreCase(tableName) && "status".equalsIgnoreCase(colName)) {
                            statusColumnExists = true;
                            break;
                        }
                    }
                }

                if (!statusColumnExists) {
                    log.info("Applying schema migration: adding 'status' column to 'users' table...");
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("ALTER TABLE users ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'");
                        stmt.execute("UPDATE users SET status = 'ACTIVE' WHERE status IS NULL");
                        log.info("Successfully added 'status' column to 'users' table.");
                    }
                } else {
                    log.info("Database schema check passed: 'status' column already present in 'users' table.");
                }

                // Ensure index on status exists
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_status ON users(status)");
                } catch (Exception e) {
                    log.debug("Index idx_users_status already exists or could not be created: {}", e.getMessage());
                }
            } else {
                log.info("Database schema check: 'users' table not yet created. Will be initialized by startup/Flyway/Hibernate.");
            }

            // Check if document_permissions table exists and has expires_at column
            boolean permTableExists = false;
            try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if ("document_permissions".equalsIgnoreCase(tableName)) {
                        permTableExists = true;
                        break;
                    }
                }
            }

            if (permTableExists) {
                boolean expiresAtColumnExists = false;
                try (ResultSet rs = metaData.getColumns(null, null, "%", "%")) {
                    while (rs.next()) {
                        String tableName = rs.getString("TABLE_NAME");
                        String colName = rs.getString("COLUMN_NAME");
                        if ("document_permissions".equalsIgnoreCase(tableName) && "expires_at".equalsIgnoreCase(colName)) {
                            expiresAtColumnExists = true;
                            break;
                        }
                    }
                }

                if (!expiresAtColumnExists) {
                    log.info("Applying schema migration: adding 'expires_at' column to 'document_permissions' table...");
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("ALTER TABLE document_permissions ADD COLUMN expires_at TIMESTAMP");
                        log.info("Successfully added 'expires_at' column to 'document_permissions' table.");
                    }
                }
            }

            // Cleanup any outdated enum check constraints that may reject new enum values (e.g. REGISTRATION_REQUEST)
            cleanupLegacyCheckConstraints(conn);

        } catch (Exception ex) {
            log.error("Database schema migration check failed: {}", ex.getMessage(), ex);
        }
    }

    private void cleanupLegacyCheckConstraints(Connection conn) {
        // Dynamically find and drop any check constraints on AUDIT_EVENTS, USERS, and DOCUMENT_PERMISSIONS enum columns
        try (Statement stmt = conn.createStatement()) {
            String query = "SELECT CONSTRAINT_NAME, TABLE_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE CONSTRAINT_TYPE = 'CHECK' AND UPPER(TABLE_NAME) IN ('AUDIT_EVENTS', 'USERS', 'DOCUMENT_PERMISSIONS')";
            List<String[]> toDrop = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    String cName = rs.getString("CONSTRAINT_NAME");
                    String tName = rs.getString("TABLE_NAME");
                    toDrop.add(new String[]{tName, cName});
                }
            }

            for (String[] item : toDrop) {
                String table = item[0];
                String constraint = item[1];
                try {
                    stmt.execute("ALTER TABLE \"" + table + "\" DROP CONSTRAINT \"" + constraint + "\"");
                    log.info("Dropped legacy check constraint '{}' from table '{}'", constraint, table);
                } catch (Exception e) {
                    log.debug("Could not drop constraint {} from {}: {}", constraint, table, e.getMessage());
                }
            }
        } catch (Exception ex) {
            log.debug("Check constraint dynamic cleanup skipped (non-H2 or not supported): {}", ex.getMessage());
        }
    }
}
