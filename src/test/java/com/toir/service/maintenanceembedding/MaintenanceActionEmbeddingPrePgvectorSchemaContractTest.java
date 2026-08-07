package com.toir.service.maintenanceembedding;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceActionEmbeddingPrePgvectorSchemaContractTest {

    @Test
    void schemaContainsDurableIdentityAndNoVectorFallback() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V20260806_1__maintenance_action_embedding_pre_pgvector.sql")) {
            if (stream == null) {
                throw new IllegalStateException("Pre-pgvector migration resource is missing");
            }
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains(
                "maintenance_action_id uuid NOT NULL",
                "source_text_hash char(64) NOT NULL",
                "model_revision varchar(255) NOT NULL",
                "lease_token uuid",
                "uq_maintenance_action_embedding_job_representation",
                "uq_maintenance_action_embedding_backfill_idempotency");
        assertThat(sql).contains("CHECK (status <> 'READY')");
        assertThat(sql).doesNotContain("CREATE EXTENSION", "vector(", "<=>", "jsonb", "double precision[]");
    }

    @Test
    void jobPortCannotCompleteReadyWithoutFutureVectorPersistence() {
        assertThat(MaintenanceActionEmbeddingJobStore.class.getMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("markReady", "completeReady", "saveVector");
    }
}
