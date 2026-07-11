package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlannedShutdownRequestApprovalPermissionMigrationTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260711_8__planned_shutdown_request_approval_permission.sql");

    @Test
    void migrationGrantsRequestAuthorityOnlyToFullOperationalManagementRoles() throws Exception {
        String sql = Files.readString(MIGRATION);
        String grantClause = sql.substring(sql.indexOf("WHERE role.code"));
        assertThat(sql).contains("PLANNED_SHUTDOWN_REQUEST_APPROVAL")
                .contains("PPR_ENGINEER is intentionally excluded");
        assertThat(grantClause).contains("'SYSTEM_ADMIN', 'TECHNICAL_DIRECTOR', 'CHIEF_MECHANIC'")
                .doesNotContain("PPR_ENGINEER")
                .doesNotContain("RELIABILITY_ENGINEER");
    }
}
