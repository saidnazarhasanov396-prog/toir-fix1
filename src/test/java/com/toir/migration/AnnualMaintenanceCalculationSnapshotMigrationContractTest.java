package com.toir.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class AnnualMaintenanceCalculationSnapshotMigrationContractTest {

    private static final Path MIGRATION_DIRECTORY =
            Path.of("src/main/resources/db/migration");
    private static final String TRACKED_V3 =
            "V20260728_3__maintenance_schedule_weekday_shifting.sql";
    private static final String FOUNDATION_V4 =
            "V20260728_4__annual_maintenance_approval_first_foundation.sql";
    private static final String SNAPSHOT_V5 =
            "V20260728_5__annual_maintenance_calculation_snapshots.sql";
    private static final String SNAPSHOT_INTEGRITY_V5_1 =
            "V20260728_5_1__annual_maintenance_calculation_snapshot_integrity.sql";

    @Test
    void committedV3V4AndV5RemainByteForByteUnchanged() throws Exception {
        assertThat(sha256(TRACKED_V3))
                .isEqualTo("f86bc7db5dc33afb08ead2e7e53916cdf29544199b4d77de2f9900493a607d31");
        assertThat(sha256(FOUNDATION_V4))
                .isEqualTo("37df0235e21abc2d737e134457b622d7ee8fca41a5f97888223147f2eb161f83");
        assertThat(sha256(SNAPSHOT_V5))
                .isEqualTo("a82612f764d17af70bff44760f43373abf81b0365d8597cbb20a937a92d3e1d7");
    }

    @Test
    void v5CreatesVersionedRelationalSnapshotWithRequiredColumnsAndChecks()
            throws Exception {
        String sql = normalizedMigration();

        assertThat(sql).contains(
                "create table maintenance_schedule_calculation_items",
                "id uuid primary key",
                "plan_id uuid not null",
                "calculation_revision bigint not null",
                "source_item_key varchar(64) not null",
                "source_item_key_version integer not null",
                "equipment_id uuid",
                "regulation_id uuid",
                "maintenance_rule_id uuid",
                "template_id uuid",
                "maintenance_type varchar(64)",
                "trigger_type varchar(64)",
                "trigger_discriminator varchar(255)",
                "cycle_ordinal bigint not null",
                "planned_date date not null",
                "scheduled_start timestamp",
                "scheduled_end timestamp",
                "due_date timestamp",
                "normative_labor_hours numeric(19, 4)",
                "priority varchar(64)",
                "department_id uuid",
                "equipment_code_snapshot varchar(255)",
                "equipment_name_snapshot varchar(255)",
                "regulation_name_snapshot varchar(255)",
                "maintenance_rule_name_snapshot varchar(255)",
                "template_name_snapshot varchar(255)",
                "task_title_snapshot varchar(255) not null",
                "created_at timestamptz not null",
                "updated_at timestamptz not null",
                "is_deleted boolean not null default false",
                "check (calculation_revision >= 1)",
                "check (source_item_key_version >= 1)"
        );
    }

    @Test
    void v5UsesUniqueRevisionIdentityAndRestrictiveCanonicalForeignKeys()
            throws Exception {
        String sql = normalizedMigration();

        assertThat(sql).contains(
                "constraint uq_ms_calc_items_plan_revision_source_key unique (plan_id, calculation_revision, source_item_key)",
                "foreign key (plan_id) references ppr_plans(id) on delete restrict",
                "foreign key (equipment_id) references equipment(id) on delete restrict",
                "foreign key (regulation_id) references maintenance_regulations(id) on delete restrict",
                "foreign key (maintenance_rule_id) references equipment_maintenance_rules(id) on delete restrict",
                "foreign key (template_id) references maintenance_templates(id) on delete restrict",
                "foreign key (department_id) references departments(id) on delete restrict"
        );
        assertThat(sql).doesNotContain("on delete cascade");
    }

    @Test
    void v5AddsNullableLegacySafeTaskTraceabilityAndOneActiveTaskInvariant()
            throws Exception {
        String sql = normalizedMigration();

        assertThat(sql).contains(
                "alter table ppr_tasks add column if not exists source_calculation_item_id uuid",
                "foreign key (source_calculation_item_id) references maintenance_schedule_calculation_items(id) on delete restrict",
                "create unique index uq_ppr_tasks_active_source_calculation_item",
                "where source_calculation_item_id is not null and is_deleted = false"
        );
        assertThat(sql).doesNotContain(
                "source_calculation_item_id uuid not null",
                "update ppr_tasks",
                "set source_calculation_item_id"
        );
    }

    @Test
    void v5RejectsSnapshotUpdatesAndDeletesAtTheDatabaseBoundary()
            throws Exception {
        String sql = normalizedMigration();

        assertThat(sql).contains(
                "before update or delete on maintenance_schedule_calculation_items",
                "raise exception 'maintenance_schedule_calculation_items are immutable'"
        );
    }

    @Test
    void v5_1MakesTaskTraceabilityPlanBoundAndIndependentOfSoftDelete()
            throws Exception {
        String sql = normalizedMigration(SNAPSHOT_INTEGRITY_V5_1);

        assertThat(sql).contains(
                "unique (id, plan_id)",
                "foreign key (source_calculation_item_id, plan_id)",
                "references maintenance_schedule_calculation_items(id, plan_id)",
                "create unique index uq_ppr_tasks_source_calculation_item",
                "where source_calculation_item_id is not null",
                "drop index uq_ppr_tasks_active_source_calculation_item"
        );
        assertThat(sql).doesNotContain(
                "where source_calculation_item_id is not null and is_deleted = false"
        );
    }

    @Test
    void v5_1AddsSnapshotForeignKeySupportIndexes() throws Exception {
        String sql = normalizedMigration(SNAPSHOT_INTEGRITY_V5_1);

        assertThat(sql).contains(
                "on maintenance_schedule_calculation_items (equipment_id)",
                "on maintenance_schedule_calculation_items (regulation_id)",
                "on maintenance_schedule_calculation_items (maintenance_rule_id)",
                "on maintenance_schedule_calculation_items (template_id)",
                "on maintenance_schedule_calculation_items (department_id)"
        );
    }

    private static String normalizedMigration() throws Exception {
        return normalizedMigration(SNAPSHOT_V5);
    }

    private static String normalizedMigration(String migration) throws Exception {
        return Files.readString(MIGRATION_DIRECTORY.resolve(migration))
                .toLowerCase()
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String sha256(String migration) throws Exception {
        byte[] bytes = Files.readString(MIGRATION_DIRECTORY.resolve(migration))
                .getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)
        );
    }
}
