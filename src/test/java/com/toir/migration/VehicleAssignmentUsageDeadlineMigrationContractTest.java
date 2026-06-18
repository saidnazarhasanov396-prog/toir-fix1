package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleAssignmentUsageDeadlineMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260618_9__vehicle_assignment_usage_deadline.sql");

    @Test
    void migrationAddsVehicleAssignmentLimitAndUsageDeadlineFields() throws Exception {
        assertThat(Files.exists(MIGRATION)).isTrue();
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("ALTER TABLE vehicle_details");
        assertThat(sql).contains("assigned_driver_usage_limit_minutes integer");
        assertThat(sql).contains("assigned_driver_assigned_by uuid");
        assertThat(sql).contains("assigned_driver_assigned_at timestamptz");
        assertThat(sql).contains("ALTER TABLE equipment_usage_sessions");
        assertThat(sql).contains("usage_limit_minutes integer");
        assertThat(sql).contains("due_at timestamptz");
        assertThat(sql).contains("assignment_actor_user_id uuid");
        assertThat(sql).contains("overdue_notified_at timestamptz");
        assertThat(sql).contains("idx_equipment_usage_sessions_open_due");
    }
}
