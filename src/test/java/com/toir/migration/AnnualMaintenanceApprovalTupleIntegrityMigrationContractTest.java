package com.toir.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnnualMaintenanceApprovalTupleIntegrityMigrationContractTest {

    @Test
    void v7KeepsLegacyNullTuplesAndRejectsPartialApprovalBindings() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V20260729_7__annual_maintenance_exact_approval_tuple_integrity.sql"));

        assertThat(sql).contains(
                "chk_approval_requests_calculation_tuple",
                "calculation_revision IS NULL",
                "calculation_content_hash IS NULL",
                "calculation_content_hash_version IS NULL",
                "calculation_revision IS NOT NULL",
                "calculation_content_hash IS NOT NULL",
                "calculation_content_hash_version IS NOT NULL",
                "calculation_revision >= 1",
                "calculation_content_hash ~ '^[0-9a-f]{64}$'");
        assertThat(sql).doesNotContain(
                "ALTER COLUMN calculation_revision SET NOT NULL");
    }

    @Test
    void v8RequiresMaterializedPlanToMatchTheApprovedCurrentTuple()
            throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V20260729_8__annual_maintenance_approved_tuple_integrity.sql"));

        assertThat(sql).contains(
                "chk_ppr_plans_approved_tuple",
                "approved_revision IS NULL",
                "approved_revision IS NOT NULL",
                "chk_ppr_plans_materialized_approved_tuple",
                "materialized_revision = approved_revision",
                "approved_revision = calculation_revision",
                "approved_content_hash = calculation_content_hash");
    }

    @Test
    void v9AddsImmutableHistoricalSourceDisplayColumns() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V20260729_9__maintenance_schedule_snapshot_source_metadata.sql"));

        assertThat(sql).contains(
                "source_code_snapshot",
                "source_name_snapshot",
                "Immutable source code",
                "Immutable source name");
    }
}
