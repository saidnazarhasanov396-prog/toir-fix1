package com.toir.service.finance;

import com.toir.entity.projects.BudgetEvent;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.enums.BudgetStatus;
import com.toir.exception.RestException;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.projects.BudgetEventRepository;
import com.toir.repository.projects.BudgetLineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetCommitmentServiceTest {

    @Mock
    BudgetLineRepository budgetLineRepository;

    @Mock
    BudgetEventRepository budgetEventRepository;

    @Mock
    MaintenanceBudgetRepository maintenanceBudgetRepository;

    @InjectMocks
    BudgetCommitmentService service;

    private UUID budgetLineId;
    private BudgetLine line;
    private MaintenanceBudget budget;

    @BeforeEach
    void setUp() {
        budgetLineId = UUID.randomUUID();
        budget = new MaintenanceBudget();
        budget.setId(UUID.randomUUID());
        budget.setStatus(BudgetStatus.APPROVED);
        budget.setTotalCommitted(200);
        line = new BudgetLine();
        line.setId(budgetLineId);
        line.setBudget(budget);
        line.setPlannedAmount(1_000);
        line.setActualAmount(100);
        line.setCommittedAmount(200);
        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)).thenReturn(Optional.of(line));
        when(budgetLineRepository.save(any(BudgetLine.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceBudgetRepository.save(any(MaintenanceBudget.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void commitBudgetIncreasesCommittedAmountAndRecordsEvent() {
        service.commitBudget(budgetLineId, 300, "PROCUREMENT_REQUEST", UUID.randomUUID(),
                UUID.randomUUID(), "commit");

        assertThat(line.getCommittedAmount()).isEqualTo(500);
        assertThat(budget.getTotalCommitted()).isEqualTo(500);
        ArgumentCaptor<BudgetEvent> eventCaptor = ArgumentCaptor.forClass(BudgetEvent.class);
        verify(budgetEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("COMMITMENT_ADDED");
    }

    @Test
    void commitBudgetRejectsWhenInsufficientAvailableAmount() {
        assertThatThrownBy(() -> service.commitBudget(budgetLineId, 900, "PROCUREMENT_REQUEST",
                UUID.randomUUID(), UUID.randomUUID(), "commit"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Insufficient budget for commitment");
    }

    @Test
    void commitBudgetRejectsWhenActualSpendLeavesInsufficientRoom() {
        line.setActualAmount(900);
        line.setCommittedAmount(0);

        assertThatThrownBy(() -> service.commitBudget(budgetLineId, 200, "PROCUREMENT_REQUEST",
                UUID.randomUUID(), UUID.randomUUID(), "commit"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Insufficient budget for commitment");
    }

    @Test
    void releaseBudgetCapsReleaseAtCurrentCommittedAmount() {
        service.releaseBudget(budgetLineId, 500, "PROCUREMENT_REQUEST", UUID.randomUUID(),
                UUID.randomUUID(), "release");

        assertThat(line.getCommittedAmount()).isZero();
        assertThat(budget.getTotalCommitted()).isZero();
        verify(budgetEventRepository).save(any(BudgetEvent.class));
    }
}
