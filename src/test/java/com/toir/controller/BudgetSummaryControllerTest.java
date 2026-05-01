package com.toir.controller;

import com.toir.dto.budget.ActualCostBudgetRow;
import com.toir.dto.budget.ActualCostHandoverSummary;
import com.toir.dto.budget.ActualCostRegisterSummary;
import com.toir.dto.budget.ActualCostReviewActivitySummary;
import com.toir.dto.common.PageResponse;
import com.toir.dto.common.PageResponseWithSummary;
import com.toir.entity.ActualCost;
import com.toir.enums.ActualCostStatus;
import com.toir.repository.ActualCostRepository;
import com.toir.repository.BudgetLineRepository;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.MaintenanceBudgetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetSummaryControllerTest {

    @Mock
    MaintenanceBudgetRepository budgetRepository;

    @Mock
    BudgetLineRepository lineRepository;

    @Mock
    ActualCostRepository actualCostRepository;

    @Mock
    CostCategoryRepository costCategoryRepository;

    @InjectMocks
    BudgetSummaryController controller;

    @Test
    void actualCostRegisterReturnsTypedPageWithSummary() {
        when(actualCostRepository.findAllByIsDeletedFalse()).thenReturn(List.of(
                actualCost(ActualCostStatus.PENDING, 100, null),
                actualCost(ActualCostStatus.APPROVED, 200, null),
                actualCost(ActualCostStatus.REJECTED, 300, null)
        ));

        PageResponseWithSummary<ActualCostBudgetRow, ActualCostRegisterSummary> response =
                controller.actualCostRegister(1, 2);

        assertTypedPage(response, 1, 2, 3L, 1);
        assertThat(response.summary().totalCount()).isEqualTo(3L);
    }

    @Test
    void reviewQueueReturnsTypedPage() {
        when(actualCostRepository.findAllByStatusAndIsDeletedFalse(ActualCostStatus.PENDING)).thenReturn(List.of(
                actualCost(ActualCostStatus.PENDING, 100, null),
                actualCost(ActualCostStatus.PENDING, 200, null)
        ));

        PageResponse<ActualCostBudgetRow> response = controller.reviewQueue(0, 1);

        assertTypedPage(response, 0, 1, 2L, 1);
    }

    @Test
    void reviewActivityReturnsTypedPageWithSummary() {
        when(actualCostRepository.findAllByIsDeletedFalse()).thenReturn(List.of(
                actualCost(ActualCostStatus.APPROVED, 100, Instant.now()),
                actualCost(ActualCostStatus.APPROVED, 200, Instant.now().minusSeconds(10)),
                actualCost(ActualCostStatus.APPROVED, 300, null)
        ));

        PageResponseWithSummary<ActualCostBudgetRow, ActualCostReviewActivitySummary> response =
                controller.reviewActivity(0, 1);

        assertTypedPage(response, 0, 1, 2L, 1);
        assertThat(response.summary().total()).isEqualTo(2);
    }

    @Test
    void handoversReturnsTypedEmptyPageWithSummary() {
        PageResponseWithSummary<ActualCostBudgetRow, ActualCostHandoverSummary> response =
                controller.handovers(0, 20);

        assertTypedPage(response, 0, 20, 0L, 0);
        assertThat(response.summary().total()).isEqualTo(0);
    }

    @Test
    void approvalPackReturnsTypedPage() {
        when(actualCostRepository.findAllByStatusAndIsDeletedFalse(ActualCostStatus.PENDING)).thenReturn(List.of(
                actualCost(ActualCostStatus.PENDING, 100, null),
                actualCost(ActualCostStatus.PENDING, 200, null)
        ));

        PageResponse<ActualCostBudgetRow> response = controller.approvalPack(0, 2);

        assertTypedPage(response, 0, 2, 2L, 2);
    }

    @Test
    void reviewHistoryPackReturnsTypedPage() {
        when(actualCostRepository.findAllByIsDeletedFalse()).thenReturn(List.of(
                actualCost(ActualCostStatus.APPROVED, 100, Instant.now()),
                actualCost(ActualCostStatus.PENDING, 200, null)
        ));

        PageResponse<ActualCostBudgetRow> response = controller.reviewHistoryPack(0, 20);

        assertTypedPage(response, 0, 20, 1L, 1);
    }

    private static ActualCost actualCost(ActualCostStatus status, double amount, Instant reviewedAt) {
        ActualCost actualCost = new ActualCost();
        actualCost.setId(UUID.randomUUID());
        actualCost.setCostCategoryId(UUID.randomUUID());
        actualCost.setStatus(status);
        actualCost.setAmount(amount);
        actualCost.setCostDate(Instant.now());
        actualCost.setReviewedAt(reviewedAt);
        return actualCost;
    }

    private static <T> void assertTypedPage(PageResponse<T> response,
                                           int page,
                                           int pageSize,
                                           long totalElements,
                                           int numberOfElements) {
        assertThat(response.totalElements()).isEqualTo(totalElements);
        assertThat(response.numberOfElements()).isEqualTo(numberOfElements);
        assertThat(response.number()).isEqualTo(page);
        assertThat(response.size()).isEqualTo(pageSize);
        assertThat(response.content()).hasSize(numberOfElements);
        assertThat(response.pageable().pageNumber()).isEqualTo(page);
        assertThat(response.pageable().pageSize()).isEqualTo(pageSize);
    }

    private static <T, S> void assertTypedPage(PageResponseWithSummary<T, S> response,
                                              int page,
                                              int pageSize,
                                              long totalElements,
                                              int numberOfElements) {
        assertThat(response.totalElements()).isEqualTo(totalElements);
        assertThat(response.numberOfElements()).isEqualTo(numberOfElements);
        assertThat(response.number()).isEqualTo(page);
        assertThat(response.size()).isEqualTo(pageSize);
        assertThat(response.content()).hasSize(numberOfElements);
        assertThat(response.pageable().pageNumber()).isEqualTo(page);
        assertThat(response.pageable().pageSize()).isEqualTo(pageSize);
    }
}
