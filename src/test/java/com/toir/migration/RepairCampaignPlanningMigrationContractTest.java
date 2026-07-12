package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RepairCampaignPlanningMigrationContractTest {
    @Test
    void v4DefinesPlanningOwnershipAndIntegrityContracts() throws Exception {
        Path path = Path.of("src/main/resources/db/migration/V20260712_4__repair_campaign_planning.sql");
        assertThat(path).exists();
        String sql = Files.readString(path);
        assertThat(sql).contains("repair_campaign_work_dependencies", "repair_campaign_resource_assignments",
                "ADD COLUMN priority", "chk_repair_campaign_work_item_priority",
                "uq_repair_campaign_dependencies_active_edge", "chk_repair_campaign_dependency_not_self",
                "chk_repair_campaign_resource_exactly_one", "chk_repair_campaign_resource_window",
                "fk_repair_campaign_dependency_predecessor", "fk_repair_campaign_dependency_successor",
                "fk_repair_campaign_resource_work_item");
    }

    @Test
    void planningModelAndPoliciesExist() throws Exception {
        for (String type : new String[]{
                "com.toir.entity.repair.RepairCampaignWorkDependency",
                "com.toir.entity.repair.RepairCampaignResourceAssignment",
                "com.toir.service.repair.RepairCampaignDependencyPolicy",
                "com.toir.service.repair.RepairCampaignResourcePolicy",
                "com.toir.dto.repaircampaign.RepairCampaignPlanningAssessment"}) {
            assertThat(Class.forName(type)).isNotNull();
        }
    }
}
