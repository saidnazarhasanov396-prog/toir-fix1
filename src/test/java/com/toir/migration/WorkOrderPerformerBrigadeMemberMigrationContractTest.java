package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderPerformerBrigadeMemberMigrationContractTest {

    @Test
    void migrationMustDefineBrigadeMemberColumnIndexAndForeignKey() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V20260606_5__work_order_performer_brigade_member.sql"
        );
        assertThat(Files.exists(migration)).isTrue();

        String sql = Files.readString(migration).toLowerCase();
        assertThat(sql).contains("add column if not exists brigade_member_id uuid");
        assertThat(sql).contains("create index if not exists idx_work_orders_brigade_member_id");
        assertThat(sql).contains("foreign key (brigade_member_id) references brigade_members (id)");
    }
}
