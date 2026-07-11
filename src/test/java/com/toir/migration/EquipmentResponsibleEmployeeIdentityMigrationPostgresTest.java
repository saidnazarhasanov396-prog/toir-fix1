package com.toir.migration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class EquipmentResponsibleEmployeeIdentityMigrationPostgresTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260711_3__equipment_responsible_employee_identity.sql");
    private static final OffsetDateTime ORIGINAL_TIME =
            OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC);

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeEach
    void createLegacySchema() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS equipment CASCADE");
            statement.execute("DROP TABLE IF EXISTS hr_employees CASCADE");
            statement.execute("DROP TABLE IF EXISTS users CASCADE");
            statement.execute("""
                    CREATE TABLE users (
                        id uuid PRIMARY KEY
                    );
                    CREATE TABLE hr_employees (
                        id uuid PRIMARY KEY,
                        user_id uuid
                    );
                    CREATE TABLE equipment (
                        id uuid PRIMARY KEY,
                        responsible_id uuid,
                        created_at timestamptz NOT NULL,
                        updated_at timestamptz NOT NULL,
                        created_by_id uuid,
                        updated_by_id uuid
                    )
                    """);
        }
    }

    @Test
    void preservesValidEmployeeIdentityEvenWhenTheSameIdIsAlsoAUser() throws Exception {
        UUID sharedId = UUID.randomUUID();
        UUID otherEmployeeId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        insertUser(sharedId);
        insertEmployee(sharedId, null);
        insertEmployee(otherEmployeeId, sharedId);
        insertEquipment(equipmentId, sharedId, null, null);

        executeMigration();

        assertThat(responsibleId(equipmentId)).isEqualTo(sharedId);
        assertThat(updatedAt(equipmentId)).isEqualTo(ORIGINAL_TIME);
    }

    @Test
    void convertsUniqueLegacyUserAndLeavesActorColumnsUnchanged() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();
        UUID updatedBy = UUID.randomUUID();
        insertUser(userId);
        insertEmployee(employeeId, userId);
        insertEquipment(equipmentId, userId, createdBy, updatedBy);

        executeMigration();

        assertThat(responsibleId(equipmentId)).isEqualTo(employeeId);
        assertThat(updatedAt(equipmentId)).isAfter(ORIGINAL_TIME);
        assertThat(actorId(equipmentId, "created_by_id")).isEqualTo(createdBy);
        assertThat(actorId(equipmentId, "updated_by_id")).isEqualTo(updatedBy);
    }

    @Test
    void preservesNullResponsibleIdentity() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        insertEquipment(equipmentId, null, null, null);

        executeMigration();

        assertThat(responsibleId(equipmentId)).isNull();
        assertThat(updatedAt(equipmentId)).isEqualTo(ORIGINAL_TIME);
    }

    @Test
    void userWithoutEmployeeFailsBeforeAnyBackfill() throws Exception {
        UUID convertibleUserId = UUID.randomUUID();
        UUID convertibleEmployeeId = UUID.randomUUID();
        UUID convertibleEquipmentId = UUID.randomUUID();
        UUID userOnlyId = UUID.randomUUID();
        insertUser(convertibleUserId);
        insertEmployee(convertibleEmployeeId, convertibleUserId);
        insertEquipment(convertibleEquipmentId, convertibleUserId, null, null);
        insertUser(userOnlyId);
        insertEquipment(UUID.randomUUID(), userOnlyId, null, null);

        assertMigrationFailsWith("UNRESOLVED");

        assertThat(responsibleId(convertibleEquipmentId)).isEqualTo(convertibleUserId);
        assertThat(identityConstraintExists()).isFalse();
        assertThat(identityIndexExists()).isFalse();
    }

    @Test
    void userWithMultipleEmployeesFailsAsAmbiguous() throws Exception {
        UUID userId = UUID.randomUUID();
        insertUser(userId);
        insertEmployee(UUID.randomUUID(), userId);
        insertEmployee(UUID.randomUUID(), userId);
        insertEquipment(UUID.randomUUID(), userId, null, null);

        assertMigrationFailsWith("AMBIGUOUS");
    }

    @Test
    void orphanIdentityFailsAsUnresolved() throws Exception {
        insertEquipment(UUID.randomUUID(), UUID.randomUUID(), null, null);

        assertMigrationFailsWith("UNRESOLVED");
    }

    @Test
    void validatedForeignKeyRejectsUserIdentityAfterMigration() throws Exception {
        UUID userId = UUID.randomUUID();
        insertUser(userId);
        executeMigration();

        assertThatThrownBy(() -> insertEquipment(UUID.randomUUID(), userId, null, null))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("fk_equipment_responsible_employee");
    }

    @Test
    void cleanSchemaMigrationCreatesValidatedConstraintIndexAndComment() throws Exception {
        executeMigration();

        assertThat(identityConstraintExists()).isTrue();
        assertThat(identityConstraintValidated()).isTrue();
        assertThat(identityIndexExists()).isTrue();
        assertThat(columnComment()).isEqualTo(
                "Operational responsible Employee identity; references hr_employees.id");
    }

    private void assertMigrationFailsWith(String classification) {
        assertThatThrownBy(this::executeMigration)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("equipment responsible identity migration blocked")
                .hasMessageContaining(classification);
    }

    private void executeMigration() throws Exception {
        String sql = Files.readString(MIGRATION);
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                // PostgreSQL JDBC accepts a complete multi-command script. Executing it whole preserves
                // dollar-quoted DO blocks and avoids unsafe semicolon splitting.
                statement.execute(sql);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void insertUser(UUID id) throws Exception {
        executeUpdate("INSERT INTO users (id) VALUES (?)", id);
    }

    private void insertEmployee(UUID id, UUID userId) throws Exception {
        executeUpdate("INSERT INTO hr_employees (id, user_id) VALUES (?, ?)", id, userId);
    }

    private void insertEquipment(UUID id, UUID responsibleId, UUID createdBy, UUID updatedBy) throws Exception {
        executeUpdate("""
                INSERT INTO equipment (
                    id, responsible_id, created_at, updated_at, created_by_id, updated_by_id
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, id, responsibleId, ORIGINAL_TIME, ORIGINAL_TIME, createdBy, updatedBy);
    }

    private void executeUpdate(String sql, Object... parameters) throws Exception {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            statement.executeUpdate();
        }
    }

    private UUID responsibleId(UUID equipmentId) throws Exception {
        return uuidValue("SELECT responsible_id FROM equipment WHERE id = ?", equipmentId);
    }

    private UUID actorId(UUID equipmentId, String column) throws Exception {
        return uuidValue("SELECT " + column + " FROM equipment WHERE id = ?", equipmentId);
    }

    private UUID uuidValue(String sql, UUID equipmentId) throws Exception {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, equipmentId);
            var result = statement.executeQuery();
            assertThat(result.next()).isTrue();
            return result.getObject(1, UUID.class);
        }
    }

    private OffsetDateTime updatedAt(UUID equipmentId) throws Exception {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT updated_at FROM equipment WHERE id = ?")) {
            statement.setObject(1, equipmentId);
            var result = statement.executeQuery();
            assertThat(result.next()).isTrue();
            return result.getObject(1, OffsetDateTime.class);
        }
    }

    private boolean identityConstraintExists() throws Exception {
        return booleanValue("""
                SELECT EXISTS (
                    SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'equipment'::regclass
                      AND conname = 'fk_equipment_responsible_employee'
                )
                """);
    }

    private boolean identityConstraintValidated() throws Exception {
        return booleanValue("""
                SELECT convalidated FROM pg_constraint
                WHERE conrelid = 'equipment'::regclass
                  AND conname = 'fk_equipment_responsible_employee'
                """);
    }

    private boolean identityIndexExists() throws Exception {
        return booleanValue("SELECT to_regclass('idx_equipment_responsible_id') IS NOT NULL");
    }

    private boolean booleanValue(String sql) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            var result = statement.executeQuery(sql);
            assertThat(result.next()).isTrue();
            return result.getBoolean(1);
        }
    }

    private String columnComment() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            var result = statement.executeQuery(
                    "SELECT col_description('equipment'::regclass, "
                            + "(SELECT attnum FROM pg_attribute "
                            + "WHERE attrelid = 'equipment'::regclass AND attname = 'responsible_id'))");
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
