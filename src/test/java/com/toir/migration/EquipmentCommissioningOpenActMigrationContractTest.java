package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentCommissioningOpenActMigrationContractTest {

    @Test
    void migrationAllowsOnlyOneOpenActPerEquipment() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260622_4__one_open_equipment_commissioning_act.sql"));

        assertThat(sql)
                .contains("CREATE UNIQUE INDEX IF NOT EXISTS uq_equipment_commissioning_open_equipment")
                .contains("ON equipment_commissioning_acts (equipment_id)")
                .contains("is_deleted = false")
                .contains("status IN ('DRAFT', 'PENDING_APPROVAL')");
    }
}
