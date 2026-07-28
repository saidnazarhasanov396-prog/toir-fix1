package com.toir.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PprPlanOriginMigrationContractTest {

    @Test
    void migrationSeparatesExistingBuilderPlansFromManualPprPlans() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260728_1__ppr_plan_origin.sql"));

        assertThat(sql).containsIgnoringCase("ADD COLUMN IF NOT EXISTS origin");
        assertThat(sql).containsIgnoringCase("anchor_mode IS NOT NULL");
        assertThat(sql).contains("MAINTENANCE_SCHEDULE");
        assertThat(sql).contains("MANUAL");
        assertThat(sql).containsIgnoringCase("SET NOT NULL");
    }
}
