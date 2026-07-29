package com.toir.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class AnnualMaintenanceCalculationSnapshotMigrationPostgresTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V20260728_5__annual_maintenance_calculation_snapshots.sql"
    );

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrateV5OnCanonicalMinimalSchema() throws Exception {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE ppr_plans (id UUID PRIMARY KEY)");
            statement.execute("CREATE TABLE equipment (id UUID PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE maintenance_regulations (id UUID PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE equipment_maintenance_rules (id UUID PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE maintenance_templates (id UUID PRIMARY KEY)");
            statement.execute("CREATE TABLE departments (id UUID PRIMARY KEY)");
            statement.execute("""
                    CREATE TABLE ppr_tasks (
                        id UUID PRIMARY KEY,
                        is_deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);
        }

        Path migrationDirectory = Files.createTempDirectory("annual-maintenance-v5-");
        Files.copy(MIGRATION, migrationDirectory.resolve(MIGRATION.getFileName()));
        Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword())
                .locations("filesystem:" + migrationDirectory.toAbsolutePath())
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(true)
                .load()
                .migrate();
    }

    @Test
    void duplicatePlanRevisionSourceKeyIsRejected() throws Exception {
        try (Connection connection = connection()) {
            UUID planId = insertPlan(connection);
            String sourceItemKey = "a".repeat(64);
            insertSnapshot(connection, planId, sourceItemKey);

            assertConstraintViolation(
                    () -> insertSnapshot(connection, planId, sourceItemKey),
                    "uq_ms_calc_items_plan_revision_source_key"
            );
        }
    }

    @Test
    void oneActiveTaskPerSourceAllowsLegacyNullTraceability() throws Exception {
        try (Connection connection = connection()) {
            UUID planId = insertPlan(connection);
            UUID sourceItemId = insertSnapshot(connection, planId, "b".repeat(64));
            insertTask(connection, sourceItemId, false);

            assertConstraintViolation(
                    () -> insertTask(connection, sourceItemId, false),
                    "uq_ppr_tasks_active_source_calculation_item"
            );
            assertThatCode(() -> {
                insertTask(connection, null, false);
                insertTask(connection, null, false);
            }).doesNotThrowAnyException();
        }
    }

    @Test
    void snapshotHistoryRestrictsSourceDeletionAndDirectMutation() throws Exception {
        try (Connection connection = connection()) {
            UUID planId = insertPlan(connection);
            UUID sourceItemId = insertSnapshot(connection, planId, "c".repeat(64));

            assertConstraintViolation(
                    () -> deletePlan(connection, planId),
                    "fk_ms_calc_items_plan"
            );
            assertConstraintViolation(
                    () -> updateSnapshotTitle(connection, sourceItemId),
                    "maintenance_schedule_calculation_items are immutable"
            );
            assertConstraintViolation(
                    () -> deleteSnapshot(connection, sourceItemId),
                    "maintenance_schedule_calculation_items are immutable"
            );
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
    }

    private static UUID insertPlan(Connection connection) throws SQLException {
        UUID planId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ppr_plans (id) VALUES (?)")) {
            statement.setObject(1, planId);
            statement.executeUpdate();
        }
        return planId;
    }

    private static UUID insertSnapshot(
            Connection connection,
            UUID planId,
            String sourceItemKey) throws SQLException {
        UUID sourceItemId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO maintenance_schedule_calculation_items (
                    id, plan_id, calculation_revision,
                    source_item_key, source_item_key_version,
                    cycle_ordinal, planned_date, task_title_snapshot,
                    created_at, updated_at, is_deleted
                )
                VALUES (?, ?, 1, ?, 1, 1, DATE '2026-01-01',
                        'Preventive maintenance', now(), now(), false)
                """)) {
            statement.setObject(1, sourceItemId);
            statement.setObject(2, planId);
            statement.setString(3, sourceItemKey);
            statement.executeUpdate();
        }
        return sourceItemId;
    }

    private static void insertTask(
            Connection connection,
            UUID sourceItemId,
            boolean deleted) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ppr_tasks (id, is_deleted, source_calculation_item_id)
                VALUES (?, ?, ?)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setBoolean(2, deleted);
            statement.setObject(3, sourceItemId);
            statement.executeUpdate();
        }
    }

    private static void deletePlan(Connection connection, UUID planId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM ppr_plans WHERE id = ?")) {
            statement.setObject(1, planId);
            statement.executeUpdate();
        }
    }

    private static void updateSnapshotTitle(Connection connection, UUID sourceItemId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE maintenance_schedule_calculation_items
                SET task_title_snapshot = 'Changed'
                WHERE id = ?
                """)) {
            statement.setObject(1, sourceItemId);
            statement.executeUpdate();
        }
    }

    private static void deleteSnapshot(Connection connection, UUID sourceItemId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM maintenance_schedule_calculation_items WHERE id = ?")) {
            statement.setObject(1, sourceItemId);
            statement.executeUpdate();
        }
    }

    private static void assertConstraintViolation(
            SqlOperation operation,
            String expectedMessage) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(rootMessage(error))
                        .contains(expectedMessage));
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    @FunctionalInterface
    private interface SqlOperation {
        void run() throws SQLException;
    }
}
