package com.toir.finance;

import com.toir.entity.projects.BudgetEvent;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.enums.BudgetStatus;
import com.toir.repository.projects.BudgetEventRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.service.finance.BudgetCommitmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Golden-path specification for the approved finance model.
 * Uses real {@link BudgetCommitmentService} with in-memory {@link BudgetLine} state.
 */
@ExtendWith(MockitoExtension.class)
class FinanceGoldenPathFlowTest {

    @Mock
    BudgetLineRepository budgetLineRepository;

    @Mock
    BudgetEventRepository budgetEventRepository;

    BudgetCommitmentService commitmentService;

    UUID lineId;
    BudgetLine line;

    @BeforeEach
    void setUp() {
        commitmentService = new BudgetCommitmentService(budgetLineRepository, budgetEventRepository);
        lineId = UUID.randomUUID();
        line = new BudgetLine();
        line.setId(lineId);
        line.setPlannedAmount(1_000_000);
        line.setActualAmount(0);
        line.setCommittedAmount(0);
        MaintenanceBudget budget = new MaintenanceBudget();
        budget.setId(UUID.randomUUID());
        budget.setStatus(BudgetStatus.APPROVED);
        line.setBudget(budget);

        when(budgetLineRepository.findByIdAndIsDeletedFalse(lineId)).thenReturn(Optional.of(line));
        when(budgetLineRepository.save(any(BudgetLine.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void procurementTargetLifecycle_allocateReceiptApprove() {
        double estimate = 400_000;
        double receiptAmount = 400_000;

        // Finance allocate (target FAZA 3): commit estimated cost
        commitmentService.commitBudget(lineId, estimate, "PROCUREMENT_REQUEST", UUID.randomUUID(),
                UUID.randomUUID(), "Allocate procurement to budget line");
        assertThat(line.getCommittedAmount()).isEqualTo(estimate);
        assertThat(available()).isEqualTo(600_000);

        // Warehouse receipt creates PENDING actual cost — no budget movement yet
        // Finance actual approve: release commitment, move to actual
        commitmentService.releaseBudget(lineId, receiptAmount, "PROCUREMENT_REQUEST", UUID.randomUUID(),
                UUID.randomUUID(), "Release on actual cost approval");
        line.setActualAmount(line.getActualAmount() + receiptAmount);

        assertThat(line.getCommittedAmount()).isZero();
        assertThat(line.getActualAmount()).isEqualTo(receiptAmount);
        assertThat(available()).isEqualTo(600_000);
        assertThat(FinanceBudgetMath.remainingBudget(line.getPlannedAmount(), line.getActualAmount(), line.getCommittedAmount()))
                .isEqualTo(600_000);
    }

    @Test
    void procurementTargetLifecycle_rejectReceiptReleasesCommitment() {
        double estimate = 400_000;

        commitmentService.commitBudget(lineId, estimate, "PROCUREMENT_REQUEST", UUID.randomUUID(),
                UUID.randomUUID(), "Allocate");
        assertThat(line.getCommittedAmount()).isEqualTo(estimate);

        // Target FAZA 2: reject receipt actual cost must release commitment without adding actual
        commitmentService.releaseBudget(lineId, estimate, "PROCUREMENT_RECEIPT", UUID.randomUUID(),
                UUID.randomUUID(), "Release on actual cost rejection");

        assertThat(line.getCommittedAmount()).isZero();
        assertThat(line.getActualAmount()).isZero();
        assertThat(available()).isEqualTo(1_000_000);
    }

    @Test
    void workOrderTargetLifecycle_createCommitThenApprove() {
        double workOrderCost = 150_000;

        // Target FAZA 6: direct actual cost create commits when budget line is set
        commitmentService.commitBudget(lineId, workOrderCost, "ACTUAL_COST_PENDING", UUID.randomUUID(),
                UUID.randomUUID(), "Reserve on actual cost create");
        assertThat(line.getCommittedAmount()).isEqualTo(workOrderCost);
        assertThat(available()).isEqualTo(850_000);

        // Approve: release commitment and recognize actual
        commitmentService.releaseBudget(lineId, workOrderCost, "ACTUAL_COST_PENDING", UUID.randomUUID(),
                UUID.randomUUID(), "Release on actual cost approval");
        line.setActualAmount(line.getActualAmount() + workOrderCost);

        assertThat(line.getCommittedAmount()).isZero();
        assertThat(line.getActualAmount()).isEqualTo(workOrderCost);
        assertThat(available()).isEqualTo(850_000);
    }

    @Test
    void workOrderTargetLifecycle_rejectReleasesPendingCommitment() {
        double workOrderCost = 150_000;

        commitmentService.commitBudget(lineId, workOrderCost, "ACTUAL_COST_PENDING", UUID.randomUUID(),
                UUID.randomUUID(), "Reserve on actual cost create");

        commitmentService.releaseBudget(lineId, workOrderCost, "ACTUAL_COST_PENDING", UUID.randomUUID(),
                UUID.randomUUID(), "Release on actual cost rejection");

        assertThat(line.getCommittedAmount()).isZero();
        assertThat(line.getActualAmount()).isZero();
        assertThat(available()).isEqualTo(1_000_000);
    }

    @Test
    void unallocateProcurementReleasesCommittedEstimate() {
        double estimate = 250_000;
        UUID requestId = UUID.randomUUID();

        commitmentService.commitBudget(lineId, estimate, "PROCUREMENT_REQUEST", requestId,
                UUID.randomUUID(), "Allocate");
        commitmentService.releaseBudget(lineId, estimate, "PROCUREMENT_REQUEST", requestId,
                UUID.randomUUID(), "Unallocate before approve");

        assertThat(line.getCommittedAmount()).isZero();
        assertThat(available()).isEqualTo(1_000_000);

        ArgumentCaptor<BudgetEvent> events = ArgumentCaptor.forClass(BudgetEvent.class);
        verify(budgetEventRepository, org.mockito.Mockito.times(2)).save(events.capture());
        assertThat(events.getAllValues())
                .extracting(BudgetEvent::getEventType)
                .containsExactly("COMMITMENT_ADDED", "COMMITMENT_REMOVED");
    }

    @Test
    void partialReceiptReleaseDoesNotOverReleaseCommitted() {
        commitmentService.commitBudget(lineId, 500_000, "PROCUREMENT_REQUEST", UUID.randomUUID(),
                UUID.randomUUID(), "Allocate");
        commitmentService.releaseBudget(lineId, 200_000, "PROCUREMENT_RECEIPT", UUID.randomUUID(),
                UUID.randomUUID(), "Partial receipt approval");
        line.setActualAmount(200_000);

        assertThat(line.getCommittedAmount()).isEqualTo(300_000);
        assertThat(available()).isEqualTo(500_000);

        commitmentService.releaseBudget(lineId, 300_000, "PROCUREMENT_RECEIPT", UUID.randomUUID(),
                UUID.randomUUID(), "Second receipt approval");
        line.setActualAmount(500_000);

        assertThat(line.getCommittedAmount()).isZero();
        assertThat(line.getActualAmount()).isEqualTo(500_000);
        assertThat(available()).isEqualTo(500_000);
    }

    private double available() {
        return FinanceBudgetMath.availableAmount(
                line.getPlannedAmount(),
                line.getActualAmount(),
                line.getCommittedAmount()
        );
    }
}
