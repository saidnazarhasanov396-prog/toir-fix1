package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StockMovementFilesMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260615_3__stock_movement_files.sql");

    @Test
    void migrationCreatesStockMovementFilesTableAndIndexes() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS stock_movement_files");
        assertThat(sql).contains("stock_movement_id uuid NOT NULL");
        assertThat(sql).contains("file_id uuid NOT NULL");
        assertThat(sql).contains("sort_order integer NOT NULL DEFAULT 0");
        assertThat(sql).contains("REFERENCES stock_movements (id)");
        assertThat(sql).contains("REFERENCES uploaded_files (id)");
        assertThat(sql).contains("CONSTRAINT uq_stock_movement_files_file UNIQUE (file_id)");
        assertThat(sql).contains("CONSTRAINT uq_stock_movement_files_sort UNIQUE (stock_movement_id, sort_order)");
        assertThat(sql).contains("CREATE INDEX IF NOT EXISTS idx_stock_movement_files_movement_id");
        assertThat(sql).contains("CREATE INDEX IF NOT EXISTS idx_stock_movement_files_file_id");
    }
}
