package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceOperationDurationNullHardeningMigrationContractTest {

    @Test
    void migrationBackfillsNullDurationsAndRestoresNotNullConstraint() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260616_7__maintenance_operation_duration_null_hardening.sql"
        )).toLowerCase();

        assertThat(sql).contains("update maintenance_operations");
        assertThat(sql).contains("duration_hours = 0");
        assertThat(sql).contains("where duration_hours is null");
        assertThat(sql).contains("alter column duration_hours set not null");
    }
}
