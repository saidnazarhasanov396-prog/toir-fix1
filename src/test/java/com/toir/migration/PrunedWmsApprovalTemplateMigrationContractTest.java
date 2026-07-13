package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PrunedWmsApprovalTemplateMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260713_2__normalize_pruned_warehouse_writeoff_approval_templates.sql");

    @Test
    void migrationNormalizesPrunedWarehouseWriteoffApprovalTemplates() throws Exception {
        assertThat(MIGRATION).exists();
        String sql = Files.readString(MIGRATION).toLowerCase().replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("update approval_templates")
                .contains("target_type = 'other'")
                .contains("where target_type = 'warehouse_writeoff'")
                .contains("update approval_requests");
    }
}
