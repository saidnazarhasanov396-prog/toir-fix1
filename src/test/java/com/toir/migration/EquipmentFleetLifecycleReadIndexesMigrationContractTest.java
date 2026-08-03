package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentFleetLifecycleReadIndexesMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260803_2__equipment_fleet_lifecycle_read_indexes.sql"
    );

    @Test
    void migrationAddsPartialIndexesForRepairAndLatestReadingLookups() throws Exception {
        String sql = Files.readString(MIGRATION)
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();

        assertThat(sql).contains(
                "create index if not exists idx_work_orders_fleet_lifecycle_repairs "
                        + "on work_orders (equipment_id, completed_at, id) "
                        + "where is_deleted = false and work_type = 'repair' "
                        + "and status in ('completed', 'closed') and completed_at is not null;",
                "create index if not exists idx_meter_readings_fleet_lifecycle_latest "
                        + "on meter_readings (meter_id, read_at desc, id desc) "
                        + "where is_deleted = false;"
        );
    }
}
