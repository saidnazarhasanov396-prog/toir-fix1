package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentCommissioningMigrationContractTest {

    @Test
    void migrationCreatesActApprovalAndEquipmentOutContracts() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260622_1__equipment_commissioning_acts.sql"));

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS equipment_commissioning_acts")
                .contains("REFERENCES hr_employees(id)")
                .doesNotContain("REFERENCES employees(id)")
                .contains("'EQUIPMENT_OUT'")
                .contains("'EQUIPMENT_COMMISSIONING'")
                .contains("EQUIPMENT_COMMISSIONING_APPROVE");
    }
}
