package com.toir.service.finance;

import com.toir.entity.equipment.ProcurementRequestLine;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.enums.BudgetAllocationStatus;
import com.toir.enums.BudgetStatus;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.exception.RestException;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.actualCost.ActualCostAllocationEventRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.security.ScopeAccessService;
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
    ActualCostAllocationEventRepository allocationEventRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    ScopeAccessService scopeAccessService;

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

        MaintenanceBudget budget = new MaintenanceBudget();
        budget.setId(UUID.randomUUID());
        budget.setDepartmentId(departmentId);
        budget.setStatus(BudgetStatus.APPROVED);

        budgetLine = new BudgetLine();
        budgetLine.setId(budgetLineId);
        budgetLine.setBudget(budget);

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(procurementRequestRepository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));
        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)).thenReturn(Optional.of(budgetLine));
        when(procurementRequestRepository.save(any(ProcurementRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void allocateBudgetMarksRequestAsAllocated() {
        var result = service.allocateBudget(requestId, budgetLineId, UUID.randomUUID(), "allocate");

        assertThat(result.budgetLineId()).isEqualTo(budgetLineId);
        assertThat(result.budgetAllocationStatus()).isEqualTo("ALLOCATED");
        verify(allocationEventRepository).save(any());
    }

    @Test
    void unallocateBudgetClearsAllocationForSubmittedRequest() {
        request.setBudgetLineId(budgetLineId);
        request.setBudgetAllocationStatus(BudgetAllocationStatus.ALLOCATED);

        var result = service.unallocateBudget(requestId, UUID.randomUUID(), "unallocate");

        assertThat(result.budgetLineId()).isNull();
        assertThat(result.budgetAllocationStatus()).isEqualTo("UNALLOCATED");
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

    @Test
    void reviewQueueMapsProcurementLinesInsideServiceBoundary() {
        ProcurementRequestLine line = new ProcurementRequestLine();
        line.setId(UUID.randomUUID());
        line.setRequest(request);
        line.setSparePartId(UUID.randomUUID());
        line.setQuantity(2);
        line.setRemainingQuantity(2);
        line.setUnit("pcs");
        line.setEstimatedCost(120);
        request.getLines().add(line);

        when(scopeAccessService.enforceDepartmentScope(request.getDepartmentId())).thenReturn(request.getDepartmentId());
        when(procurementRequestRepository.findFinanceReviewQueue(
                request.getDepartmentId(),
                ProcurementRequestStatus.SUBMITTED,
                BudgetAllocationStatus.UNALLOCATED
        )).thenReturn(List.of(request));

        var result = service.reviewQueue(request.getDepartmentId(), ProcurementRequestStatus.SUBMITTED, true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).lines()).hasSize(1);
        assertThat(result.get(0).lines().get(0).id()).isEqualTo(line.getId());
    }
}
