package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryTransactionsMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260613_5__inventory_transactions.sql");

    @Test
    void migrationCreatesInventoryTransactionsTable() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS inventory_transactions");
        assertThat(sql).contains("type varchar(30) NOT NULL");
        assertThat(sql).contains("warehouse_id uuid NOT NULL");
        assertThat(sql).contains("spare_part_id uuid NOT NULL");
        assertThat(sql).contains("quantity numeric(19,4) NOT NULL");
        assertThat(sql).contains("unit_price numeric(19,2)");
        assertThat(sql).contains("total_amount numeric(19,2)");
        assertThat(sql).contains("transaction_date date NOT NULL");
        assertThat(sql).contains("created_by uuid");
    }

    @Test
    void migrationCreatesRequiredIndexes() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("idx_inventory_tx_type");
        assertThat(sql).contains("idx_inventory_tx_date");
        assertThat(sql).contains("idx_inventory_tx_spare_part");
        assertThat(sql).contains("idx_inventory_tx_warehouse");
        assertThat(sql).contains("idx_inventory_tx_work_order");
        assertThat(sql).contains("idx_inventory_tx_department");
        assertThat(sql).contains("idx_inventory_tx_responsible");
        assertThat(sql).contains("idx_inventory_tx_taken_by");
    }
}
