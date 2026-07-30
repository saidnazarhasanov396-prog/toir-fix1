package com.toir.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SparePartServiceLifeMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260710_2__spare_part_service_life_foundation.sql");

    @Test
    void migrationCreatesEveryAdditiveLifecycleTable() throws Exception {
        assertThat(Files.exists(MIGRATION)).isTrue();
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql).contains("create table if not exists spare_part_life_rules");
        assertThat(sql).contains("create table if not exists spare_part_life_limits");
        assertThat(sql).contains("create table if not exists spare_part_installations");
        assertThat(sql).contains("create table if not exists spare_part_installation_meter_baselines");
        assertThat(sql).contains("create table if not exists spare_part_installation_material_allocations");
        assertThat(sql).contains("create table if not exists spare_part_lifecycle_commands");
        assertThat(sql).contains("create table if not exists spare_part_due_events");
        assertThat(sql).doesNotContain("equipment_spare_parts");
        assertThat(sql).doesNotContain("insert into spare_part_installations");
    }

    @Test
    void migrationUsesDecimalResourcesAndLifecycleChecks() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql).contains("limit_value numeric(19, 6) not null");
        assertThat(sql).contains("warning_before_value numeric(19, 6)");
        assertThat(sql).contains("quantity numeric(19, 6) not null");
        assertThat(sql).contains("baseline_value numeric(19, 6) not null");
        assertThat(sql).contains("allocated_quantity numeric(19, 6) not null");
        assertThat(sql).contains("check (limit_value > 0)");
        assertThat(sql).contains("check (warning_before_value is null or warning_before_value >= 0)");
        assertThat(sql).contains("check (quantity > 0)");
        assertThat(sql).contains("chk_sp_installation_status_fields");
        assertThat(sql).contains("chk_sp_life_limit_shape");
    }

    @Test
    void migrationAddsHistorySafeForeignKeysAndUniqueness() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql).contains("foreign key (equipment_id) references equipment(id)");
        assertThat(sql).contains("foreign key (equipment_node_id) references equipment_nodes(id)");
        assertThat(sql).contains("foreign key (spare_part_id) references spare_parts(id)");
        assertThat(sql).contains("foreign key (equipment_meter_id) references equipment_meters(id)");
        assertThat(sql).contains("foreign key (repair_material_usage_id) references repair_material_usages(id)");
        assertThat(sql).doesNotContain("on delete cascade");
        assertThat(sql).contains("ux_sp_installations_active_position");
        assertThat(sql).contains("where status = 'active' and is_deleted = false");
        assertThat(sql).contains("idempotency_key varchar(255) not null unique");
        assertThat(sql).contains("repair_material_usage_id uuid not null unique");
        assertThat(sql).contains("unique (installation_id, cycle_key)");
    }

    @Test
    void migrationAddsOperationalIndexes() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql).contains("idx_sp_installations_active_equipment");
        assertThat(sql).contains("idx_sp_installations_active_part");
        assertThat(sql).contains("idx_sp_installations_work_orders");
        assertThat(sql).contains("idx_sp_installations_serial");
        assertThat(sql).contains("idx_sp_installations_due_scan");
        assertThat(sql).contains("idx_sp_due_events_state_due");
        assertThat(sql).contains("idx_sp_due_events_installation_cycle");
    }

    @Test
    void permissionMigrationSeedsGranularLifecycleAuthorities() throws Exception {
        Path permissions = Path.of(
                "src/main/resources/db/migration/V20260710_3__spare_part_service_life_permissions.sql");
        assertThat(Files.exists(permissions)).isTrue();
        String sql = Files.readString(permissions);

        assertThat(sql).contains(
                "SPARE_PART_LIFE_RULE_READ",
                "SPARE_PART_LIFE_RULE_CREATE",
                "SPARE_PART_LIFE_RULE_UPDATE",
                "SPARE_PART_LIFE_RULE_DELETE",
                "SPARE_PART_INSTALLATION_READ",
                "SPARE_PART_INSTALL",
                "SPARE_PART_REMOVE",
                "SPARE_PART_REPLACE",
                "SPARE_PART_DUE_READ",
                "SPARE_PART_DUE_ACKNOWLEDGE",
                "SPARE_PART_EXPIRY_OVERRIDE"
        );
    }

    @Test
    void w1MigrationAddsManualAuditAndAppendOnlyWorkOrderHistory() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V20260730_3__spare_part_lifecycle_w1_actions.sql");
        String sql = Files.readString(migration).toLowerCase();

        assertThat(sql).contains("manual_due_at", "manual_due_by", "manual_due_reason");
        assertThat(sql).contains("create table if not exists spare_part_due_event_work_orders");
        assertThat(sql).contains("foreign key", "references spare_part_due_events(id)");
        assertThat(sql).contains("references work_orders(id)");
        assertThat(sql).contains("unique (due_event_id, idempotency_key)");
        assertThat(sql).doesNotContain("on delete cascade");
    }
}
