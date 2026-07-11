package com.toir.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.repaircampaign.RepairCampaignWorkItemRequest;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RepairCampaignWorkSourceMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260712_2__repair_campaign_work_sources.sql");

    @Test
    void migrationCreatesCanonicalWorkItemsWithNamedIdentityConstraints() throws Exception {
        assertThat(MIGRATION).exists();
        String sql = Files.readString(MIGRATION).toLowerCase().replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("alter table repair_campaigns add column if not exists version bigint not null default 0")
                .contains("create table repair_campaign_work_items")
                .contains("constraint fk_repair_campaign_work_items_campaign")
                .contains("foreign key (repair_campaign_id) references repair_campaigns(id)")
                .contains("constraint fk_repair_campaign_work_items_equipment")
                .contains("foreign key (equipment_id) references equipment(id)")
                .contains("constraint chk_repair_campaign_work_items_source_type")
                .contains("constraint chk_repair_campaign_work_items_source_identity")
                .contains("'replan_required'")
                .contains("constraint chk_repair_campaign_work_items_order")
                .contains("create unique index uq_repair_campaign_work_items_active_source")
                .contains("create unique index uq_repair_campaign_work_items_active_order")
                .contains("alter table planned_shutdown_work_items")
                .contains("'repair_request'")
                .contains("'inspection_round'")
                .contains("where is_deleted = false");

        for (String source : new String[]{
                "manual", "defect", "ppr", "repair_request", "inspection_round", "work_order"}) {
            assertThat(sql).contains("'" + source + "'");
        }
    }

    @Test
    void sourceEnumAndEntityMappingMatchTheSchema() throws Exception {
        Class<?> sourceType = Class.forName("com.toir.enums.RepairCampaignWorkItemSourceType");
        assertThat(Arrays.stream(sourceType.getEnumConstants()).map(Object::toString))
                .containsExactly("MANUAL", "DEFECT", "PPR", "REPAIR_REQUEST", "INSPECTION_ROUND", "WORK_ORDER");
        Class<?> statusType = Class.forName("com.toir.enums.RepairCampaignWorkItemStatus");
        assertThat(Arrays.stream(statusType.getEnumConstants()).map(Object::toString))
                .containsExactly("PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED", "REPLAN_REQUIRED");

        Class<?> entity = Class.forName("com.toir.entity.repair.RepairCampaignWorkItem");
        assertThat(entity.getDeclaredField("campaign").getAnnotation(ManyToOne.class)).isNotNull();
        assertThat(entity.getDeclaredField("sourceType").getAnnotation(Enumerated.class)).isNotNull();
        assertThat(entity.getDeclaredField("status").getAnnotation(Enumerated.class)).isNotNull();
        for (String field : new String[]{
                "sourceId", "equipmentId", "title", "status", "orderNumber", "notes"}) {
            assertThat(entity.getDeclaredField(field)).as(field).isNotNull();
        }
    }

    @Test
    void genericRequestDoesNotExposeServerOwnedStatusEvenWhenJsonAttemptsIt() throws Exception {
        assertThat(Arrays.stream(RepairCampaignWorkItemRequest.class.getRecordComponents())
                .map(component -> component.getName())).doesNotContain("status");
        UUID equipmentId = UUID.randomUUID();
        String payload = """
                {"version":1,"sourceType":"MANUAL","sourceId":null,"equipmentId":"%s",
                 "title":"manual","status":"REPLAN_REQUIRED","orderNumber":0,"notes":null}
                """.formatted(equipmentId);
        RepairCampaignWorkItemRequest request = new ObjectMapper().readValue(payload, RepairCampaignWorkItemRequest.class);
        assertThat(new ObjectMapper().writeValueAsString(request)).doesNotContain("status", "REPLAN_REQUIRED");
    }
}
