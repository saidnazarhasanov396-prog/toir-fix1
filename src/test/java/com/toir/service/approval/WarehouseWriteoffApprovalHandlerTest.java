package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalTargetType;
import com.toir.service.warehouse.WarehouseQualityService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WarehouseWriteoffApprovalHandlerTest {

    private final WarehouseQualityService warehouseQualityService = mock(WarehouseQualityService.class);
    private final WarehouseWriteoffApprovalHandler handler = new WarehouseWriteoffApprovalHandler(warehouseQualityService);

    @Test
    void supportsWarehouseWriteoffApproveAndReject() {
        assertThat(handler.supports(ApprovalTargetType.WAREHOUSE_WRITEOFF, ApprovalActionType.APPROVE)).isTrue();
        assertThat(handler.supports(ApprovalTargetType.WAREHOUSE_WRITEOFF, ApprovalActionType.REJECT)).isTrue();
        assertThat(handler.supports(ApprovalTargetType.WORK_ORDER, ApprovalActionType.APPROVE)).isFalse();
    }

    @Test
    void returnsTerminalStatusPayload() {
        ApprovalRequest request = new ApprovalRequest();
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        request.setTargetId(targetId);
        request.setActionType(ApprovalActionType.APPROVE);
        request.setSteps(List.of(step(actorId, ApprovalDecision.APPROVED, "ok")));
        assertThat(handler.execute(request)).contains("APPROVED");
        verify(warehouseQualityService).approveFromApprovalWorkflow(targetId, actorId, "ok");

        request.setActionType(ApprovalActionType.REJECT);
        request.setSteps(List.of(step(actorId, ApprovalDecision.REJECTED, "no")));
        assertThat(handler.execute(request)).contains("REJECTED");
        verify(warehouseQualityService).rejectFromApprovalWorkflow(targetId, actorId, "no");
    }

    private ApprovalStep step(UUID actorId, ApprovalDecision decision, String comment) {
        ApprovalStep step = new ApprovalStep();
        step.setStepNumber(1);
        step.setDecision(decision);
        step.setDecidedById(actorId);
        step.setComment(comment);
        return step;
    }
}
