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
import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class AnnualMaintenanceCalculationSnapshotMigrationPostgresTest {

    private static final Path SNAPSHOT_MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V20260728_5__annual_maintenance_calculation_snapshots.sql"
    );
    private static final Path CORRECTIVE_MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V20260728_5_1__annual_maintenance_calculation_snapshot_integrity.sql"
    );

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrateSnapshotMigrationsOnCanonicalMinimalSchema() throws Exception {
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
                        plan_id UUID NOT NULL REFERENCES ppr_plans(id),
                        is_deleted BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);
        }

        Path migrationDirectory = Files.createTempDirectory("annual-maintenance-v5-");
        Files.copy(
                SNAPSHOT_MIGRATION,
                migrationDirectory.resolve(SNAPSHOT_MIGRATION.getFileName()));
        Files.copy(
                CORRECTIVE_MIGRATION,
                migrationDirectory.resolve(CORRECTIVE_MIGRATION.getFileName()));
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
    void oneTaskPerSourceIsIndependentOfSoftDeleteAndAllowsNullTraceability()
            throws Exception {
        try (Connection connection = connection()) {
            UUID planId = insertPlan(connection);
            UUID sourceItemId = insertSnapshot(connection, planId, "b".repeat(64));
            insertTask(connection, planId, sourceItemId, true);

            assertConstraintViolation(
                    () -> insertTask(connection, planId, sourceItemId, false),
                    "uq_ppr_tasks_source_calculation_item"
            );
            assertThatCode(() -> {
                insertTask(connection, planId, null, false);
                insertTask(connection, planId, null, false);
            }).doesNotThrowAnyException();
        }
    }

    @Test
    void taskCannotReferenceSnapshotFromAnotherPlan() throws Exception {
        try (Connection connection = connection()) {
            UUID snapshotPlanId = insertPlan(connection);
            UUID taskPlanId = insertPlan(connection);
            UUID sourceItemId = insertSnapshot(
                    connection, snapshotPlanId, "c".repeat(64));

            assertConstraintViolation(
                    () -> insertTask(connection, taskPlanId, sourceItemId, false),
                    "fk_ppr_tasks_source_calculation_item_plan"
            );
        }
    }

    @Test
    void nullEquipmentAndMultipleItemsForOneEquipmentMonthArePreserved()
            throws Exception {
        try (Connection connection = connection()) {
            UUID planId = insertPlan(connection);
            UUID equipmentId = insertEquipment(connection);
            insertSnapshot(
                    connection, planId, 1L, "d".repeat(64), equipmentId,
                    LocalDate.of(2026, 1, 1), 1L);
            insertSnapshot(
                    connection, planId, 1L, "e".repeat(64), equipmentId,
                    LocalDate.of(2026, 1, 31), 2L);
            insertSnapshot(
                    connection, planId, 1L, "f".repeat(64), null,
                    LocalDate.of(2026, 1, 15), 3L);

            assertThat(countSnapshots(connection, planId, 1L, equipmentId))
                    .isEqualTo(2L);
            assertThat(countSnapshots(connection, planId, 1L, null))
                    .isEqualTo(1L);
        }
    }

    @Test
    void sameSourceKeyInAnotherRevisionDoesNotOverwriteHistory() throws Exception {
        try (Connection connection = connection()) {
            UUID planId = insertPlan(connection);
            String sourceItemKey = "1".repeat(64);
            insertSnapshot(
                    connection, planId, 1L, sourceItemKey, null,
                    LocalDate.of(2026, 2, 1), 1L);
            insertSnapshot(
                    connection, planId, 2L, sourceItemKey, null,
                    LocalDate.of(2026, 2, 1), 1L);

            assertThat(countSnapshots(connection, planId, 1L, null))
                    .isEqualTo(1L);
            assertThat(countSnapshots(connection, planId, 2L, null))
                    .isEqualTo(1L);
        }
    }

    @Test
    void snapshotHistoryRestrictsSourceDeletionAndDirectMutation() throws Exception {
        try (Connection connection = connection()) {
            UUID planId = insertPlan(connection);
            UUID sourceItemId = insertSnapshot(connection, planId, "2".repeat(64));

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

    private static UUID insertEquipment(Connection connection) throws SQLException {
        UUID equipmentId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO equipment (id) VALUES (?)")) {
            statement.setObject(1, equipmentId);
            statement.executeUpdate();
        }
        return equipmentId;
    }

    private static UUID insertSnapshot(
            Connection connection,
            UUID planId,
            String sourceItemKey) throws SQLException {
        return insertSnapshot(
                connection,
                planId,
                1L,
                sourceItemKey,
                null,
                LocalDate.of(2026, 1, 1),
                1L
        );
    }

    private static UUID insertSnapshot(
            Connection connection,
            UUID planId,
            long calculationRevision,
            String sourceItemKey,
            UUID equipmentId,
            LocalDate plannedDate,
            long cycleOrdinal) throws SQLException {
        UUID sourceItemId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO maintenance_schedule_calculation_items (
                    id, plan_id, calculation_revision,
                    source_item_key, source_item_key_version,
                    equipment_id, cycle_ordinal, planned_date, task_title_snapshot,
                    created_at, updated_at, is_deleted
                )
                VALUES (?, ?, ?, ?, 1, ?, ?, ?,
                        'Preventive maintenance', now(), now(), false)
                """)) {
            statement.setObject(1, sourceItemId);
            statement.setObject(2, planId);
            statement.setLong(3, calculationRevision);
            statement.setString(4, sourceItemKey);
            statement.setObject(5, equipmentId);
            statement.setLong(6, cycleOrdinal);
            statement.setObject(7, plannedDate);
            statement.executeUpdate();
        }
        return sourceItemId;
    }

    private static void insertTask(
            Connection connection,
            UUID planId,
            UUID sourceItemId,
            boolean deleted) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ppr_tasks (
                    id, plan_id, is_deleted, source_calculation_item_id
                )
                VALUES (?, ?, ?, ?)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, planId);
            statement.setBoolean(3, deleted);
            statement.setObject(4, sourceItemId);
            statement.executeUpdate();
        }
    }

    private static long countSnapshots(
            Connection connection,
            UUID planId,
            long calculationRevision,
            UUID equipmentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*)
                FROM maintenance_schedule_calculation_items
                WHERE plan_id = ?
                  AND calculation_revision = ?
                  AND equipment_id IS NOT DISTINCT FROM ?
                """)) {
            statement.setObject(1, planId);
            statement.setLong(2, calculationRevision);
            statement.setObject(3, equipmentId);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
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
