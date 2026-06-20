package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProcurementPriorityMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260620_1__procurement_request_priority.sql");

    @Test
    void migrationAddsPriorityColumnConstraintAndIndex() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("ALTER TABLE procurement_requests");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS priority varchar(50) NOT NULL DEFAULT 'MEDIUM'");
        assertThat(sql).contains("procurement_requests_priority_check");
        assertThat(sql).contains("'LOW'", "'MEDIUM'", "'HIGH'", "'CRITICAL'", "'EMERGENCY'");
        assertThat(sql).contains("idx_procurement_requests_priority");
    }
}
