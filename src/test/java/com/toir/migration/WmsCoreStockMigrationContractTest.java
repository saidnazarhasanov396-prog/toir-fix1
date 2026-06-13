package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WmsCoreStockMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260613_14__wms_core_stock.sql");

    @Test
    void migrationCreatesCoreWarehouseStockTables() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS warehouse_bins");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS warehouse_stock_balances");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS warehouse_stock_ledgers");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS warehouse_reservation_ledgers");
    }

    @Test
    void migrationEnforcesBalanceInvariants() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("chk_warehouse_stock_balance_qty");
        assertThat(sql).contains("qty_on_hand >= 0");
        assertThat(sql).contains("qty_reserved >= 0");
        assertThat(sql).contains("qty_reserved <= qty_on_hand");
    }

    @Test
    void migrationAddsLedgerIdempotencyAndReferenceIndexes() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("uq_warehouse_stock_ledgers_idempotency_key");
        assertThat(sql).contains("idx_warehouse_stock_ledgers_reference");
        assertThat(sql).contains("idx_warehouse_reservation_ledgers_reference");
    }

    @Test
    void migrationBackfillsBalancesFromExistingWarehouseStocks() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("INSERT INTO warehouse_stock_balances");
        assertThat(sql).contains("FROM warehouse_stocks");
        assertThat(sql).contains("qty_on_hand");
        assertThat(sql).contains("qty_reserved");
    }
}
