package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WarrantyEmergencyOverridePermissionMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260626_2__warranty_emergency_override_permission.sql"
    );

    @Test
    void migrationDocumentsCodeOnlyPermissionAddition() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql.toLowerCase()).contains("repair_request_warranty_override");
        assertThat(sql.trim()).endsWith(";");
        assertThat(sql).contains("SELECT 1");
    }
}
