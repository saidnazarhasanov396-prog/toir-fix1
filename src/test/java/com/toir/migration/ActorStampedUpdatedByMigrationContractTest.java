package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActorStampedUpdatedByMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260610_1__actor_stamped_updated_by.sql"
    );

    @Test
    void migrationAddsNullableUpdatedByOnlyToScopedActorStampedTables() throws Exception {
        assertThat(Files.exists(MIGRATION)).isTrue();
        String sql = Files.readString(MIGRATION).toLowerCase();

        List<String> includedTables = List.of(
                "work_orders",
                "ppr_plans",
                "defect_lists",
                "stock_movements",
                "contractor_works",
                "actual_cost_review_route_overrides",
                "regulation_change_proposals"
        );

        for (String table : includedTables) {
            assertThat(sql).contains("alter table " + table);
        }
        assertThat(sql).contains("add column if not exists updated_by_id uuid");
        assertThat(sql).doesNotContain("not null");
        assertThat(sql).doesNotContain("default");
        assertThat(sql).doesNotContain("references");
        assertThat(sql).doesNotContain("create index");
        assertThat(sql).doesNotContain("audit_logs");
        assertThat(sql).doesNotContain("users");
        assertThat(sql).doesNotContain("roles");
        assertThat(sql).doesNotContain("uploaded_by_id");
    }
}
