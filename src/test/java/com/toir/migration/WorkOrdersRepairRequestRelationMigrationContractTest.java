package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrdersRepairRequestRelationMigrationContractTest {

    @Test
    void migrationMustDefineColumnIndexOrphanGuardAndForeignKey() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V20260515_1__work_orders_repair_request_fk.sql"
        );
        assertThat(Files.exists(migration)).isTrue();

        String sql = Files.readString(migration).toLowerCase();
        assertThat(sql).contains("add column if not exists repair_request_id uuid");
        assertThat(sql).contains("create index if not exists idx_work_orders_repair_request_id");
        assertThat(sql).contains("where is_deleted = false");
        assertThat(sql).contains("left join repair_requests");
        assertThat(sql).contains("raise exception");
        assertThat(sql).contains("foreign key (repair_request_id) references repair_requests (id)");
    }
}
