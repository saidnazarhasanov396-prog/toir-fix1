package com.toir.migration;

import com.toir.enums.EquipmentStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentStatusConstraintMigrationContractTest {

    @Test
    void migrationSynchronizesEquipmentAndHistoryConstraintsWithEnum() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260622_2__sync_equipment_status_constraints.sql"));

        assertThat(sql)
                .contains("DROP CONSTRAINT IF EXISTS equipment_status_check")
                .contains("ADD CONSTRAINT equipment_status_check")
                .contains("DROP CONSTRAINT IF EXISTS equipment_status_history_from_status_check")
                .contains("ADD CONSTRAINT equipment_status_history_from_status_check")
                .contains("DROP CONSTRAINT IF EXISTS equipment_status_history_to_status_check")
                .contains("ADD CONSTRAINT equipment_status_history_to_status_check");

        for (EquipmentStatus status : EquipmentStatus.values()) {
            assertThat(sql).contains("'" + status.name() + "'");
        }
    }
}
