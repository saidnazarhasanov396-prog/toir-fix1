package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleDriverEmployeeIdentityPreflightContractTest {

    private static final Path PREFLIGHT = Path.of(
            "scripts/db/preflight_vehicle_driver_employee_identity.sql");
    private static final Path VEHICLE_MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260616_4__vehicle_driver_sessions.sql");
    private static final Path ROLLOUT = Path.of(
            "docs/rollout/vehicle-equipment-employee-assignment-rollout.md");

    @Test
    void preflightClassifiesEveryDriverIdentityWithoutMutatingData() throws Exception {
        String preflightSql = Files.readString(PREFLIGHT);
        String upperSql = preflightSql.toUpperCase();

        assertThat(preflightSql)
                .contains("VALID_EMPLOYEE", "LEGACY_USER_WITH_UNIQUE_EMPLOYEE", "AMBIGUOUS", "UNRESOLVED")
                .contains("vehicle_details_id", "equipment_id", "responsible_uuid", "user_match", "employee_link_count");
        assertThat(upperSql)
                .doesNotContain("UPDATE ")
                .doesNotContain("DELETE ")
                .doesNotContain("INSERT ")
                .doesNotContain("MERGE ");
    }

    @Test
    void rolloutCallsOutVehicleNullingAndProtectedRuntimeEvidenceGate() throws Exception {
        String vehicleMigrationSql = Files.readString(VEHICLE_MIGRATION);
        String rollout = Files.readString(ROLLOUT);

        assertThat(vehicleMigrationSql).contains("SET assigned_driver_id = NULL");
        assertThat(rollout)
                .contains("V20260711_3__equipment_responsible_employee_identity.sql")
                .contains("remote commits", "Flyway state", "pending migrations")
                .contains("VALID_EMPLOYEE", "LEGACY_USER_WITH_UNIQUE_EMPLOYEE", "AMBIGUOUS", "UNRESOLVED")
                .contains("validated FK definitions", "User-only", "actor-field proof")
                .contains("GET API checks", "UI checks")
                .contains("BLOCKED");
    }
}
