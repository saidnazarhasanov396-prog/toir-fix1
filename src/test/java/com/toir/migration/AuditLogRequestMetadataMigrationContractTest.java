package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogRequestMetadataMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260625_5__audit_log_request_metadata.sql"
    );
    private static final String LAST_KNOWN_DEPLOYED_MIGRATION_VERSION = "20260625_3";

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

    @Test
    void migrationRunsAfterAlreadyDeployedJune25Migrations() {
        String version = migrationVersion(MIGRATION);

        assertThat(compareVersions(version, LAST_KNOWN_DEPLOYED_MIGRATION_VERSION))
                .isGreaterThan(0);
    }

    private String migrationVersion(Path migration) {
        String fileName = migration.getFileName().toString();
        assertThat(fileName).startsWith("V").contains("__");
        return fileName.substring(1, fileName.indexOf("__"));
    }

    private int compareVersions(String left, String right) {
        int[] leftParts = versionParts(left);
        int[] rightParts = versionParts(right);
        int maxLength = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < maxLength; i++) {
            int leftPart = i < leftParts.length ? leftParts[i] : 0;
            int rightPart = i < rightParts.length ? rightParts[i] : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }

    private int[] versionParts(String version) {
        return Arrays.stream(version.split("[._]"))
                .mapToInt(Integer::parseInt)
                .toArray();
    }
}
