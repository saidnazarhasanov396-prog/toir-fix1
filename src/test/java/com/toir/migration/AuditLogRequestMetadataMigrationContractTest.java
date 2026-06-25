package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogRequestMetadataMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260624_2__audit_log_request_metadata.sql"
    );

    @Test
    void migrationAddsNullableAuditMetadataColumnsAndReadIndexes() throws Exception {
        assertThat(Files.exists(MIGRATION)).isTrue();
        String sql = Files.readString(MIGRATION).toLowerCase();

        for (String column : List.of(
                "reason text",
                "source varchar(255)",
                "request_method varchar(32)",
                "request_path text",
                "correlation_id varchar(255)"
        )) {
            assertThat(sql).contains(column);
        }

        assertThat(sql).contains("alter table audit_logs");
        assertThat(sql).contains("create index if not exists idx_audit_logs_module");
        assertThat(sql).contains("create index if not exists idx_audit_logs_entity");
        assertThat(sql).contains("create index if not exists idx_audit_logs_created_at");
        assertThat(sql).contains("create index if not exists idx_audit_logs_correlation_id");
        assertThat(sql).doesNotContain("not null");
    }
}
