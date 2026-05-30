package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FlexibleMaintenancePlanningMigrationContractTest {

    @Test
    void migrationAddsFlexibleMaintenancePolicyColumnsAndCompletionAnchors() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V20260530_1__flexible_maintenance_planning.sql"
        );

        assertThat(Files.exists(migration)).isTrue();
        String sql = Files.readString(migration).toLowerCase();

        assertThat(sql).contains("alter table maintenance_regulations");
        assertThat(sql).contains("add column if not exists trigger_policy varchar(32) not null default 'any'");
        assertThat(sql).contains("add column if not exists recalculation_policy varchar(64) not null default 'from_actual_completion'");

        assertThat(sql).contains("alter table equipment_maintenance_rules");
        assertThat(sql).contains("add column if not exists disables_base_regulation boolean not null default false");
        assertThat(sql).contains("add column if not exists override_reason text");

        assertThat(sql).contains("create table if not exists maintenance_completion_anchors");
        assertThat(sql).contains("equipment_id uuid not null");
        assertThat(sql).contains("regulation_id uuid");
        assertThat(sql).contains("equipment_maintenance_rule_id uuid");
        assertThat(sql).contains("performed_at timestamptz not null");
        assertThat(sql).contains("meter_snapshots jsonb not null default '[]'::jsonb");
        assertThat(sql).contains("chk_maintenance_completion_anchors_source_present");
        assertThat(sql).contains("idx_maintenance_completion_anchors_equipment_regulation");
        assertThat(sql).contains("idx_maintenance_completion_anchors_equipment_rule");
    }
}
