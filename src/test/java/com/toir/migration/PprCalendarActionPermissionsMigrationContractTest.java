package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PprCalendarActionPermissionsMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260730_6__ppr_calendar_action_permissions.sql"
    );

    @Test
    void migrationCopiesEachExistingRegistryActionToItsCalendarCounterpart() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains(
                "PPR_PLAN_CREATE", "PPR_CALENDAR_CREATE",
                "PPR_PLAN_UPDATE", "PPR_CALENDAR_UPDATE",
                "PPR_PLAN_DELETE", "PPR_CALENDAR_DELETE",
                "PPR_PLAN_APPROVE", "PPR_CALENDAR_APPROVE",
                "PPR_PLAN_GENERATE", "PPR_CALENDAR_GENERATE"
        );
        assertThat(sql).contains("WHERE is_deleted = false");
        assertThat(sql).contains("AND NOT (");
        assertThat(sql.toLowerCase()).doesNotContain("delete from roles");
    }
}
