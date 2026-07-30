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
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class CounteragentBankDetailsMigrationPostgresTest {

    private static final UUID COMPLETE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PARTIAL_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID EMPTY_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrateAndSeedLegacyRows() throws Exception {
        flyway("20260730.3").migrate();
        try (Connection connection = connection()) {
            insertCounteragent(connection, COMPLETE_ID, "CA-MIG-COMPLETE",
                    " Complete Bank ", " 111 ", " 001 ");
            insertCounteragent(connection, PARTIAL_ID, "CA-MIG-PARTIAL",
                    " Partial Bank ", null, " 002 ");
            insertCounteragent(connection, EMPTY_ID, "CA-MIG-EMPTY",
                    " ", null, "");
        }
        flyway("20260730.4").migrate();
    }

    @Test
    void migrationBackfillsCompleteAndPartialRowsButSkipsEmptyLegacyData() throws Exception {
        try (Connection connection = connection()) {
            assertThat(bankDetail(connection, COMPLETE_ID))
                    .containsExactly("Complete Bank", "111", "001", "true", "0");
            assertThat(bankDetail(connection, PARTIAL_ID))
                    .containsExactly("Partial Bank", "", "002", "true", "0");
            assertThat(bankDetail(connection, EMPTY_ID)).isEmpty();
        }
    }

    @Test
    void databaseGuardsPrimaryAndOrderWhileDeleteSemanticsRemainDistinct() throws Exception {
        try (Connection connection = connection()) {
            assertThatThrownBy(() -> insertBankDetail(connection, PARTIAL_ID, true, 1))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertBankDetail(connection, EMPTY_ID, false, -1))
                    .isInstanceOf(SQLException.class);

            try (PreparedStatement softDelete = connection.prepareStatement(
                    "UPDATE counteragents SET is_deleted = true WHERE id = ?"
            )) {
                softDelete.setObject(1, PARTIAL_ID);
                softDelete.executeUpdate();
            }
            assertThat(bankDetailCount(connection, PARTIAL_ID)).isEqualTo(1);

            try (PreparedStatement physicalDelete = connection.prepareStatement(
                    "DELETE FROM counteragents WHERE id = ?"
            )) {
                physicalDelete.setObject(1, PARTIAL_ID);
                physicalDelete.executeUpdate();
            }
            assertThat(bankDetailCount(connection, PARTIAL_ID)).isZero();
        }
    }

    private static Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .target(target)
                .validateOnMigrate(true)
                .load();
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
    }

    private static void insertCounteragent(
            Connection connection,
            UUID id,
            String code,
            String bankName,
            String bankAccount,
            String mfo
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO counteragents (
                    id, created_at, updated_at, is_deleted, code, name,
                    bank_name, bank_account, mfo, status
                )
                VALUES (?, now(), now(), false, ?, ?, ?, ?, ?, 'ACTIVE')
                """)) {
            statement.setObject(1, id);
            statement.setString(2, code);
            statement.setString(3, code);
            statement.setString(4, bankName);
            statement.setString(5, bankAccount);
            statement.setString(6, mfo);
            statement.executeUpdate();
        }
    }

    private static void insertBankDetail(
            Connection connection,
            UUID counteragentId,
            boolean primary,
            int displayOrder
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO counteragent_bank_details (
                    id, counteragent_id, bank_name, bank_account, mfo, is_primary, display_order
                )
                VALUES (?, ?, 'Direct Bank', '999', '009', ?, ?)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, counteragentId);
            statement.setBoolean(3, primary);
            statement.setInt(4, displayOrder);
            statement.executeUpdate();
        }
    }

    private static List<String> bankDetail(
            Connection connection,
            UUID counteragentId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT bank_name, bank_account, mfo, is_primary, display_order
                FROM counteragent_bank_details
                WHERE counteragent_id = ?
                ORDER BY display_order, id
                """)) {
            statement.setObject(1, counteragentId);
            var result = statement.executeQuery();
            if (!result.next()) {
                return List.of();
            }
            return List.of(
                    result.getString("bank_name"),
                    result.getString("bank_account"),
                    result.getString("mfo"),
                    Boolean.toString(result.getBoolean("is_primary")),
                    Integer.toString(result.getInt("display_order"))
            );
        }
    }

    private static long bankDetailCount(Connection connection, UUID counteragentId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM counteragent_bank_details WHERE counteragent_id = ?"
        )) {
            statement.setObject(1, counteragentId);
            var result = statement.executeQuery();
            result.next();
            return result.getLong(1);
        }
    }
}
