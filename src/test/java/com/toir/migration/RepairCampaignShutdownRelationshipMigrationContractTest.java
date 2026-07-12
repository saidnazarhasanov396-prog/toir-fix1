package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RepairCampaignShutdownRelationshipMigrationContractTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260712_3__repair_campaign_shutdown_relationship.sql");

    @Test
    void migrationCreatesRelationshipAndCanonicalWindowsWithNamedConstraints() throws Exception {
        assertThat(MIGRATION).exists();
        String sql = Files.readString(MIGRATION).toLowerCase().replaceAll("\\s+", " ");
        assertThat(sql)
                .contains("create table planned_shutdown_campaigns")
                .contains("constraint fk_planned_shutdown_campaigns_shutdown")
                .contains("constraint fk_planned_shutdown_campaigns_campaign")
                .contains("create unique index uq_planned_shutdown_campaigns_active_pair")
                .contains("create table repair_campaign_work_item_windows")
                .contains("constraint fk_repair_campaign_work_item_windows_campaign_item")
                .contains("constraint fk_repair_campaign_work_item_windows_shutdown_item_owner")
                .contains("create unique index uq_repair_campaign_work_item_windows_active_identity")
                .contains("where is_deleted = false");
    }

    @Test
    void entityRepositoryDtoAndServiceContractsExist() throws Exception {
        for (String type : new String[]{
                "com.toir.entity.plannedshutdown.PlannedShutdownCampaignLink",
                "com.toir.entity.repair.RepairCampaignWorkItemWindow",
                "com.toir.repository.plannedshutdown.PlannedShutdownCampaignLinkRepository",
                "com.toir.repository.repair.RepairCampaignWorkItemWindowRepository",
                "com.toir.dto.repaircampaign.RepairCampaignShutdownLinkRequest",
                "com.toir.dto.repaircampaign.RepairCampaignShutdownLinkResponse",
                "com.toir.dto.repaircampaign.RepairCampaignWorkItemWindowRequest",
                "com.toir.dto.repaircampaign.RepairCampaignWorkItemWindowResponse",
                "com.toir.service.repair.RepairCampaignShutdownLinkService"}) {
            assertThat(Class.forName(type)).as(type).isNotNull();
        }
    }
}
