package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalGeneratedRolesCleanupMigrationContractTest {

    @Test
    void migrationRemovesKnownGeneratedRolesOnlyFromSeededTemplates() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260618_7__remove_generated_approval_roles.sql"
        ));

        assertThat(sql).containsIgnoringCase("DELETE FROM approval_template_steps");
        assertThat(sql).containsIgnoringCase("UPDATE approval_templates");
        assertThat(sql).containsIgnoringCase("SET approver_role = NULL");
        assertThat(sql).contains(
                "WORK_ORDER_APPROVER",
                "PPR_PLAN_APPROVER",
                "PROCUREMENT_APPROVER",
                "BUDGET_APPROVER",
                "MAINTENANCE_EVENT_APPROVER",
                "MAINTENANCE_REGULATION_APPROVER",
                "REPAIR_REQUEST_APPROVER"
        );
        assertThat(sql).contains(
                "WORK_ORDER_APPROVAL",
                "PPR_PLAN_APPROVAL",
                "PROCUREMENT_APPROVAL",
                "BUDGET_APPROVAL",
                "MAINTENANCE_DUE_EVENT_APPROVAL",
                "MAINTENANCE_REGULATION_APPROVAL",
                "REPAIR_REQUEST_APPROVAL"
        );
        assertThat(sql).doesNotContain("approval_requests");
        assertThat(sql).doesNotContain("approval_steps");
    }
}
