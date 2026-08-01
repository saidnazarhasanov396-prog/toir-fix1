package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalMajorityTieBreakMigrationContractTest {

    @Test
    void migrationAddsValidatedNullableTieBreakSnapshots() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V20260801_7__approval_majority_tie_break.sql"));

        assertThat(sql).containsIgnoringCase("ALTER TABLE approval_templates");
        assertThat(sql).containsIgnoringCase("ALTER TABLE approval_requests");
        assertThat(sql).containsIgnoringCase("tie_break_policy");
        assertThat(sql).containsIgnoringCase("APPROVE_ON_TIE");
        assertThat(sql).containsIgnoringCase("REJECT_ON_TIE");
        assertThat(sql).containsIgnoringCase("CHECK");
    }
}
