package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentProcurementRequestMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260618_8__equipment_procurement_requests.sql");

    @Test
    void migrationAddsProcurementRequestTypeAndSourceTraceFields() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS type varchar(32) NOT NULL DEFAULT 'SPARE_PART'");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS source_defect_id uuid");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS source_defect_title varchar(255)");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS source_ppr_task_id uuid");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS source_ppr_task_title varchar(255)");
        assertThat(sql).contains("idx_procurement_requests_source_defect");
        assertThat(sql).contains("idx_procurement_requests_source_ppr_task");
        assertThat(sql).contains("idx_procurement_requests_type");
    }

    @Test
    void migrationAddsEquipmentLineAndMovementTraceFields() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("ALTER TABLE procurement_request_lines");
        assertThat(sql).contains("ALTER COLUMN spare_part_id DROP NOT NULL");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS equipment_type_id uuid");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS equipment_type_name varchar(255)");
        assertThat(sql).contains("chk_procurement_request_lines_item_xor");
        assertThat(sql).contains("ALTER TABLE stock_movements");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS equipment_type_id uuid");
        assertThat(sql).contains("'EQUIPMENT_IN'");
        assertThat(sql).contains("chk_stock_movements_item_xor");
        assertThat(sql).contains("idx_stock_movements_equipment_type_id");
    }

    @Test
    void migrationAddsEquipmentProcurementTraceFields() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("ALTER TABLE equipment");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS procurement_request_id uuid");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS procurement_request_line_id uuid");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS procurement_stock_movement_id uuid");
        assertThat(sql).contains("idx_equipment_procurement_request");
        assertThat(sql).contains("idx_equipment_procurement_line");
        assertThat(sql).contains("idx_equipment_procurement_stock_movement");
    }
}
