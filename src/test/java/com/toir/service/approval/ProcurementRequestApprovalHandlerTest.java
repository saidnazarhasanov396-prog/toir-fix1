package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.BudgetAllocationStatus;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.exception.RestException;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.service.finance.BudgetCommitmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcurementRequestApprovalHandlerTest {

    @Mock
    ProcurementRequestRepository procurementRequestRepository;


    @Mock
    BudgetCommitmentService budgetCommitmentService;

    @InjectMocks
    ProcurementRequestApprovalHandler handler;

    private UUID requestId;
    private UUID budgetLineId;
    private ProcurementRequest request;

    @BeforeEach
    void setUp() {
        requestId = UUID.randomUUID();
        budgetLineId = UUID.randomUUID();
        request = new ProcurementRequest();
        request.setId(requestId);
        request.setStatus(ProcurementRequestStatus.SUBMITTED);
        request.setBudgetLineId(budgetLineId);
        request.setBudgetAllocationStatus(BudgetAllocationStatus.ALLOCATED);
        request.setTotalEstimatedCost(500);
        when(procurementRequestRepository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));
        when(procurementRequestRepository.save(any(ProcurementRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void approveCommitsBudgetWhenRequestIsAllocated() {
        handler.execute(approval(ApprovalActionType.APPROVE));

        verify(budgetCommitmentService).commitBudget(
                eq(budgetLineId),
                eq(500.0),
                eq("PROCUREMENT_REQUEST"),
                eq(requestId),
                any(),
                eq("Budget commitment on procurement approval")
        );
    }

    @Test
    void approveRejectsWhenBudgetIsNotAllocated() {
        request.setBudgetAllocationStatus(BudgetAllocationStatus.UNALLOCATED);
        request.setBudgetLineId(null);

        assertThatThrownBy(() -> handler.execute(approval(ApprovalActionType.APPROVE)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("must be allocated to a budget line before approval");

        verify(budgetCommitmentService, never()).commitBudget(any(), any(Double.class), any(), any(), any(), any());
    }

    private ApprovalRequest approval(ApprovalActionType actionType) {
        ApprovalRequest approval = new ApprovalRequest();
        approval.setTargetType(ApprovalTargetType.PROCUREMENT_REQUEST);
        approval.setActionType(actionType);
        approval.setTargetId(requestId);
        ApprovalStep step = new ApprovalStep();
        step.setStepNumber(1);
        step.setDecision(ApprovalDecision.APPROVED);
        step.setApproverId(UUID.randomUUID());
        approval.setSteps(List.of(step));
        return approval;
    }
}
