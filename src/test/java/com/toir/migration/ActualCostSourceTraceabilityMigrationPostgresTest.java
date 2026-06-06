package com.toir.migration;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class ActualCostSourceTraceabilityMigrationPostgresTest {

    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/V20260606_1__actual_cost_source_traceability.sql");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void legacyDuplicateWorkOrderCostsMigrateSuccessfully() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        )) {
            createLegacyActualCostsTable(connection);
            insertLegacyWorkOrderCost(connection, workOrderId, "PENDING");
            insertLegacyWorkOrderCost(connection, workOrderId, "APPROVED");

            executeMigration(connection);

            assertThat(countRows(connection, """
                    SELECT count(*)
                    FROM actual_costs
                    WHERE source_type = 'WORK_ORDER'
                      AND source_id = ?
                    """, workOrderId)).isEqualTo(2);
        }
    }

    private void createLegacyActualCostsTable(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                CREATE TABLE actual_costs (
                    id uuid PRIMARY KEY,
                    work_order_id uuid,
                    repair_request_id uuid,
                    contractor_work_id uuid,
                    status varchar(32) NOT NULL,
                    is_deleted boolean NOT NULL DEFAULT false
                )
                """)) {
            statement.executeUpdate();
        }
    }

    private void insertLegacyWorkOrderCost(Connection connection, UUID workOrderId, String status) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO actual_costs (id, work_order_id, status, is_deleted)
                VALUES (?, ?, ?, false)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, workOrderId);
            statement.setString(3, status);
            statement.executeUpdate();
        }
    }

    private long countRows(Connection connection, String sql, UUID sourceId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sourceId);
            var rs = statement.executeQuery();
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        }
    }

    private void executeMigration(Connection connection) throws Exception {
        String migration = Files.readString(MIGRATION);
        for (String statement : migration.split(";")) {
            if (statement.isBlank()) {
                continue;
            }
            try (PreparedStatement preparedStatement = connection.prepareStatement(statement)) {
                preparedStatement.execute();
            }
        }
    }
}
