package com.toir.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

@Testcontainers(disabledWithoutDocker = true)
class EquipmentLifecycleDatasetExportAuditMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(true)
                .load()
                .migrate();
    }

    @Test
    void auditConstraintAcceptsEquipmentLifecycleDatasetExportModule() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        )) {
            assertThatCode(() -> insertAuditLog(connection))
                    .doesNotThrowAnyException();
        }
    }

    private void insertAuditLog(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_logs (
                    id, created_at, is_deleted, module, action, entity_type, entity_id, message
                )
                VALUES (?, now(), false, 'EQUIPMENT_LIFECYCLE_DATASET_EXPORT',
                        'CREATE', 'EquipmentLifecycleDatasetExport', ?, 'created')
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, UUID.randomUUID().toString());
            statement.executeUpdate();
        }
    }
}
