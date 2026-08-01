package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalRejectionPoliciesMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260801_9__approval_rejection_policies.sql");

    @Test
    void migrationAddsPolicySnapshotsReworkAndDecisionEvidence() throws Exception {
        assertThat(MIGRATION).exists();

        String sql = Files.readString(MIGRATION);

        assertThat(sql).containsIgnoringCase("ADD COLUMN IF NOT EXISTS rejection_policy");
        assertThat(sql).containsIgnoringCase("UPDATE approval_templates");
        assertThat(sql).containsIgnoringCase("UPDATE approval_requests");
        assertThat(sql).containsIgnoringCase("SET rejection_policy = 'TERMINATE'");
        assertThat(sql).containsIgnoringCase("ALTER COLUMN rejection_policy SET DEFAULT 'TERMINATE'");
        assertThat(sql).containsIgnoringCase("ALTER COLUMN rejection_policy SET NOT NULL");
        assertThat(sql).containsIgnoringCase("'RETURN_TO_PREVIOUS_STEP'");
        assertThat(sql).containsIgnoringCase("'RETURN_TO_INITIATOR'");
        assertThat(sql).containsIgnoringCase("'MAJORITY'");
        assertThat(sql).containsIgnoringCase("'REWORK'");
        assertThat(sql).containsIgnoringCase("ADD COLUMN IF NOT EXISTS step_id uuid");
        assertThat(sql).containsIgnoringCase("ADD COLUMN IF NOT EXISTS step_number integer");
        assertThat(sql).containsIgnoringCase("ADD COLUMN IF NOT EXISTS approval_round integer");
        assertThat(sql).containsIgnoringCase("ADD COLUMN IF NOT EXISTS decision varchar");

        assertBackfillPrecedesNotNull(sql, "approval_templates");
        assertBackfillPrecedesNotNull(sql, "approval_requests");
    }

    private void assertBackfillPrecedesNotNull(String sql, String table) {
        int tableStart = sql.indexOf("ALTER TABLE " + table);
        int backfill = sql.indexOf("UPDATE " + table, tableStart);
        int notNull = sql.indexOf("ALTER COLUMN rejection_policy SET NOT NULL", backfill);

        assertThat(tableStart).isGreaterThanOrEqualTo(0);
        assertThat(backfill).isGreaterThan(tableStart);
        assertThat(notNull).isGreaterThan(backfill);
    }
}
