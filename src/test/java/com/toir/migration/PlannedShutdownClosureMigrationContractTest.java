package com.toir.migration;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class PlannedShutdownClosureMigrationContractTest {
    @Test
    void migrationEnforcesSingleActiveTestKeyProductionSignoffAndClosureSnapshot() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260711_10__planned_shutdown_closure_evidence.sql"));
        assertThat(sql).contains("planned_shutdown_startup_tests", "numeric(19,4)",
                "uq_planned_shutdown_startup_tests_active_key", "planned_shutdown_production_returns",
                "uq_planned_shutdown_production_returns_active", "planned_shutdown_closure_snapshots",
                "uq_planned_shutdown_closure_snapshots_active",
                "uq_planned_shutdown_isolation_active_order");
    }

    @Test
    void forwardMigrationMakesEveryClosureSnapshotAbsolutelyImmutable() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260711_11__planned_shutdown_closure_immutability.sql"));
        assertThat(sql).contains(
                "HAVING count(*) > 1",
                "DROP INDEX IF EXISTS uq_planned_shutdown_closure_snapshots_active",
                "UNIQUE (planned_shutdown_id)",
                "BEFORE UPDATE OR DELETE",
                "PLANNED_SHUTDOWN_CLOSURE_SNAPSHOT_IMMUTABLE");
    }
}
