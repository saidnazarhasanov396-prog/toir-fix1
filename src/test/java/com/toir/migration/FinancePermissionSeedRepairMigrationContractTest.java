package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FinancePermissionSeedRepairMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260627_11__finance_permission_seed_repair.sql"
    );

    @Test
    void migrationAppendsFinanceControlPermissionsToExistingRoles() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("append_role_permissions('ECONOMIST'")
                .contains("append_role_permissions('FINANCE_MANAGER'")
                .contains("BUDGET_CLOSE")
                .contains("BUDGET_REOPEN")
                .contains("BUDGET_TRANSFER")
                .contains("BUDGET_REVISE")
                .contains("ACTUAL_COST_ALLOCATE")
                .contains("ACTUAL_COST_REQUEST_CORRECTION")
                .contains("FINANCE_REPORT_EXPORT")
                .contains("DROP FUNCTION append_role_permissions(text, text[])");
    }
}
