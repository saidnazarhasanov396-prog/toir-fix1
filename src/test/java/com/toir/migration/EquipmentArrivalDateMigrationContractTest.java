package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentArrivalDateMigrationContractTest {

    @Test
    void migrationAddsNullableArrivalDateColumn() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V20260604_5__equipment_arrival_date.sql"
        );

        assertThat(Files.exists(migration)).isTrue();
        String sql = Files.readString(migration).toLowerCase();

        assertThat(sql).contains("alter table equipment");
        assertThat(sql).contains("add column if not exists arrival_date date");
        assertThat(sql).doesNotContain("arrival_date date not null");
    }
}
