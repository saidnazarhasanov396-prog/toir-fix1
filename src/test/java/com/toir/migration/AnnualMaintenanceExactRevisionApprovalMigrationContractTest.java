package com.toir.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AnnualMaintenanceExactRevisionApprovalMigrationContractTest {

    @Test
    void v6ProtectsOnlyActiveExactNonLegacyApprovalTuples() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V20260729_6__annual_maintenance_exact_revision_approval.sql"));

        assertThat(sql).contains(
                "calculation_revision",
                "calculation_content_hash",
                "calculation_content_hash_version",
                "status = 'PENDING'",
                "is_deleted = FALSE",
                "uq_approval_requests_active_exact_calculation");
        assertThat(sql).doesNotContain(
                "ALTER COLUMN calculation_revision SET NOT NULL",
                "ALTER COLUMN calculation_content_hash SET NOT NULL");
    }
}
