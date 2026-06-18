package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalTemplateStepsMigrationContractTest {

    @Test
    void migrationAddsActionAndOrderedTemplateStepsAndBackfillsLegacyRules() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260618_5__approval_template_steps_and_actions.sql"
        ));

        assertThat(sql).containsIgnoringCase("ADD COLUMN IF NOT EXISTS action_type");
        assertThat(sql).containsIgnoringCase("CREATE TABLE IF NOT EXISTS approval_template_steps");
        assertThat(sql).containsIgnoringCase("UNIQUE (template_id, step_order)");
        assertThat(sql).containsIgnoringCase("INSERT INTO approval_template_steps");
        assertThat(sql).doesNotContain("approval_requests");
        assertThat(sql).doesNotContain("approval_steps");
    }
}
