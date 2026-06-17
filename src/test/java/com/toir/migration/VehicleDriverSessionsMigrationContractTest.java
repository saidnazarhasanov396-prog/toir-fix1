package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleDriverSessionsMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260616_4__vehicle_driver_sessions.sql");

    @Test
    void migrationCreatesEmployeeWorkRolesAndSeedsDefaultCapabilities() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS employee_work_roles");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS hr_employee_work_role_assignments");
        assertThat(sql).contains("'DRIVER'");
        assertThat(sql).contains("'MECHANIC'");
        assertThat(sql).contains("'ELECTRICIAN'");
        assertThat(sql).contains("'PLUMBER'");
        assertThat(sql).contains("ON CONFLICT (employee_id, work_role_id) DO UPDATE");
    }

    @Test
    void migrationBackfillsVehicleAssignedDriverFromEmployeeUserIdAndAddsEmployeeFk() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("SET assigned_driver_id = e.id");
        assertThat(sql).contains("WHERE vd.assigned_driver_id = e.user_id");
        assertThat(sql).contains("SET assigned_driver_id = NULL");
        assertThat(sql).contains("fk_vehicle_details_assigned_driver_employee");
        assertThat(sql).contains("FOREIGN KEY (assigned_driver_id) REFERENCES hr_employees(id)");
        assertThat(sql).contains("ux_vehicle_details_assigned_driver_active");
    }

    @Test
    void migrationCreatesDrivingSessionsWithOpenSessionGuards() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS vehicle_driving_sessions");
        assertThat(sql).contains("FOREIGN KEY (equipment_id) REFERENCES equipment(id)");
        assertThat(sql).contains("FOREIGN KEY (driver_employee_id) REFERENCES hr_employees(id)");
        assertThat(sql).contains("CHECK (status IN ('OPEN', 'RETURNED'))");
        assertThat(sql).contains("chk_vehicle_driving_sessions_return_after_start");
        assertThat(sql).contains("chk_vehicle_driving_sessions_odometer");
        assertThat(sql).contains("chk_vehicle_driving_sessions_engine_hours");
        assertThat(sql).contains("ux_vehicle_driving_sessions_open_equipment");
        assertThat(sql).contains("ux_vehicle_driving_sessions_open_driver");
    }
}
