package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseAnalyticsIndexesMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260624_1__warehouse_analytics_indexes.sql");

    @Test
    void migrationAddsWarehouseAnalyticsIndexes() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("idx_wsl_warehouse_spare_posted");
        assertThat(sql).contains("idx_wrl_warehouse_spare_posted");
        assertThat(sql).contains("idx_reservations_warehouse_status_updated");
        assertThat(sql).contains("idx_wsl_metadata_warehouse_type_date");
        assertThat(sql).doesNotContain("ON stock_movements");
    }
}
