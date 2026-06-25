package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProcurementSupplierWarrantyMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260625_3__procurement_supplier_warranty.sql");

    @Test
    void migrationAddsSupplierToProcurementRequests() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("ALTER TABLE procurement_requests");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS supplier_id uuid");
        assertThat(sql).contains("fk_procurement_requests_supplier");
        assertThat(sql).contains("idx_procurement_requests_supplier_id");
    }

    @Test
    void migrationAddsEquipmentWarrantyFieldsToProcurementLines() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("ALTER TABLE procurement_request_lines");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS has_warranty boolean NOT NULL DEFAULT false");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS warranty_start_date date");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS warranty_end_date date");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS warranty_duration_months integer");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS warranty_supplier_id uuid");
        assertThat(sql).contains("ck_procurement_request_lines_warranty_dates");
        assertThat(sql).contains("ck_procurement_request_lines_warranty_duration");
        assertThat(sql).contains("fk_procurement_request_lines_warranty_supplier");
        assertThat(sql).contains("idx_procurement_request_lines_warranty_supplier_id");
    }
}
