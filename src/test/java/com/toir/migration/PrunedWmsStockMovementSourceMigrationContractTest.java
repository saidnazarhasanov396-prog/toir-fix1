package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PrunedWmsStockMovementSourceMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260704_2__normalize_pruned_wms_stock_movement_sources.sql");

    @Test
    void migrationNormalizesRemovedWarehouseWriteoffStockMovementSource() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("UPDATE stock_movements");
        assertThat(sql).contains("source_type = 'MANUAL'");
        assertThat(sql).contains("WHERE source_type = 'WAREHOUSE_WRITEOFF'");
        assertThat(sql).doesNotContain("source_id = NULL");
        assertThat(sql).doesNotContain("source_line_id = NULL");
    }
}
