package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RcmTaskSourceMigrationContractTest {
    @Test
    void migrationAddsPairedSourceMetadataAndPartialUniqueIndex() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V20260731_4__rcm_task_source_idempotency.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertThat(sql).contains("source_type", "source_key", "ck_ppr_tasks_source_pair",
                    "uq_ppr_tasks_active_rcm_source_key", "where is_deleted = false",
                    "source_type = 'rcm_auto_plan'");
        }
    }
}
