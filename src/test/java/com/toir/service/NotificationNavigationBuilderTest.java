package com.toir.service;

import com.toir.enums.NotificationEventType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationNavigationBuilderTest {

    private final NotificationNavigationBuilder builder = new NotificationNavigationBuilder();

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
    void pprTaskIncludesPlanAndExactlyOneWorkOrderSecondaryAction() {
        UUID taskId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();

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
                .containsEntry("taskId", taskId.toString());
        assertThat((List<?>) target.metadata().get("secondaryActions")).hasSize(1);
    }

    @Test
    void pprTaskDoesNotChooseAnArbitraryWorkOrderWhenMultipleExist() {
        NotificationNavigation target = builder.forPprTask(
                NotificationEventType.PPR_TASK_OVERDUE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(UUID.randomUUID(), UUID.randomUUID())
        );

        assertThat(target.metadata()).doesNotContainKey("secondaryActions");
        assertThat(target.actionUrl()).startsWith("/ppr-calendar/");
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
}
