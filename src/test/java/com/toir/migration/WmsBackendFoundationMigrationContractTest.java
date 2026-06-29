package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WmsBackendFoundationMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260627_12__wms_backend_foundation.sql"
    );

    @Test
    void migrationExtendsWarehouseBinAndStockQualitySchema() throws Exception {
        String sql = migrationSql();

        assertThat(sql).contains("ALTER TABLE warehouse_bins");
        assertThat(sql).contains("quality_zone_type varchar(32) NOT NULL DEFAULT 'STORAGE'");
        assertThat(sql).contains("allow_mixed_spare_parts boolean NOT NULL DEFAULT true");
        assertThat(sql).contains("ALTER TABLE warehouse_stock_balances");
        assertThat(sql).contains("stock_status varchar(32) NOT NULL DEFAULT 'AVAILABLE'");
        assertThat(sql).contains("quality_hold_reason varchar(255)");
        assertThat(sql).contains("idx_warehouse_stock_balances_status");
        assertThat(sql).contains("uq_warehouse_stock_balances_identity_key");
    }

    @Test
    void migrationAddsDocumentCoordinateColumnsToExistingOperationalTables() throws Exception {
        String sql = migrationSql();

        assertThat(sql).contains("ALTER TABLE warehouse_stock_ledger_metadata");
        assertThat(sql).contains("CREATE OR REPLACE VIEW stock_movements AS");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS source_document_no varchar(100)");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS destination_bin_id uuid");
        assertThat(sql).contains("ALTER TABLE inventory_transactions");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS source_bin_id uuid");
        assertThat(sql).contains("ALTER TABLE repair_material_usages");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS lot_number varchar(100)");
        assertThat(sql).contains("ALTER TABLE warehouse_equipment_items");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS receipt_document_no varchar(100)");
    }

    @Test
    void migrationCreatesTaskCountReturnAndWriteoffTables() throws Exception {
        String sql = migrationSql();

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS warehouse_tasks");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS warehouse_task_lines");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS inventory_count_sessions");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS inventory_count_lines");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS repair_material_returns");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS warehouse_writeoff_requests");
        assertThat(sql).contains("idx_warehouse_tasks_status");
        assertThat(sql).contains("idx_inventory_count_lines_session");
        assertThat(sql).contains("idx_warehouse_writeoff_requests_status");
    }

    @Test
    void migrationExtendsAttachmentTargetsAndRolePermissionsWithoutPermissionTables() throws Exception {
        String sql = migrationSql();

        assertThat(sql).contains("attachment_target_type_check");
        assertThat(sql).contains("PURCHASE_ORDER");
        assertThat(sql).contains("INVENTORY_COUNT_SESSION");
        assertThat(sql).contains("WAREHOUSE_TASK");
        assertThat(sql).contains("WAREHOUSE_BIN");
        assertThat(sql).contains("WAREHOUSE_WRITEOFF");
        assertThat(sql).contains("append_wms_backend_foundation_role_permissions");
        assertThat(sql).contains("append_wms_backend_foundation_role_permissions('STOREKEEPER', ARRAY[");
        assertThat(sql).contains("WAREHOUSE_TASK_EXECUTE");
        assertThat(sql).doesNotContain("CREATE TABLE permissions");
        assertThat(sql).doesNotContain("INSERT INTO permissions");
        assertThat(sql).doesNotContain("CREATE TABLE role_permissions");
        assertThat(sql).doesNotContain("FROM role_permissions");
        assertThat(sql).doesNotContain("JOIN role_permissions");
    }

    private String migrationSql() throws Exception {
        assertThat(Files.exists(MIGRATION)).isTrue();
        return Files.readString(MIGRATION);
    }
}
