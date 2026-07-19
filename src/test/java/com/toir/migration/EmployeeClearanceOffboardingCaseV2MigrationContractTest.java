package com.toir.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EmployeeClearanceOffboardingCaseV2MigrationContractTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260718_1__erp_employee_clearance_offboarding_case_v2.sql");

    @Test
    void migrationKeepsLegacyRowsAndAddsIndexedCaseAuditField() throws Exception {
        assertThat(MIGRATION).exists();

        String sql = Files.readString(MIGRATION).toLowerCase();
        assertThat(sql).contains(
                "alter table erp_employee_clearance_outbox",
                "add column offboarding_case_id uuid",
                "create index idx_erp_employee_clearance_outbox_case",
                "(offboarding_case_id, source_revision)",
                "where offboarding_case_id is not null");
    }
}
