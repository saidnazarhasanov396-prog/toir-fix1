package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentUsageSessionsMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260618_3__equipment_usage_sessions.sql");

    @Test
    void migrationCreatesGenericEquipmentUsageSessionsWithOpenSessionGuards() throws Exception {
        assertThat(Files.exists(MIGRATION)).isTrue();
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS equipment_usage_sessions");
        assertThat(sql).contains("FOREIGN KEY (equipment_id) REFERENCES equipment(id)");
        assertThat(sql).contains("FOREIGN KEY (operator_employee_id) REFERENCES hr_employees(id)");
        assertThat(sql).contains("CHECK (status IN ('OPEN', 'RETURNED'))");
        assertThat(sql).contains("chk_equipment_usage_sessions_return_after_start");
        assertThat(sql).contains("chk_equipment_usage_sessions_meter_value");
        assertThat(sql).contains("ux_equipment_usage_sessions_open_equipment");
        assertThat(sql).contains("ux_equipment_usage_sessions_open_operator");
    }

    @Test
    void migrationBackfillsVehicleDrivingSessionsIntoGenericUsageSessions() throws Exception {
        assertThat(Files.exists(MIGRATION)).isTrue();
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("INSERT INTO equipment_usage_sessions");
        assertThat(sql).contains("FROM vehicle_driving_sessions vds");
        assertThat(sql).contains("operator_employee_id");
        assertThat(sql).contains("driver_employee_id");
        assertThat(sql).contains("ON CONFLICT (id) DO NOTHING");
    }
}
