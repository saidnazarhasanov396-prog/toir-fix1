package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentGroupsMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260616_3__attachment_groups.sql");

    @Test
    void migrationCreatesUnifiedAttachmentGroupTablesAndBackfillsLegacyDocuments() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS attachment_groups");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS attachment_group_items");
        assertThat(sql).contains("REFERENCES uploaded_files(id)");
        assertThat(sql).contains("target_type");
        assertThat(sql).contains("target_id");
        assertThat(sql).contains("order_number");

        assertThat(sql).contains("FROM equipment_documents");
        assertThat(sql).contains("FROM vehicle_documents");
        assertThat(sql).contains("FROM work_order_documents");
        assertThat(sql).contains("FROM stock_movement_files");
    }
}
