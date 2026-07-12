package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlannedShutdownGenerationMigrationContractTest {
    @Test
    void migrationAddsDurableWindowAndIdempotencyContract() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260711_9__planned_shutdown_generation_idempotency.sql"));
        assertThat(sql).contains("window_version bigint NOT NULL DEFAULT 1")
                .contains("planned_shutdown_generation_requests")
                .contains("request_fingerprint varchar(64) NOT NULL")
                .contains("ordered_work_order_ids text NOT NULL")
                .contains("uq_ps_generation_request_shutdown_key UNIQUE (planned_shutdown_id, idempotency_key)");
    }
}
