package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class LifecycleApprovalTemplateUniquenessMigrationContractTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260716_1__unique_active_lifecycle_approval_templates.sql");
    private static final Path RUNBOOK = Path.of(
            "docs/runbooks/lifecycle-approval-template-uniqueness-preflight.md");
    private static final Path POSTGRES_TEST = Path.of(
            "src/test/java/com/toir/migration/LifecycleApprovalTemplateUniquenessMigrationPostgresTest.java");

    @Test
    void migrationLocksBeforePreflightUsesIdenticalNormalizationAndNeverRewritesTemplates() throws IOException {
        String sql = Files.readString(MIGRATION);
        assertThat(count(sql, "COALESCE(action_type, 'APPROVE')")).isGreaterThanOrEqualTo(2);
        assertThat(sql).contains(
                "LOCK TABLE approval_templates IN SHARE ROW EXCLUSIVE MODE",
                "MULTIPLE_ACTIVE_TEMPLATES",
                "REPAIR_CAMPAIGN",
                "PLANNED_SHUTDOWN",
                "CREATE UNIQUE INDEX",
                "active = true",
                "is_deleted = false");
        assertThat(sql).doesNotContain(
                "UPDATE approval_templates",
                "DELETE FROM approval_templates",
                "INSERT INTO approval_templates");
        assertThat(sql.indexOf("LOCK TABLE approval_templates"))
                .isLessThan(sql.indexOf("DO $$"));
    }

    @Test
    void runbookLocksValidatesExactCoverageAndChecksUpdateResultAndInvariant() throws IOException {
        String runbook = Files.readString(RUNBOOK);

        assertThat(runbook).contains(
                "LOCK TABLE approval_templates IN SHARE ROW EXCLUSIVE MODE",
                "keep_template.active = false",
                "keep_template.is_deleted = true",
                "losing_template.active = false",
                "losing_template.is_deleted = true",
                "a keep_id cannot be deactivated",
                "COUNT(DISTINCT remediation.keep_id) <> 1",
                "reviewed pairs must exactly cover every active duplicate group",
                "GET DIAGNOSTICS updated_count = ROW_COUNT",
                "updated_count <> expected_update_count",
                "MULTIPLE_ACTIVE_TEMPLATES remain after approved remediation");
        assertThat(runbook.indexOf("LOCK TABLE approval_templates"))
                .isLessThan(runbook.indexOf("DO $$"));
        assertThat(runbook).doesNotContain("ORDER BY updated_at DESC LIMIT 1");
    }

    @Test
    void postgresTestUsesDedicatedLocalDatabaseAndConcurrencyIsBounded() throws Exception {
        String source = Files.readString(
                Path.of(
                        "src/test/java/com/toir/migration/"
                        + "LifecycleApprovalTemplateUniquenessMigrationPostgresTest.java"
                )
        );

        assertThat(source)
                .contains(
                        "private static final String JDBC_URL",
                        "jdbc:postgresql://localhost:5433/toir_migration_test",
                        "private static final String DB_USERNAME",
                        "private static final String DB_PASSWORD",
                        "DriverManager.getConnection(",
                        "JDBC_URL",
                        "DB_USERNAME",
                        "DB_PASSWORD",
                        "new CountDownLatch(2)",
                        "connectionsReady.await(10, TimeUnit.SECONDS)",
                        "start.await(10, TimeUnit.SECONDS)",
                        "get(15, TimeUnit.SECONDS)",
                        "SET LOCAL lock_timeout = '5s'",
                        "SET LOCAL statement_timeout = '10s'",
                        "finally",
                        "executor.shutdownNow()",
                        "executor.awaitTermination(5, TimeUnit.SECONDS)",
                        "\"23505\""
                )
                .doesNotContain(
                        "@Testcontainers",
                        "@Container",
                        "PostgreSQLContainer<",
                        "POSTGRES.getJdbcUrl()",
                        "POSTGRES.getUsername()",
                        "POSTGRES.getPassword()"
                );
    }

    private static int count(String text, String needle) {
        return text.split(Pattern.quote(needle), -1).length - 1;
    }
}
