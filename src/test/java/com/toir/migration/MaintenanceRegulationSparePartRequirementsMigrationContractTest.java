package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceRegulationSparePartRequirementsMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260610_3__maintenance_regulation_spare_part_requirements.sql");

    @Test
    void migrationCreatesRegulationRequirementTableAndIndexes() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS maintenance_regulation_spare_part_requirements");
        assertThat(sql).contains("FOREIGN KEY (regulation_id) REFERENCES maintenance_regulations(id)");
        assertThat(sql).contains("FOREIGN KEY (spare_part_id) REFERENCES spare_parts(id)");
        assertThat(sql).contains("CONSTRAINT chk_mr_spare_req_quantity CHECK (quantity > 0)");
        assertThat(sql).contains("CREATE UNIQUE INDEX IF NOT EXISTS ux_mr_spare_req_active_unique");
        assertThat(sql).contains("WHERE is_deleted = false AND is_active = true");
    }

    @Test
    void migrationExtendsWorkOrderRequirementsForRegulationSources() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("regulation_requirement_id uuid");
        assertThat(sql).contains(
                "FOREIGN KEY (regulation_requirement_id) REFERENCES maintenance_regulation_spare_part_requirements(id)");
        assertThat(sql).contains("REGULATION_REQUIRED_SPARE_PART");
        assertThat(sql).contains("CREATE UNIQUE INDEX IF NOT EXISTS ux_wo_spare_req_regulation_source");
    }
}
