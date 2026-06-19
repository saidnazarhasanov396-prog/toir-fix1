package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProcurementResponsibleMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260619_1__procurement_request_responsible.sql");

    @Test
    void migrationAddsProcurementRequestResponsibleField() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("ALTER TABLE procurement_requests");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS responsible_id uuid");
        assertThat(sql).contains("idx_procurement_requests_responsible");
    }
}
