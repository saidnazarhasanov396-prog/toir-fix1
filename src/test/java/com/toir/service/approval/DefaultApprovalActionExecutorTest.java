package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalTargetType;
import com.toir.exception.RestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultApprovalActionExecutorTest {

    @Test
    void executeFailsFastWhenNoHandlerMatches() {
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(ApprovalTargetType.MAINTENANCE_REGULATION);
        request.setActionType(ApprovalActionType.APPROVE);
        DefaultApprovalActionExecutor executor = new DefaultApprovalActionExecutor(List.of());

        assertThatThrownBy(() -> executor.execute(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("No approval action handler registered");
    }

    @Test
    void workOrderRejectIsHandledWithoutFinalApprovalAction() {
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(ApprovalTargetType.WORK_ORDER);
        request.setActionType(ApprovalActionType.REJECT);
        DefaultApprovalActionExecutor executor = new DefaultApprovalActionExecutor(List.of(new WorkOrderApprovalHandler(null)));

        assertThat(executor.execute(request)).contains("REJECTED");
    }

    @Test
    void documentTypeWinsWhenLegacyAliasAndTargetTypeDisagree() {
        ApprovalRequest request = new ApprovalRequest();
        request.setDocumentType("WORK_ORDER");
        request.setDocumentType("PPR_PLAN");
        request.setActionType(ApprovalActionType.REJECT);
        DefaultApprovalActionExecutor executor = new DefaultApprovalActionExecutor(List.of(
                new WorkOrderApprovalHandler(null),
                new PprPlanApprovalHandler(null)
        ));

        assertThat(executor.execute(request)).contains("REJECTED");
    }
}
