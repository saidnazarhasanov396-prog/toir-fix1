package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderPerformerEmployeeMigrationContractTest {

    @Test
    void migrationAddsCanonicalEmployeeAndOnlyBackfillsUniqueUserMatches() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V20260731_2__work_order_performer_employee.sql"
        );

        String sql = Files.readString(migration).toLowerCase();
        assertThat(sql).contains("add column if not exists performer_employee_id uuid");
        assertThat(sql).contains("create index if not exists idx_work_orders_performer_employee_id");
        assertThat(sql).contains("foreign key (performer_employee_id) references hr_employees (id)");
        assertThat(sql).contains("having count(*) = 1");
        assertThat(sql).contains("e.is_deleted = false");
        assertThat(sql).doesNotContain("limit 1");
    }
}
