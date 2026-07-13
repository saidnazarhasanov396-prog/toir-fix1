package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RepairCampaignApprovalScopeConstraintCorrectionMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260713_1__repair_campaign_resource_check_approval_scope_constraint.sql");

    @Test
    void migrationReplacesStatusCoupledApprovalScopeConstraintWithPairOnlyConstraint() throws Exception {
        assertThat(MIGRATION).exists();
        String sql = Files.readString(MIGRATION).toLowerCase().replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("drop constraint if exists chk_repair_campaign_approval_scope")
                .contains("add constraint chk_repair_campaign_approval_scope check")
                .contains("approval_scope_version is null and approval_scope_hash is null")
                .contains("approval_scope_version is not null and approval_scope_hash is not null");
        assertThat(sql)
                .doesNotContain("resource_check")
                .doesNotContain("pending_approval");
    }
}
