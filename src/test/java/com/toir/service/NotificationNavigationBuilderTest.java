package com.toir.service;

import com.toir.entity.PprPlan;
import com.toir.enums.MaterializationMode;
import com.toir.enums.NotificationEventType;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprPlanOrigin;
import com.toir.enums.TaskMaterializationStatus;
import com.toir.repository.PprPlanRepository;
import com.toir.service.pprcalendar.PprOperationalCalendarPolicy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationNavigationBuilderTest {

    @Mock
    private PprPlanRepository planRepository;

    private final PprOperationalCalendarPolicy operationalCalendarPolicy =
            new PprOperationalCalendarPolicy();

    private NotificationNavigationBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new NotificationNavigationBuilder(planRepository, operationalCalendarPolicy);
    }

    @Test
    void buildsCanonicalApprovalAndDefectRoutesFromLegacyAliases() {
        UUID approvalId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();

        assertThat(builder.forEntity(
                NotificationEventType.APPROVAL_REQUESTED,
                "ApprovalRequest",
                approvalId
        )).satisfies(target -> {
            assertThat(target.entityType()).isEqualTo(NotificationEntityTypes.APPROVAL_REQUEST);
            assertThat(target.actionUrl()).isEqualTo("/approvals/" + approvalId);
        });
        assertThat(builder.forEntity(
                NotificationEventType.DEFECT_CREATED_FROM_INSPECTION,
                "Defect",
                defectId
        ).actionUrl()).isEqualTo("/defects/" + defectId);
    }

    @Test
    void pprTaskOnOperationalPlanUsesCalendarRoute() {
        UUID taskId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        stubOperationalPlan(planId);

        NotificationNavigation target = builder.forPprTask(
                NotificationEventType.PPR_TASK_OVERDUE,
                taskId,
                planId,
                List.of(workOrderId)
        );

        assertThat(target.eventType()).isEqualTo("PPR_TASK_OVERDUE");
        assertThat(target.entityType()).isEqualTo(NotificationEntityTypes.PPR_TASK);
        assertThat(target.actionUrl()).isEqualTo(
                "/ppr-calendar/" + planId + "?taskId=" + taskId
        );
        assertThat(target.metadata())
                .containsEntry("planId", planId.toString())
                .containsEntry("taskId", taskId.toString())
                .containsEntry(
                        NotificationNavigationBuilder.METADATA_ROUTE_CONTEXT,
                        NotificationNavigationBuilder.ROUTE_CONTEXT_OPERATIONAL);
        assertThat((List<?>) target.metadata().get("secondaryActions")).hasSize(1);
    }

    @Test
    void pprTaskOnNonOperationalPlanUsesBuilderRoute() {
        UUID taskId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        when(planRepository.findByIdAndIsDeletedFalse(planId))
                .thenReturn(Optional.of(draftSchedule(planId)));

        NotificationNavigation target = builder.forPprTask(
                NotificationEventType.PPR_TASK_OVERDUE,
                taskId,
                planId,
                List.of()
        );

        assertThat(target.actionUrl()).isEqualTo(
                "/maintenance-schedule-builder/" + planId + "?taskId=" + taskId + "&draft=1"
        );
        assertThat(target.metadata()).containsEntry(
                NotificationNavigationBuilder.METADATA_ROUTE_CONTEXT,
                NotificationNavigationBuilder.ROUTE_CONTEXT_BUILDER);
    }

    @Test
    void pprTaskDefaultsToBuilderWhenPlanIsMissing() {
        UUID taskId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.empty());

        NotificationNavigation target = builder.forPprTask(
                NotificationEventType.PPR_TASK_OVERDUE,
                taskId,
                planId,
                List.of(UUID.randomUUID(), UUID.randomUUID())
        );

        assertThat(target.metadata()).doesNotContainKey("secondaryActions");
        assertThat(target.actionUrl()).isEqualTo(
                "/maintenance-schedule-builder/" + planId + "?taskId=" + taskId + "&draft=1"
        );
    }

    @Test
    void pprPlanEntityUsesOperationalCalendarWhenEligible() {
        UUID planId = UUID.randomUUID();
        stubOperationalPlan(planId);

        NotificationNavigation target = builder.forEntity(
                NotificationEventType.APPROVAL_APPROVED,
                NotificationEntityTypes.PPR_PLAN,
                planId
        );

        assertThat(target.actionUrl()).isEqualTo("/ppr-calendar/" + planId);
        assertThat(target.metadata()).containsEntry(
                NotificationNavigationBuilder.METADATA_ROUTE_CONTEXT,
                NotificationNavigationBuilder.ROUTE_CONTEXT_OPERATIONAL);
    }

    @Test
    void pprPlanningSessionUsesBuilderSessionRoute() {
        UUID sessionId = UUID.randomUUID();

        NotificationNavigation target = builder.forEntity(
                NotificationEventType.APPROVAL_APPROVED,
                NotificationEntityTypes.PPR_PLANNING_SESSION,
                sessionId
        );

        assertThat(target.actionUrl()).isEqualTo(
                "/maintenance-schedule-builder/sessions/" + sessionId
        );
    }

    @Test
    void unsupportedEntityKeepsIdentityWithoutFabricatingRoute() {
        NotificationNavigation target = builder.forEntity(
                NotificationEventType.CALIBRATION_OVERDUE,
                "CALIBRATION_RECORD",
                UUID.randomUUID()
        );

        assertThat(target.entityType()).isEqualTo("CALIBRATION_RECORD");
        assertThat(target.actionUrl()).isNull();
    }

    @Test
    void approvalResultUsesOwnerAsPrimaryAndApprovalAsSecondary() {
        UUID workOrderId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();

        NotificationNavigation target = builder.forApprovalResult(
                NotificationEventType.APPROVAL_APPROVED,
                NotificationEntityTypes.WORK_ORDER,
                workOrderId,
                approvalId
        );

        assertThat(target.actionUrl()).isEqualTo("/work-orders/" + workOrderId);
        assertThat(target.metadata()).containsEntry("approvalRequestId", approvalId.toString());
        assertThat((List<?>) target.metadata().get("secondaryActions")).hasSize(1);
    }

    @Test
    void sparePartDueUsesCanonicalEquipmentAttentionRouteAndStructuredMetadata() {
        UUID equipmentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID installationId = UUID.randomUUID();

        NotificationNavigation target = builder.forSparePartDue(
                NotificationEventType.SPARE_PART_DUE, equipmentId, eventId, installationId);

        assertThat(target.actionUrl()).isEqualTo(
                "/equipment/" + equipmentId
                        + "?tab=spareParts&view=attention&eventId=" + eventId
                        + "&installationId=" + installationId);
        assertThat(target.metadata())
                .containsEntry("equipmentId", equipmentId.toString())
                .containsEntry("eventId", eventId.toString())
                .containsEntry("installationId", installationId.toString());
    }

    private void stubOperationalPlan(UUID planId) {
        when(planRepository.findByIdAndIsDeletedFalse(planId))
                .thenReturn(Optional.of(materializedSchedule(planId)));
    }

    private static PprPlan materializedSchedule(UUID planId) {
        PprPlan plan = new PprPlan();
        plan.setId(planId);
        plan.setOrigin(PprPlanOrigin.MAINTENANCE_SCHEDULE);
        plan.setMaterializationMode(MaterializationMode.APPROVAL_FIRST);
        plan.setTaskMaterializationStatus(TaskMaterializationStatus.MATERIALIZED);
        plan.setStatus(PlanStatus.APPROVED);
        plan.setCalculationRevision(1L);
        plan.setApprovedRevision(1L);
        plan.setMaterializedRevision(1L);
        plan.setMaterializedTaskCount(1);
        return plan;
    }

    private static PprPlan draftSchedule(UUID planId) {
        PprPlan plan = materializedSchedule(planId);
        plan.setStatus(PlanStatus.CALCULATED);
        plan.setTaskMaterializationStatus(TaskMaterializationStatus.NOT_MATERIALIZED);
        return plan;
    }
}
