package com.toir.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EquipmentPassportProductivityMigrationContractTest {

    @Test
    void migrationAddsStructuredProductivityColumnAndDropsLegacyThroughput() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260617_4__equipment_passport_productivity.sql"));

        assertThat(sql).contains("ALTER TABLE equipment_passports");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS productivity text");
        assertThat(sql).contains("DROP COLUMN IF EXISTS throughput");
    }
}
