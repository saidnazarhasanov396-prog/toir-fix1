package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalTargetType;
import com.toir.service.WorkOrderService;
import com.toir.service.PlannedShutdownService;
import com.toir.service.maintanance.MaintenanceRegulationService;
import com.toir.service.maintanance.MaintenanceScheduleMaterializationService;
import com.toir.service.repair.RepairRequestService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApprovalActionHandlerTest {

    @Test
    void workOrderApproveCallsFinalizerWithLastApprover() {
        WorkOrderService workOrderService = mock(WorkOrderService.class);
        UUID targetId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();

        new WorkOrderApprovalHandler(workOrderService).execute(request(
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE,
                targetId,
                approvedStep(1, UUID.randomUUID()),
                approvedStep(2, approverId)
        ));

        verify(workOrderService).finalizeApprovalFromApprovalRequest(targetId, approverId);
    }

    @Test
    void pprPlanApproveCallsFinalizerWithLastApprover() {
        MaintenanceScheduleMaterializationService materializationService =
                mock(MaintenanceScheduleMaterializationService.class);
        UUID targetId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalRequest request = request(
                ApprovalTargetType.PPR_PLAN,
                ApprovalActionType.APPROVE,
                targetId,
                approvedStep(1, approverId));

        new PprPlanApprovalHandler(materializationService).execute(request);

        verify(materializationService).finalizeApproval(request, approverId);
    }

    @Test
    void repairRequestRejectCallsRejectFinalizerWithStepComment() {
        RepairRequestService repairRequestService = mock(RepairRequestService.class);
        UUID targetId = UUID.randomUUID();
        ApprovalStep rejected = new ApprovalStep();
        rejected.setStepNumber(1);
        rejected.setDecision(ApprovalDecision.REJECTED);
        rejected.setComment("Missing meter readings");

        new RepairRequestApprovalHandler(repairRequestService).execute(request(
                ApprovalTargetType.REPAIR_REQUEST,
                ApprovalActionType.REJECT,
                targetId,
                rejected
        ));

        verify(repairRequestService).finalizeRejectionFromApprovalRequest(targetId, "Missing meter readings");
    }

    @Test
    void maintenanceRegulationApproveAndRejectCallFinalizers() {
        MaintenanceRegulationService maintenanceRegulationService = mock(MaintenanceRegulationService.class);
        UUID targetId = UUID.randomUUID();
        MaintenanceRegulationApprovalHandler handler =
                new MaintenanceRegulationApprovalHandler(maintenanceRegulationService);

        handler.execute(request(ApprovalTargetType.MAINTENANCE_REGULATION, ApprovalActionType.APPROVE, targetId));
        handler.execute(request(ApprovalTargetType.MAINTENANCE_REGULATION, ApprovalActionType.REJECT, targetId));

        verify(maintenanceRegulationService).finalizeApprovalFromApprovalRequest(targetId);
        verify(maintenanceRegulationService).finalizeRejectionFromApprovalRequest(targetId);
    }

    @Test
    void plannedShutdownPassesCanonicalApprovalRequestToFinalizer() {
        PlannedShutdownService plannedShutdownService = mock(PlannedShutdownService.class);
        UUID targetId = UUID.randomUUID();
        ApprovalRequest request = request(ApprovalTargetType.PLANNED_SHUTDOWN, ApprovalActionType.APPROVE, targetId,
                approvedStep(1, UUID.randomUUID()), approvedStep(2, UUID.randomUUID()));

        new PlannedShutdownApprovalHandler(plannedShutdownService).execute(request);

        verify(plannedShutdownService).finalizeApprovalFromApprovalRequest(targetId, request);
    }

    private ApprovalRequest request(ApprovalTargetType targetType,
                                    ApprovalActionType actionType,
                                    UUID targetId,
                                    ApprovalStep... steps) {
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(targetType);
        request.setActionType(actionType);
        request.setTargetId(targetId);
        for (ApprovalStep step : steps) {
            step.setRequest(request);
            request.getSteps().add(step);
        }
        return request;
    }

    private ApprovalStep approvedStep(int number, UUID decidedById) {
        ApprovalStep step = new ApprovalStep();
        step.setStepNumber(number);
        step.setDecision(ApprovalDecision.APPROVED);
        step.setDecidedById(decidedById);
        return step;
    }
}
