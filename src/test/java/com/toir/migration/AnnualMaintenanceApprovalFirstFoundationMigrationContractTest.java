package com.toir.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnnualMaintenanceApprovalFirstFoundationMigrationContractTest {

    private static final Path MIGRATION_DIRECTORY =
            Path.of("src/main/resources/db/migration");
    private static final String TRACKED_V3 =
            "V20260728_3__maintenance_schedule_weekday_shifting.sql";
    private static final String FOUNDATION_V4 =
            "V20260728_4__annual_maintenance_approval_first_foundation.sql";

    @Test
    void trackedV3RemainsTheOnlyMigrationAtVersionThree() throws Exception {
        List<String> v3Files;
        try (var paths = Files.list(MIGRATION_DIRECTORY)) {
            v3Files = paths
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("V20260728_3__"))
                    .sorted()
                    .toList();
        }

        assertThat(v3Files).containsExactly(TRACKED_V3);
        assertThat(Files.readString(MIGRATION_DIRECTORY.resolve(TRACKED_V3)))
                .contains("shift_from_excluded_weekdays")
                .doesNotContain("materialization_mode", "APPROVAL_FIRST");
    }

    @Test
    void v4UsesExpandBackfillDefaultConstrainOrdering() throws Exception {
        String sql = migration().toLowerCase();

        int expand = sql.indexOf("add column if not exists materialization_mode");
        int backfill = sql.indexOf("update ppr_plans");
        int defaults = sql.indexOf("alter column materialization_mode set default");
        int constrain = sql.indexOf("constraint chk_ppr_plans_materialization_mode");
        int foreignKey = sql.indexOf("constraint fk_approval_requests_superseded_by");

        assertThat(expand).isGreaterThanOrEqualTo(0);
        assertThat(backfill).isGreaterThan(expand);
        assertThat(defaults).isGreaterThan(backfill);
        assertThat(constrain).isGreaterThan(defaults);
        assertThat(foreignKey).isGreaterThan(constrain);
    }

    @Test
    void v4BackfillsLegacyRowsWithoutChangingGeneratedPlansOrTasks() throws Exception {
        String sql = migration();

        assertThat(sql).contains(
                "materialization_mode = 'LEGACY_MATERIALIZED'",
                "task_materialization_status = 'NOT_APPLICABLE'",
                "DEFAULT 'LEGACY_MATERIALIZED'",
                "DEFAULT 'NOT_APPLICABLE'"
        );
        assertThat(sql).doesNotContain(
                "SET status = 'CALCULATED'",
                "UPDATE ppr_tasks",
                "DELETE FROM ppr_tasks",
                "maintenance_schedule_calculation_items",
                "source_calculation_item_id",
                "maintenance_schedule_command_results",
                "uq_approval_first_active_request"
        );
    }

    @Test
    void v4AddsLegacySafePlanAndApprovalConstraints() throws Exception {
        String sql = migration();

        assertThat(sql).contains(
                "'LEGACY_MATERIALIZED'",
                "'APPROVAL_FIRST'",
                "'NOT_APPLICABLE'",
                "'NOT_MATERIALIZED'",
                "'MATERIALIZED'",
                "origin = 'MAINTENANCE_SCHEDULE'",
                "calculation_revision >= 1",
                "calculation_content_hash ~ '^[0-9a-f]{64}$'",
                "calculation_content_hash_version >= 1",
                "materialized_revision IS NOT NULL",
                "materialized_task_count >= 0",
                "materialized_revision IS NULL",
                "materialized_revision <= calculation_revision",
                "'CALCULATED'",
                "'SUPERSEDED'"
        );
    }

    @Test
    void v4AddsOnlyMissingApprovalBindingFields() throws Exception {
        String sql = migration().toLowerCase();

        assertThat(sql).contains(
                "add column if not exists calculation_revision",
                "add column if not exists calculation_content_hash",
                "add column if not exists calculation_content_hash_version",
                "add column if not exists resolved_route_fingerprint",
                "add column if not exists requester_context_fingerprint",
                "add column if not exists resolution_code",
                "add column if not exists superseded_by_request_id"
        );
        assertThat(sql).doesNotContain(
                "add column if not exists template_id",
                "add column if not exists template_version",
                "add column if not exists action_type",
                "add column if not exists failure_reason",
                "add column if not exists last_return_comment",
                "add column if not exists last_returned_by",
                "add column if not exists last_returned_at",
                "add column if not exists resolution_reason",
                "add column if not exists resolved_by",
                "add column if not exists resolved_at",
                "approved_revision"
        );
    }

    private static String migration() throws Exception {
        return Files.readString(MIGRATION_DIRECTORY.resolve(FOUNDATION_V4));
    }
}
