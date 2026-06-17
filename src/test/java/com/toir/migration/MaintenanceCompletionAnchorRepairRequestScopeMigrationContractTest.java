package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceCompletionAnchorRepairRequestScopeMigrationContractTest {

    @Test
    void migrationScopesRepairRequestAnchorUniquenessByRegulationAndRule() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V20260617_1__repair_request_anchor_scope_uniqueness.sql"
        );
        assertThat(Files.exists(migration)).isTrue();

        String sql = Files.readString(migration).toLowerCase();

        assertThat(sql).contains("drop index if exists uq_maintenance_completion_anchors_repair_request");
        assertThat(sql).contains("repair_request_id, regulation_id, equipment_maintenance_rule_id");
        assertThat(sql).contains("repair_request_id, regulation_id");
        assertThat(sql).contains("repair_request_id, equipment_maintenance_rule_id");
    }
}
