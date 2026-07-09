package com.toir.service.finance;

import com.toir.entity.projects.BudgetEvent;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.enums.BudgetAllocationStatus;
import com.toir.enums.BudgetStatus;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.exception.RestException;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.projects.BudgetEventRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.ProcurementRequestService;
import com.toir.util.AuditBuilderService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcurementBudgetAllocationServiceTest {

    @Mock
    ProcurementRequestRepository procurementRequestRepository;

    @Mock
    BudgetLineRepository budgetLineRepository;

    @Mock
    BudgetEventRepository budgetEventRepository;

    @Mock
    BudgetCommitmentService budgetCommitmentService;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    ProcurementRequestService procurementRequestService;

    @InjectMocks
    ProcurementBudgetAllocationService service;

    private UUID requestId;
    private UUID budgetLineId;
    private ProcurementRequest request;
    private BudgetLine budgetLine;

    @BeforeEach
    void setUp() {
        requestId = UUID.randomUUID();
        budgetLineId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        request = new ProcurementRequest();
        request.setId(requestId);
        request.setStatus(ProcurementRequestStatus.SUBMITTED);
        request.setDepartmentId(departmentId);
        request.setBudgetAllocationStatus(BudgetAllocationStatus.UNALLOCATED);
        request.setTotalEstimatedCost(300);

        MaintenanceBudget budget = new MaintenanceBudget();
        budget.setId(UUID.randomUUID());
        budget.setDepartmentId(departmentId);
        budget.setStatus(BudgetStatus.APPROVED);

        budgetLine = new BudgetLine();
        budgetLine.setId(budgetLineId);
        budgetLine.setBudget(budget);
        budgetLine.setPlannedAmount(1_000);
        budgetLine.setActualAmount(0);
        budgetLine.setCommittedAmount(0);

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(procurementRequestRepository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));
        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)).thenReturn(Optional.of(budgetLine));
        when(procurementRequestRepository.save(any(ProcurementRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(procurementRequestService.toProcurementRequestDto(any(ProcurementRequest.class)))
                .thenAnswer(invocation -> {
                    ProcurementRequest saved = invocation.getArgument(0);
                    return com.toir.dto.procurement.ProcurementRequestDto.from(saved);
                });
    }

    @Test
    void allocateBudgetMarksRequestAsAllocatedAndCommitsEstimate() {
        UUID actorId = UUID.randomUUID();
        var result = service.allocateBudget(requestId, budgetLineId, actorId, "allocate");

        assertThat(result.budgetLineId()).isEqualTo(budgetLineId);
        assertThat(result.budgetAllocationStatus()).isEqualTo("ALLOCATED");
        verify(budgetCommitmentService).assertCanCommit(budgetLine, 300);
        verify(budgetCommitmentService).commitBudget(
                budgetLineId,
                300,
                "PROCUREMENT_REQUEST",
                requestId,
                actorId,
                "allocate"
        );
        verify(budgetEventRepository).save(any(BudgetEvent.class));
    }

    @Test
    void allocateBudgetRejectsWhenCommitmentWouldExceedAvailableBudget() {
        doThrow(RestException.badRequest("Insufficient budget for commitment"))
                .when(budgetCommitmentService).assertCanCommit(budgetLine, 300);

        assertThatThrownBy(() -> service.allocateBudget(requestId, budgetLineId, UUID.randomUUID(), "allocate"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Insufficient budget for commitment");

        verify(budgetCommitmentService, never()).commitBudget(any(), any(Double.class), any(), any(), any(), any());
    }

    @Test
    void unallocateBudgetClearsAllocationAndReleasesCommitmentForSubmittedRequest() {
        UUID actorId = UUID.randomUUID();
        request.setBudgetLineId(budgetLineId);
        request.setBudgetAllocationStatus(BudgetAllocationStatus.ALLOCATED);

        var result = service.unallocateBudget(requestId, actorId, "unallocate");

        assertThat(result.budgetLineId()).isNull();
        assertThat(result.budgetAllocationStatus()).isEqualTo("UNALLOCATED");
        verify(budgetCommitmentService).releaseBudget(
                budgetLineId,
                300,
                "PROCUREMENT_REQUEST",
                requestId,
                actorId,
                "unallocate"
        );
        verify(budgetEventRepository).save(any(BudgetEvent.class));
    }

    @Test
    void unallocateBudgetRejectsApprovedRequest() {
        request.setBudgetLineId(budgetLineId);
        request.setBudgetAllocationStatus(BudgetAllocationStatus.ALLOCATED);
        request.setStatus(ProcurementRequestStatus.APPROVED);

        assertThatThrownBy(() -> service.unallocateBudget(requestId, UUID.randomUUID(), "unallocate"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot unallocate approved procurement request");
    }
}
