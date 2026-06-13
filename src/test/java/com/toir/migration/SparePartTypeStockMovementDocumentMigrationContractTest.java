package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SparePartTypeStockMovementDocumentMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260613_7__spare_part_type_and_movement_documents.sql");

    @Test
    void migrationAddsSparePartTypeAndMovementDocumentFields() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("ALTER TABLE spare_parts");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS type varchar(50)");
        assertThat(sql).contains("UPDATE spare_parts");
        assertThat(sql).contains("SET type = 'OTHER'");
        assertThat(sql).contains("ALTER TABLE stock_movements");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS unit varchar(30)");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS unit_price numeric(19,2)");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS total_amount numeric(19,2)");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS responsible_person_id uuid");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS taken_by_id uuid");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS supplier_name varchar(255)");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS movement_date date");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS comment text");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS department_id uuid");
    }

    @Test
    void migrationAddsMovementHistoryIndexes() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("CREATE INDEX IF NOT EXISTS idx_stock_movements_type");
        assertThat(sql).contains("CREATE INDEX IF NOT EXISTS idx_stock_movements_spare_part_id");
        assertThat(sql).contains("CREATE INDEX IF NOT EXISTS idx_stock_movements_warehouse_id");
        assertThat(sql).contains("CREATE INDEX IF NOT EXISTS idx_stock_movements_movement_date");
        assertThat(sql).contains("CREATE INDEX IF NOT EXISTS idx_stock_movements_responsible_person_id");
        assertThat(sql).contains("CREATE INDEX IF NOT EXISTS idx_stock_movements_work_order_id");
    }
}
