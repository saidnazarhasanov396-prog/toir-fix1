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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class WarehouseStockConstraintMigrationTest {

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
    void warehouseStockConstraintsRejectInvalidDirectWrites() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        )) {
            UUID sparePartId = seedSparePart(connection);

            assertConstraintRejects(connection, sparePartId, -1, 0,
                    "chk_warehouse_stocks_quantity_nonnegative");
            assertConstraintRejects(connection, sparePartId, 5, -1,
                    "chk_warehouse_stocks_reserved_qty_nonnegative");
            assertConstraintRejects(connection, sparePartId, 5, 6,
                    "chk_warehouse_stocks_reserved_not_over_quantity");
        }
    }

    @Test
    void parallelReservationAttemptsCannotOversellLockedStockRow() throws Exception {
        UUID sparePartId;
        UUID stockId;
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        )) {
            sparePartId = seedSparePart(connection);
            stockId = seedStock(connection, sparePartId, 5, 0);
        }

        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> reserveWithRowLock(stockId, 4, start));
            Future<Boolean> second = executor.submit(() -> reserveWithRowLock(stockId, 4, start));
            start.countDown();

            List<Boolean> results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertThat(results).containsExactlyInAnyOrder(true, false);
            try (Connection connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(),
                    POSTGRES.getUsername(),
                    POSTGRES.getPassword()
            );
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT quantity, reserved_qty FROM warehouse_stocks WHERE id = ?"
                 )) {
                statement.setObject(1, stockId);
                var rs = statement.executeQuery();
                assertThat(rs.next()).isTrue();
                assertThat(rs.getDouble("quantity")).isEqualTo(5);
                assertThat(rs.getDouble("reserved_qty")).isEqualTo(4);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private UUID seedSparePart(Connection connection) throws SQLException {
        UUID sparePartId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO spare_part_types (
                    id, created_at, updated_at, code, name, default_unit, active
                )
                VALUES (?, now(), now(), ?, 'Constraint test type', 'pcs', true)
                """)) {
            statement.setObject(1, typeId);
            statement.setString(2, "SPT-" + typeId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO spare_parts (
                    id, created_at, updated_at, is_deleted,
                    code, name, unit, min_stock, kind, type_id
                )
                VALUES (?, now(), now(), false, ?, 'Constraint test part', 'pcs', 0, 'SPARE_PART', ?)
                """)) {
            statement.setObject(1, sparePartId);
            statement.setString(2, "SP-" + sparePartId);
            statement.setObject(3, typeId);
            statement.executeUpdate();
        }
        return sparePartId;
    }

    private void assertConstraintRejects(Connection connection,
                                         UUID sparePartId,
                                         double quantity,
                                         double reservedQty,
                                         String constraintName) {
        assertThatThrownBy(() -> insertStock(connection, sparePartId, quantity, reservedQty))
                .isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(rootMessage(error)).contains(constraintName));
    }

    private void insertStock(Connection connection,
                             UUID sparePartId,
                             double quantity,
                             double reservedQty) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO warehouse_stocks (
                    id, created_at, updated_at, is_deleted,
                    warehouse_id, spare_part_id, quantity, reserved_qty, min_qty
                )
                VALUES (?, now(), now(), false, ?, ?, ?, ?, 0)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, UUID.randomUUID());
            statement.setObject(3, sparePartId);
            statement.setDouble(4, quantity);
            statement.setDouble(5, reservedQty);
            statement.executeUpdate();
        }
    }

    private UUID seedStock(Connection connection,
                           UUID sparePartId,
                           double quantity,
                           double reservedQty) throws SQLException {
        UUID stockId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO warehouse_stocks (
                    id, created_at, updated_at, is_deleted,
                    warehouse_id, spare_part_id, quantity, reserved_qty, min_qty
                )
                VALUES (?, now(), now(), false, ?, ?, ?, ?, 0)
                """)) {
            statement.setObject(1, stockId);
            statement.setObject(2, UUID.randomUUID());
            statement.setObject(3, sparePartId);
            statement.setDouble(4, quantity);
            statement.setDouble(5, reservedQty);
            statement.executeUpdate();
        }
        return stockId;
    }

    private boolean reserveWithRowLock(UUID stockId, double quantity, CountDownLatch start) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        )) {
            connection.setAutoCommit(false);
            try (PreparedStatement select = connection.prepareStatement("""
                    SELECT quantity, reserved_qty
                    FROM warehouse_stocks
                    WHERE id = ?
                    FOR UPDATE
                    """)) {
                select.setObject(1, stockId);
                var rs = select.executeQuery();
                if (!rs.next()) {
                    connection.rollback();
                    return false;
                }
                double onHand = rs.getDouble("quantity");
                double reserved = rs.getDouble("reserved_qty");
                if (onHand - reserved < quantity) {
                    connection.rollback();
                    return false;
                }
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE warehouse_stocks
                        SET reserved_qty = reserved_qty + ?, updated_at = now()
                        WHERE id = ?
                        """)) {
                    update.setDouble(1, quantity);
                    update.setObject(2, stockId);
                    update.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
