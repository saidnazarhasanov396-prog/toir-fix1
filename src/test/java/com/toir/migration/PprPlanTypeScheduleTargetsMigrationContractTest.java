package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PprPlanTypeScheduleTargetsMigrationContractTest {

    @Test
    void migrationAddsPprPlanContractColumnsAndTargetsTable() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V20260526_2__ppr_plan_type_schedule_targets.sql"
        );

        assertThat(Files.exists(migration)).isTrue();
        String sql = Files.readString(migration).toLowerCase();

        assertThat(sql).contains("alter table ppr_plans");
        assertThat(sql).contains("add column if not exists ppr_type varchar");
        assertThat(sql).contains("add column if not exists schedule_type varchar");
        assertThat(sql).contains("add column if not exists frequency varchar");
        assertThat(sql).contains("add column if not exists interval_hours bigint");
        assertThat(sql).contains("add column if not exists scope_type varchar");

        assertThat(sql).contains("create table if not exists ppr_plan_targets");
        assertThat(sql).contains("plan_id uuid not null");
        assertThat(sql).contains("target_type varchar");
        assertThat(sql).contains("equipment_id uuid");
        assertThat(sql).contains("equipment_type_id uuid");
        assertThat(sql).contains("references ppr_plans(id)");
        assertThat(sql).contains("references equipment(id)");
        assertThat(sql).contains("references equipment_types(id)");
        assertThat(sql).contains("uq_ppr_plan_targets_active_equipment");
        assertThat(sql).contains("uq_ppr_plan_targets_active_equipment_type");

        assertThat(sql).doesNotContain("ppr_type varchar(64) not null");
        assertThat(sql).doesNotContain("schedule_type varchar(64) not null");
        assertThat(sql).doesNotContain("scope_type varchar(64) not null");
    }
}
