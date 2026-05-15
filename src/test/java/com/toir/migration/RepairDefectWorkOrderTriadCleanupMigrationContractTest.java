package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RepairDefectWorkOrderTriadCleanupMigrationContractTest {

    @Test
    void migrationMustRenameDropMigrateAndHardenTriadRelations() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V20260516_1__repair_defect_work_order_triad_cleanup.sql"
        );
        assertThat(Files.exists(migration)).isTrue();

        String sql = Files.readString(migration).toLowerCase();

        assertThat(sql).contains("rename column request_id to repair_request_id");
        assertThat(sql).contains("set repair_request_id = request_id");
        assertThat(sql).contains("drop column request_id");

        assertThat(sql).contains("add column if not exists defect_id uuid");
        assertThat(sql).contains("set defect_id = d.id");
        assertThat(sql).contains("from defects d");
        assertThat(sql).contains("d.work_order_id = wo.id");
        assertThat(sql).contains("drop column if exists work_order_id");

        assertThat(sql).contains("create index if not exists idx_defects_repair_request_id");
        assertThat(sql).contains("create index if not exists idx_work_orders_repair_request_id");
        assertThat(sql).contains("create index if not exists idx_work_orders_defect_id");
        assertThat(sql).contains("where is_deleted = false");

        assertThat(sql).contains("left join repair_requests rr on rr.id = d.repair_request_id");
        assertThat(sql).contains("left join repair_requests rr on rr.id = w.repair_request_id");
        assertThat(sql).contains("left join defects d on d.id = w.defect_id");
        assertThat(sql).contains("raise exception");
        assertThat(sql).contains("fk_defects_repair_request");
        assertThat(sql).contains("fk_work_orders_repair_request");
        assertThat(sql).contains("fk_work_orders_defect");

        assertThat(sql).contains("foreign key (repair_request_id) references repair_requests (id)");
        assertThat(sql).contains("foreign key (defect_id) references defects (id)");
    }
}
