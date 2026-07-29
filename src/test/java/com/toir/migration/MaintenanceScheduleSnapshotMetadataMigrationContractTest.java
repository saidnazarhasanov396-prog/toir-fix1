package com.toir.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class MaintenanceScheduleSnapshotMetadataMigrationContractTest {

    @Test
    void backfillTemporarilyRemovesAndThenRestoresSnapshotImmutability()
            throws IOException {
        String sql = new ClassPathResource(
                "db/migration/"
                        + "V20260729_9__maintenance_schedule_snapshot_source_metadata.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        int dropTrigger = sql.indexOf(
                "DROP TRIGGER IF EXISTS trg_ms_calculation_items_immutable");
        int backfill = sql.indexOf(
                "UPDATE maintenance_schedule_calculation_items item");
        int restoreTrigger = sql.indexOf(
                "CREATE TRIGGER trg_ms_calculation_items_immutable");

        assertThat(dropTrigger).isGreaterThanOrEqualTo(0);
        assertThat(backfill).isGreaterThan(dropTrigger);
        assertThat(restoreTrigger).isGreaterThan(backfill);
        assertThat(sql)
                .contains("source_code_snapshot")
                .contains("source_name_snapshot")
                .contains("EXECUTE FUNCTION reject_ms_calculation_item_mutation()");
    }
}
