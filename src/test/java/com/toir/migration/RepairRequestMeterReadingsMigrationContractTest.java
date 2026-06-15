package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RepairRequestMeterReadingsMigrationContractTest {

    @Test
    void migrationAddsRepairContextColumnsToMeterReadings() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V20260615_1__repair_request_meter_readings.sql"
        );
        assertThat(Files.exists(migration)).isTrue();

        String sql = Files.readString(migration).toLowerCase();

        assertThat(sql).contains("alter table meter_readings");
        assertThat(sql).contains("repair_request_id uuid");
        assertThat(sql).contains("work_order_id uuid");
        assertThat(sql).contains("defect_id uuid");
        assertThat(sql).contains("reading_context");
        assertThat(sql).contains("failure_detected");
        assertThat(sql).contains("idx_meter_readings_repair_request");
    }
}
