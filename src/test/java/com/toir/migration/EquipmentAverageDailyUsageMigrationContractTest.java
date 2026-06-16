package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentAverageDailyUsageMigrationContractTest {

    @Test
    void migrationAddsNullableAverageDailyUsageColumn() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V20260616_6__equipment_average_daily_usage.sql"
        );

        assertThat(Files.exists(migration)).isTrue();
        String sql = Files.readString(migration).toLowerCase();

        assertThat(sql).contains("alter table equipment");
        assertThat(sql).contains("add column if not exists average_daily_usage double precision");
        assertThat(sql).contains("chk_equipment_average_daily_usage_positive");
        assertThat(sql).doesNotContain("average_daily_usage double precision not null");
    }
}
