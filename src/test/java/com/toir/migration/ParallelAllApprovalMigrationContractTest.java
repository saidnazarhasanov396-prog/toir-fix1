package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelAllApprovalMigrationContractTest {

    @Test
    void migrationBackfillsSequentialFlowAndAddsRoundTaskUniqueness() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260727_2__parallel_all_approval.sql"
        ));

        assertThat(sql).containsIgnoringCase("ADD COLUMN IF NOT EXISTS flow_type");
        assertThat(sql).containsIgnoringCase("ADD COLUMN IF NOT EXISTS approval_round");
        assertThat(sql).containsIgnoringCase("ADD COLUMN IF NOT EXISTS template_id");
        assertThat(sql).containsIgnoringCase("ADD COLUMN IF NOT EXISTS template_version");
        assertThat(sql).containsIgnoringCase("SET flow_type = 'SEQUENTIAL'");
        assertThat(sql).containsIgnoringCase("SET approval_round = 1");
        assertThat(sql).containsIgnoringCase(
                "ON approval_steps (request_id, approval_round, approver_id)");
        assertThat(sql).containsIgnoringCase("WHERE flow_type = 'PARALLEL_ALL'");
        assertThat(sql).containsIgnoringCase("'CANCELLED'");
    }
}
