package com.toir.service.maintenanceembedding;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceActionEmbeddingPgvectorSchemaContractTest {

    @Test
    void migrationUsesRealVector768WithoutOwningExtensionOrAnnIndex() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V20260806_2__maintenance_action_embedding_pgvector.sql")) {
            if (stream == null) {
                throw new IllegalStateException("pgvector migration resource is missing");
            }
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains(
                "embedding vector(768) NOT NULL",
                "maintenance_action_id uuid NOT NULL",
                "model_revision varchar(255) NOT NULL",
                "source_text_hash char(64) NOT NULL",
                "DROP CONSTRAINT ck_maintenance_action_embedding_job_ready_guard");
        assertThat(sql)
                .doesNotContainIgnoringCase("CREATE EXTENSION")
                .doesNotContainIgnoringCase("jsonb")
                .doesNotContainIgnoringCase("double precision[]")
                .doesNotContainIgnoringCase("ivfflat")
                .doesNotContainIgnoringCase("hnsw");
    }
}
