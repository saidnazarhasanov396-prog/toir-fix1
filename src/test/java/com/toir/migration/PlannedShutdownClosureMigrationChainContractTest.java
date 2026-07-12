package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlannedShutdownClosureMigrationChainContractTest {
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    @Test
    void v4ToV10ToV11TransformsExistingEvidenceTablesWithoutDuplicateCreate() throws Exception {
        String v4 = read("V20260711_4__planned_shutdown_core.sql");
        String v10 = read("V20260711_10__planned_shutdown_closure_evidence.sql");
        String v11 = read("V20260711_11__planned_shutdown_closure_immutability.sql");

        assertThat(v4).contains("CREATE TABLE planned_shutdown_startup_tests",
                "CREATE TABLE planned_shutdown_closure_snapshots");
        assertThat(v10).doesNotContain("CREATE TABLE planned_shutdown_startup_tests",
                "CREATE TABLE planned_shutdown_closure_snapshots");
        assertThat(v10).contains("CREATE TABLE planned_shutdown_production_returns");

        assertThat(v10).contains(
                "PLANNED_SHUTDOWN_STARTUP_TEST_ROWS_REQUIRE_MANUAL_REMEDIATION",
                "status = 'WAIVED'", "measured_value !~",
                "ALTER TABLE planned_shutdown_startup_tests",
                "RENAME COLUMN test_code TO test_key",
                "RENAME COLUMN measured_unit TO result_unit",
                "RENAME COLUMN result_notes TO evidence",
                "RENAME COLUMN tested_at TO verified_at",
                "ADD COLUMN unit varchar(64)", "TYPE numeric(19,4)",
                "DROP INDEX IF EXISTS uq_planned_shutdown_startup_tests_active_code",
                "DROP INDEX IF EXISTS idx_planned_shutdown_startup_tests_shutdown_status",
                "chk_planned_shutdown_startup_tests_result",
                "uq_planned_shutdown_startup_tests_active_key");
        assertThat(v10).contains(
                "DROP CONSTRAINT IF EXISTS fk_planned_shutdown_startup_tests_performed_by",
                "DROP CONSTRAINT IF EXISTS fk_planned_shutdown_startup_tests_verified_by",
                "DROP CONSTRAINT IF EXISTS chk_planned_shutdown_startup_tests_status",
                "DROP CONSTRAINT IF EXISTS chk_planned_shutdown_startup_tests_order",
                "ADD CONSTRAINT fk_planned_shutdown_startup_tests_performer",
                "ADD CONSTRAINT fk_planned_shutdown_startup_tests_verifier",
                "CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PASSED', 'FAILED'))");
        assertThat(v10).containsSubsequence(
                "PLANNED_SHUTDOWN_CLOSURE_LEGACY_ROWS_REQUIRE_MANUAL_REMEDIATION",
                "PLANNED_SHUTDOWN_STARTUP_TEST_ROWS_REQUIRE_MANUAL_REMEDIATION",
                "DROP INDEX IF EXISTS uq_planned_shutdown_startup_tests_active_code",
                "RENAME COLUMN test_code TO test_key",
                "ALTER COLUMN measured_value TYPE numeric(19,4)",
                "ADD CONSTRAINT chk_planned_shutdown_startup_tests_result",
                "CREATE UNIQUE INDEX uq_planned_shutdown_startup_tests_active_key");

        assertThat(v10).contains(
                "PLANNED_SHUTDOWN_CLOSURE_LEGACY_ROWS_REQUIRE_MANUAL_REMEDIATION",
                "DROP INDEX IF EXISTS uq_planned_shutdown_closure_active_shutdown",
                "DROP COLUMN closure_version", "DROP COLUMN planned_downtime_minutes",
                "DROP COLUMN actual_downtime_minutes", "DROP COLUMN snapshot",
                "DROP COLUMN production_signoff_employee_id",
                "ADD COLUMN window_version bigint NOT NULL",
                "ADD COLUMN snapshot_hash varchar(64) NOT NULL",
                "ADD COLUMN snapshot_json text NOT NULL",
                "uq_planned_shutdown_closure_snapshots_active");
        assertThat(v10).contains(
                "DROP CONSTRAINT IF EXISTS fk_planned_shutdown_closure_production_signoff",
                "DROP CONSTRAINT IF EXISTS fk_planned_shutdown_closure_closed_by",
                "DROP CONSTRAINT IF EXISTS chk_planned_shutdown_closure_version",
                "DROP CONSTRAINT IF EXISTS chk_planned_shutdown_closure_downtime",
                "ADD CONSTRAINT fk_planned_shutdown_closure_snapshots_actor");
        assertThat(v10).containsSubsequence(
                "PLANNED_SHUTDOWN_CLOSURE_LEGACY_ROWS_REQUIRE_MANUAL_REMEDIATION",
                "DROP INDEX IF EXISTS uq_planned_shutdown_closure_active_shutdown",
                "DROP COLUMN closure_version",
                "ADD COLUMN window_version bigint NOT NULL",
                "CREATE UNIQUE INDEX uq_planned_shutdown_closure_snapshots_active");

        assertThat(v11).contains(
                "DROP INDEX IF EXISTS uq_planned_shutdown_closure_snapshots_active",
                "ADD CONSTRAINT uq_planned_shutdown_closure_snapshots_shutdown",
                "UNIQUE (planned_shutdown_id)");
    }

    private static String read(String file) throws Exception {
        return Files.readString(MIGRATIONS.resolve(file));
    }
}
