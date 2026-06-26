package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RepairCampaignBudgetLinksMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260626_1__repair_campaign_budget_links.sql"
    );

    @Test
    void migrationAddsCampaignBudgetLinksAndIndexes() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql).contains("maintenance_budget_id");
        assertThat(sql).contains("budget_line_id");
        assertThat(sql).contains("fk_repair_campaigns_maintenance_budget");
        assertThat(sql).contains("fk_repair_campaign_stages_budget_line");
        assertThat(sql).contains("fk_work_orders_budget_line");
        assertThat(sql).contains("idx_repair_campaigns_maintenance_budget_id");
        assertThat(sql).contains("idx_repair_campaign_stages_budget_line_id");
        assertThat(sql).contains("idx_work_orders_budget_line_id");
    }
}
