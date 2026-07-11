package com.toir.migration;

import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RepairCampaignCoreMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260712_1__repair_campaign_core.sql");

    @Test
    void migrationAddsDedicatedLifecycleMetadataAndConservativeBackfill() throws Exception {
        assertThat(MIGRATION).exists();
        String sql = Files.readString(MIGRATION).toLowerCase().replaceAll("\\s+", " ");

        for (String status : List.of(
                "DRAFT", "SCOPE_FORMATION", "RESOURCE_CHECK", "PENDING_APPROVAL", "APPROVED",
                "PREPARATION", "IN_PROGRESS", "SUSPENDED", "COMPLETED", "CLOSING", "CLOSED", "CANCELLED")) {
            assertThat(sql).contains("'" + status.toLowerCase() + "'");
        }
        for (String column : List.of(
                "campaign_type varchar(64)", "responsible_employee_id uuid", "priority varchar(32)",
                "objective text", "approval_scope_version bigint", "approval_scope_hash varchar(128)",
                "approved_at timestamptz", "preparation_started_at timestamptz", "started_at timestamptz",
                "suspended_at timestamptz", "completed_at timestamptz", "closing_started_at timestamptz",
                "closed_at timestamptz", "cancelled_at timestamptz", "suspended_from_status varchar(32)",
                "closure_version bigint")) {
            assertThat(sql).contains(column);
        }
        assertThat(sql)
                .contains("repair_campaign_status_history")
                .contains("foreign key (responsible_employee_id) references hr_employees(id)")
                .contains("legacy_remediation_required")
                .contains("status = 'scope_formation'")
                .doesNotContain("status = 'approved'")
                .doesNotContain("status = 'in_progress'");
    }

    @Test
    void migrationDefinesNamedConstraintsAndActiveCodeUniqueness() throws Exception {
        assertThat(MIGRATION).exists();
        String sql = Files.readString(MIGRATION).toLowerCase().replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("constraint chk_repair_campaign_status")
                .contains("constraint chk_repair_campaign_priority")
                .contains("constraint chk_repair_campaign_dates")
                .contains("constraint chk_repair_campaign_approval_scope")
                .contains("create unique index uq_repair_campaigns_active_code on repair_campaigns (code) where is_deleted = false")
                .contains("constraint fk_repair_campaign_status_history_campaign")
                .contains("constraint fk_repair_campaign_status_history_actor");
    }

    @Test
    void dedicatedEnumsAndMappingsMatchTheSchema() throws Exception {
        assertEnumValues("com.toir.enums.RepairCampaignStatus",
                "DRAFT", "SCOPE_FORMATION", "RESOURCE_CHECK", "PENDING_APPROVAL", "APPROVED",
                "PREPARATION", "IN_PROGRESS", "SUSPENDED", "COMPLETED", "CLOSING", "CLOSED", "CANCELLED");
        assertEnumValues("com.toir.enums.RepairCampaignPriority", "LOW", "MEDIUM", "HIGH", "CRITICAL");
        assertEnumValues("com.toir.enums.RepairCampaignStageStatus",
                "DRAFT", "APPROVED", "IN_PROGRESS", "COMPLETED", "CLOSED", "CANCELLED");

        Class<?> aggregate = Class.forName("com.toir.entity.repair.RepairCampaign");
        assertThat(aggregate.getDeclaredField("version").getAnnotation(Version.class)).isNotNull();
        for (String fieldName : List.of(
                "campaignType", "responsibleEmployeeId", "priority", "objective",
                "approvalScopeVersion", "approvalScopeHash", "approvedAt", "preparationStartedAt",
                "startedAt", "suspendedAt", "completedAt", "closingStartedAt", "closedAt",
                "cancelledAt", "suspendedFromStatus", "closureVersion")) {
            Field field = aggregate.getDeclaredField(fieldName);
            assertThat(field.getAnnotation(Column.class)).as(fieldName).isNotNull();
        }
        assertThat(aggregate.getDeclaredField("priority").getAnnotation(Enumerated.class)).isNotNull();
        assertThat(aggregate.getDeclaredField("suspendedFromStatus").getAnnotation(Enumerated.class)).isNotNull();

        Class<?> history = Class.forName("com.toir.entity.repair.RepairCampaignStatusHistory");
        for (String fieldName : List.of(
                "repairCampaignId", "fromStatus", "toStatus", "actorId", "reason",
                "scopeVersion", "windowVersion", "correlationKey", "occurredAt")) {
            assertThat(history.getDeclaredField(fieldName)).isNotNull();
        }

        Field stageStatus = Class.forName("com.toir.entity.repair.RepairCampaignStage")
                .getDeclaredField("status");
        assertThat(stageStatus.getType().getName()).isEqualTo("com.toir.enums.RepairCampaignStageStatus");
        Field stageDtoStatus = Class.forName("com.toir.dto.repaircampaign.RepairCampaignStageDto")
                .getDeclaredField("status");
        assertThat(stageDtoStatus.getType().getName()).isEqualTo("com.toir.enums.RepairCampaignStageStatus");

        String baseline = Files.readString(Path.of("src/main/resources/db/migration/B20260523_7__schema_baseline.sql"));
        String stageConstraint = baseline.substring(
                baseline.indexOf("CONSTRAINT repair_campaign_stages_status_check"),
                baseline.indexOf(";", baseline.indexOf("CONSTRAINT repair_campaign_stages_status_check")));
        assertThat(stageConstraint).contains("DRAFT", "APPROVED", "IN_PROGRESS", "COMPLETED", "CLOSED", "CANCELLED")
                .doesNotContain("SCOPE_FORMATION", "RESOURCE_CHECK", "PENDING_APPROVAL", "PREPARATION", "SUSPENDED", "CLOSING");
    }

    private static void assertEnumValues(String className, String... expected) throws Exception {
        Class<?> enumType = Class.forName(className);
        assertThat(enumType.isEnum()).isTrue();
        assertThat(Arrays.stream(enumType.getEnumConstants()).map(Object::toString))
                .containsExactly(expected);
    }
}
