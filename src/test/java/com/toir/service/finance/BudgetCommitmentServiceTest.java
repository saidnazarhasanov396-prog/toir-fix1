package com.toir.service.finance;

import com.toir.entity.projects.BudgetEvent;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.enums.BudgetStatus;
import com.toir.exception.RestException;
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

    @InjectMocks
    BudgetCommitmentService service;

    private UUID budgetLineId;
    private BudgetLine line;

    @BeforeEach
    void setUp() {
        budgetLineId = UUID.randomUUID();
        MaintenanceBudget budget = new MaintenanceBudget();
        budget.setId(UUID.randomUUID());
        budget.setStatus(BudgetStatus.APPROVED);
        line = new BudgetLine();
        line.setId(budgetLineId);
        line.setBudget(budget);
        line.setPlannedAmount(1_000);
        line.setActualAmount(100);
        line.setCommittedAmount(200);
        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)).thenReturn(Optional.of(line));
        when(budgetLineRepository.save(any(BudgetLine.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void commitBudgetIncreasesCommittedAmountAndRecordsEvent() {
        service.commitBudget(budgetLineId, 300, "PROCUREMENT_REQUEST", UUID.randomUUID(),
                UUID.randomUUID(), "commit");

        assertThat(line.getCommittedAmount()).isEqualTo(500);
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
    void releaseBudgetDecreasesCommittedAmountWithoutGoingBelowZero() {
        service.releaseBudget(budgetLineId, 500, "PROCUREMENT_REQUEST", UUID.randomUUID(),
                UUID.randomUUID(), "release");

        assertThat(line.getCommittedAmount()).isZero();
        verify(budgetEventRepository).save(any(BudgetEvent.class));
    }
}
