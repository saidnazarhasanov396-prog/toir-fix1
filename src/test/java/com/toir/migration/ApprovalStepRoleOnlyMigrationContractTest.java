package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalStepRoleOnlyMigrationContractTest {

    @Test
    void migrationMakesApproverIdNullableAndRequiresIdOrRole() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260618_1__approval_step_role_based_nullable_approver.sql"
        ));

        assertThat(sql).containsIgnoringCase("ALTER COLUMN approver_id DROP NOT NULL");
        assertThat(sql).containsIgnoringCase("approver_id IS NOT NULL");
        assertThat(sql).containsIgnoringCase("BTRIM(approver_role)");
    }
}
