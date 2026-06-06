package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ActualCostSourceTraceabilityMigrationContractTest {

    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/V20260606_1__actual_cost_source_traceability.sql");

    @Test
    void sourceTraceabilityUniqueIndexOnlyAppliesToGranularAutoSources() throws Exception {
        String migration = Files.readString(MIGRATION);

        assertThat(migration)
                .contains("ux_actual_cost_auto_source_active")
                .contains("source_type IN ('LABOR_ENTRY', 'MATERIAL_ISSUE', 'PROCUREMENT_RECEIPT', 'CONTRACTOR_WORK')")
                .contains("status <> 'REJECTED'")
                .doesNotContain("CREATE UNIQUE INDEX IF NOT EXISTS ux_actual_cost_source_active")
                .doesNotContain("WHERE is_deleted = false\n      AND source_type IS NOT NULL\n      AND source_id IS NOT NULL");
    }
}
