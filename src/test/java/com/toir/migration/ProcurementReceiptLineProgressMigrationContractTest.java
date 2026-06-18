package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProcurementReceiptLineProgressMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260618_2__procurement_receipt_line_progress_and_stock_sources.sql");

    @Test
    void migrationAddsProcurementReceiptProgressFields() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS received_quantity");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS remaining_quantity");
        assertThat(sql).contains("PARTIALLY_RECEIVED");
    }

    @Test
    void migrationAddsStockMovementSourceTraceFields() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS source_type");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS source_id");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS source_line_id");
        assertThat(sql).contains("idx_stock_movements_source");
        assertThat(sql).contains("idx_stock_movements_source_line");
    }
}
