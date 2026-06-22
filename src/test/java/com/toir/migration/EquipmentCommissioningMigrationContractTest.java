package com.toir.migration;

import com.toir.enums.EquipmentCommissioningStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentCommissioningMigrationContractTest {

    @Test
    void migrationCreatesActApprovalAndEquipmentOutContracts() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260622_1__equipment_commissioning_acts.sql"));
        String wmsMovementMigration = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260619_5__replace_legacy_movements_with_wms_view.sql"));

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS equipment_commissioning_acts")
                .contains("REFERENCES hr_employees(id)")
                .doesNotContain("REFERENCES employees(id)")
                .contains("REFERENCES warehouse_stock_ledger_metadata(id)")
                .doesNotContain("REFERENCES stock_movements(id)")
                .doesNotContain("ALTER TABLE stock_movements")
                .contains("'EQUIPMENT_COMMISSIONING'")
                .contains("EQUIPMENT_COMMISSIONING_APPROVE");
        assertThat(wmsMovementMigration)
                .contains("CREATE TABLE warehouse_stock_ledger_metadata")
                .contains("CREATE VIEW stock_movements AS")
                .contains("INSTEAD OF INSERT");

        for (EquipmentCommissioningStatus status : EquipmentCommissioningStatus.values()) {
            assertThat(sql).contains("'" + status.name() + "'");
        }
    }
}
