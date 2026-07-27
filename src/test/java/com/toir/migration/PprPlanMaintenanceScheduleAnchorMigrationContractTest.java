package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PprPlanMaintenanceScheduleAnchorMigrationContractTest {

    @Test
    void migrationAddsNullableConstrainedAnchorMode() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V20260727_1__ppr_plan_maintenance_schedule_anchor.sql"
        );

        assertThat(Files.exists(migration)).isTrue();
        String sql = Files.readString(migration).toLowerCase();

        assertThat(sql).contains("alter table ppr_plans");
        assertThat(sql).contains("add column if not exists anchor_mode");
        assertThat(sql).contains("'current'");
        assertThat(sql).contains("'reset_to_plan_start'");
        assertThat(sql).doesNotContain("anchor_mode varchar(32) not null");
    }
}
