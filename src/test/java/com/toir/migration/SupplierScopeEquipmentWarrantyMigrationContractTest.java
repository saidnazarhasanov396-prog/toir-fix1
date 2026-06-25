package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierScopeEquipmentWarrantyMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260625_2__supplier_scope_equipment_warranty.sql");

    @Test
    void migrationAddsSupplierScopeAndEquipmentSupplierLinks() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("ALTER TABLE suppliers");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS supplier_type varchar(32) NOT NULL DEFAULT 'BOTH'");
        assertThat(sql).contains("ck_suppliers_supplier_type");
        assertThat(sql).contains("supplier_type IN ('EQUIPMENT', 'SPARE_PART', 'BOTH')");
        assertThat(sql).contains("ALTER TABLE equipment");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS supplier_id uuid");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS warranty_supplier_id uuid");
        assertThat(sql).contains("fk_equipment_supplier");
        assertThat(sql).contains("fk_equipment_warranty_supplier");
        assertThat(sql).contains("idx_equipment_supplier_id");
        assertThat(sql).contains("idx_equipment_warranty_supplier_id");
    }

    @Test
    void migrationAddsRepairRequestWarrantySupplierSnapshot() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("ALTER TABLE repair_requests");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS warranty_supplier_id uuid");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS warranty_supplier_name varchar(255)");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS warranty_supplier_contact_person varchar(255)");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS warranty_supplier_phone varchar(255)");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS warranty_supplier_email varchar(255)");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS warranty_start_date_at_creation date");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS warranty_end_date_at_creation date");
        assertThat(sql).contains("idx_repair_requests_warranty_supplier_id");
        assertThat(sql).contains("fk_repair_requests_warranty_supplier");
    }
}
