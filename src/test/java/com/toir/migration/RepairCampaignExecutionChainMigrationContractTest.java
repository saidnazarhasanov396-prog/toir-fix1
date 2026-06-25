package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RepairCampaignExecutionChainMigrationContractTest {

    @Test
    void migrationAddsCampaignExecutionChainColumnsAndConstraints() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260625_4__repair_campaign_execution_chain.sql"
        )).toLowerCase();

        assertThat(sql).contains("scope_type");
        assertThat(sql).contains("equipment_type_id");
        assertThat(sql).contains("repair_campaign_departments");
        assertThat(sql).contains("repair_campaign_id");
        assertThat(sql).contains("repair_campaign_stage_id");
        assertThat(sql).contains("fk_work_orders_repair_campaign");
        assertThat(sql).contains("fk_work_orders_repair_campaign_stage");
        assertThat(sql).contains("idx_work_orders_repair_campaign_id");
        assertThat(sql).contains("idx_work_orders_repair_campaign_stage_id");
        assertThat(sql).contains("equipment_type");
        assertThat(sql).contains("cross_department");
        assertThat(sql).contains("owner");
        assertThat(sql).contains("executor");
        assertThat(sql).contains("participant");
        assertThat(sql).contains("approver");
    }
}
