package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentAverageOperatingLifeMigrationContractTest {

    @Test
    void migrationAddsNullableAverageOperatingLifeHoursColumn() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V20260525_5__equipment_average_operating_life_hours.sql"
        );

        assertThat(Files.exists(migration)).isTrue();
        String sql = Files.readString(migration).toLowerCase();

        assertThat(sql).contains("alter table equipment");
        assertThat(sql).contains("add column if not exists average_operating_life_hours bigint");
        assertThat(sql).doesNotContain("average_operating_life_hours bigint not null");
    }
}
