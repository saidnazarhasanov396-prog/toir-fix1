package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ToirStage1IntegrityMigrationContractTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260711_1__toir_stage1_integrity_foundation.sql");

    @Test
    void addsConcurrencySafetyIdempotencyAndExactMoney() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();
        assertThat(sql).contains("add column if not exists version bigint not null default 0");
        assertThat(sql).contains("requires_shutdown boolean not null default false");
        assertThat(sql).contains("requires_isolation boolean not null default false");
        assertThat(sql).contains("generation_key varchar(512)");
        assertThat(sql).contains("uq_work_orders_active_generation_key");
        assertThat(sql).contains("numeric(19,4)");
        assertThat(sql).contains("currency_code varchar(3) not null default 'uzs'");
    }
}
