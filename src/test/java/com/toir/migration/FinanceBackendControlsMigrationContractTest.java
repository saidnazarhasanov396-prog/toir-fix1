package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceBackendControlsMigrationContractTest {

    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/V20260627_10__finance_backend_controls.sql");

    @Test
    void migrationAddsFinanceLifecycleAllocationAndAuditControls() throws Exception {
        String migration = Files.readString(MIGRATION);

        assertThat(migration)
                .contains("SUBMITTED")
                .contains("REJECTED")
                .contains("budget_events")
                .contains("actual_cost_allocation_events")
                .contains("correction_reason")
                .contains("allocated_at");
    }
}
