package com.toir.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

@Testcontainers
class LifecycleApprovalTemplateUniquenessMigrationPostgresTest {

    private static final String PREVIOUS_VERSION = "20260715.1";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeEach
    void migrateContainerToPreviousMigration() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .target(PREVIOUS_VERSION)
                .validateOnMigrate(true)
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
        executeUpdate("""
                DELETE FROM approval_templates
                WHERE target_type IN ('REPAIR_CAMPAIGN', 'PLANNED_SHUTDOWN')
                """);
    }

    @Test
    void cleanMigrationSucceedsAndDoesNotRewriteRows() throws Exception {
        UUID templateId = insertTemplate("REPAIR_CLEAN", "REPAIR_CAMPAIGN", "APPROVE", true, false);
        Map<String, Object> before = templateRow(templateId);

        migrateLatest();

        assertThat(templateRow(templateId)).isEqualTo(before);
    }

    @Test
    void nullAndApproveActionsShareOneNormalizedKey() throws Exception {
        executeUpdate("ALTER TABLE approval_templates ALTER COLUMN action_type DROP NOT NULL");
        insertTemplate("REPAIR_NULL", "REPAIR_CAMPAIGN", null, true, false);
        insertTemplate("REPAIR_APPROVE", "REPAIR_CAMPAIGN", "APPROVE", true, false);

        Throwable thrown = catchThrowable(this::migrateLatest);

        assertThat(thrown)
                .isNotNull()
                .hasStackTraceContaining("MULTIPLE_ACTIVE_TEMPLATES")
                .hasStackTraceContaining("target=REPAIR_CAMPAIGN")
                .hasStackTraceContaining("action=APPROVE")
                .hasStackTraceContaining("count=2");
    }

    @Test
    void duplicateActiveLifecycleTemplatesFailWithTargetActionAndCount() throws Exception {
        insertTemplate("REPAIR_DUPLICATE_1", "REPAIR_CAMPAIGN", "APPROVE", true, false);
        insertTemplate("REPAIR_DUPLICATE_2", "REPAIR_CAMPAIGN", "APPROVE", true, false);

        assertThat(catchThrowable(this::migrateLatest))
                .isNotNull()
                .hasStackTraceContaining(
                        "MULTIPLE_ACTIVE_TEMPLATES target=REPAIR_CAMPAIGN action=APPROVE count=2");
    }

    @Test
    void inactiveAndDeletedDuplicatesAreAllowed() throws Exception {
        for (String target : new String[]{"REPAIR_CAMPAIGN", "PLANNED_SHUTDOWN"}) {
            insertTemplate(target + "_ACTIVE", target, "APPROVE", true, false);
            insertTemplate(target + "_INACTIVE", target, "APPROVE", false, false);
            insertTemplate(target + "_DELETED", target, "APPROVE", true, true);
        }

        migrateLatest();

        assertThat(count("""
                SELECT count(*) FROM approval_templates
                WHERE target_type IN ('REPAIR_CAMPAIGN', 'PLANNED_SHUTDOWN')
                """)).isEqualTo(6);
    }

    @Test
    void unrelatedTargetsAreUnaffected() throws Exception {
        UUID first = insertTemplate("WORK_ORDER_DUPLICATE_1", "WORK_ORDER", "APPROVE", true, false);
        UUID second = insertTemplate("WORK_ORDER_DUPLICATE_2", "WORK_ORDER", "APPROVE", true, false);

        migrateLatest();

        assertThat(count("""
                SELECT count(*) FROM approval_templates
                WHERE id IN ('%s', '%s') AND active = true
                """.formatted(first, second))).isEqualTo(2);
    }

    @Test
    void duplicateActivationIsRejectedAfterMigration() throws Exception {
        insertTemplate("SHUTDOWN_ACTIVE", "PLANNED_SHUTDOWN", "APPROVE", true, false);
        UUID inactive = insertTemplate(
                "SHUTDOWN_INACTIVE", "PLANNED_SHUTDOWN", "APPROVE", false, false);
        migrateLatest();

        assertThatThrownBy(() -> activate(inactive))
                .isInstanceOfSatisfying(SQLException.class,
                        exception -> assertThat(exception.getSQLState()).isEqualTo("23505"));
    }

    @Test
    void concurrentActivationLeavesAtMostOneActiveTemplate() throws Exception {
        UUID first = insertTemplate("REPAIR_CONCURRENT_1", "REPAIR_CAMPAIGN", "APPROVE", false, false);
        UUID second = insertTemplate("REPAIR_CONCURRENT_2", "REPAIR_CAMPAIGN", "APPROVE", false, false);
        migrateLatest();
        CountDownLatch connectionsReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<ActivationResult> firstActivation =
                    executor.submit(() -> activateAndCommit(first, connectionsReady, start));
            Future<ActivationResult> secondActivation =
                    executor.submit(() -> activateAndCommit(second, connectionsReady, start));
            assertThat(connectionsReady.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ActivationResult> results = List.of(
                    firstActivation.get(15, TimeUnit.SECONDS),
                    secondActivation.get(15, TimeUnit.SECONDS));
            assertThat(results.stream().filter(ActivationResult::committed).count()).isEqualTo(1);
            assertThat(results.stream()
                    .filter(result -> !result.committed())
                    .map(ActivationResult::sqlState)
                    .toList()).containsExactly("23505");
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(count("""
                SELECT count(*) FROM approval_templates
                WHERE target_type = 'REPAIR_CAMPAIGN'
                  AND COALESCE(action_type, 'APPROVE') = 'APPROVE'
                  AND active = true
                  AND is_deleted = false
                """)).isEqualTo(1);
    }

    private void migrateLatest() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(true)
                .load()
                .migrate();
    }

    private UUID insertTemplate(
            String code, String target, String action, boolean active, boolean deleted) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = testConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO approval_templates (
                    id, code, name, target_type, action_type, active, is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, id);
            statement.setString(2, code);
            statement.setString(3, code + " name");
            statement.setString(4, target);
            if (action == null) {
                statement.setNull(5, Types.VARCHAR);
            } else {
                statement.setString(5, action);
            }
            statement.setBoolean(6, active);
            statement.setBoolean(7, deleted);
            statement.executeUpdate();
        }
        return id;
    }

    private void activate(UUID id) throws Exception {
        try (Connection connection = testConnection(); PreparedStatement statement = connection.prepareStatement(
                "UPDATE approval_templates SET active = true WHERE id = ?")) {
            statement.setObject(1, id);
            statement.executeUpdate();
        }
    }

    private ActivationResult activateAndCommit(
            UUID id, CountDownLatch connectionsReady, CountDownLatch start) throws Exception {
        try (Connection connection = testConnection(); PreparedStatement statement = connection.prepareStatement(
                "UPDATE approval_templates SET active = true WHERE id = ?")) {
            connection.setAutoCommit(false);
            try (Statement settings = connection.createStatement()) {
                settings.execute("SET LOCAL lock_timeout = '5s'");
                settings.execute("SET LOCAL statement_timeout = '10s'");
            }
            statement.setObject(1, id);
            connectionsReady.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent activation start gate");
            }
            try {
                statement.executeUpdate();
                connection.commit();
                return new ActivationResult(true, null);
            } catch (SQLException exception) {
                connection.rollback();
                if (!"23505".equals(exception.getSQLState())) {
                    throw exception;
                }
                return new ActivationResult(false, exception.getSQLState());
            }
        }
    }

    private Map<String, Object> templateRow(UUID id) throws Exception {
        try (Connection connection = testConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM approval_templates WHERE id = ?")) {
            statement.setObject(1, id);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                ResultSetMetaData metadata = result.getMetaData();
                Map<String, Object> row = new LinkedHashMap<>();
                for (int column = 1; column <= metadata.getColumnCount(); column++) {
                    row.put(metadata.getColumnName(column), result.getObject(column));
                }
                assertThat(result.next()).isFalse();
                return row;
            }
        }
    }

    private long count(String query) throws Exception {
        try (Connection connection = testConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(query)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private void executeUpdate(String sql) throws Exception {
        try (Connection connection = testConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private Connection testConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private record ActivationResult(boolean committed, String sqlState) {}
}
