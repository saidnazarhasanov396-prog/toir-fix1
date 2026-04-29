package com.toir.controller;

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
import java.util.Map;
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
    void actualCostRegisterReturnsStandardPageWithSummary() {
        when(actualCostRepository.findAllByIsDeletedFalse()).thenReturn(List.of(
                actualCost(ActualCostStatus.PENDING, 100, null),
                actualCost(ActualCostStatus.APPROVED, 200, null),
                actualCost(ActualCostStatus.REJECTED, 300, null)
        ));

        Map<String, Object> response = controller.actualCostRegister(1, 2);

        assertStandardPage(response, 1, 2, 3L, 1);
        assertNoLegacyItemsMeta(response);
        assertThat(response).containsKey("summary");
        assertThat(((Map<?, ?>) response.get("summary")).get("totalCount")).isEqualTo(3L);
    }

    @Test
    void reviewQueueReturnsStandardPage() {
        when(actualCostRepository.findAllByStatusAndIsDeletedFalse(ActualCostStatus.PENDING)).thenReturn(List.of(
                actualCost(ActualCostStatus.PENDING, 100, null),
                actualCost(ActualCostStatus.PENDING, 200, null)
        ));

        Map<String, Object> response = controller.reviewQueue(0, 1);

        assertStandardPage(response, 0, 1, 2L, 1);
        assertNoLegacyItemsMeta(response);
    }

    @Test
    void reviewActivityReturnsStandardPageWithSummary() {
        when(actualCostRepository.findAllByIsDeletedFalse()).thenReturn(List.of(
                actualCost(ActualCostStatus.APPROVED, 100, Instant.now()),
                actualCost(ActualCostStatus.APPROVED, 200, Instant.now().minusSeconds(10)),
                actualCost(ActualCostStatus.APPROVED, 300, null)
        ));

        Map<String, Object> response = controller.reviewActivity(0, 1);

        assertStandardPage(response, 0, 1, 2L, 1);
        assertNoLegacyItemsMeta(response);
        assertThat(((Map<?, ?>) response.get("summary")).get("total")).isEqualTo(2);
    }

    @Test
    void handoversReturnsStandardEmptyPageWithSummary() {
        Map<String, Object> response = controller.handovers(0, 20);

        assertStandardPage(response, 0, 20, 0L, 0);
        assertNoLegacyItemsMeta(response);
        assertThat(((Map<?, ?>) response.get("summary")).get("total")).isEqualTo(0);
    }

    @Test
    void approvalPackReturnsStandardPage() {
        when(actualCostRepository.findAllByStatusAndIsDeletedFalse(ActualCostStatus.PENDING)).thenReturn(List.of(
                actualCost(ActualCostStatus.PENDING, 100, null),
                actualCost(ActualCostStatus.PENDING, 200, null)
        ));

        Map<String, Object> response = controller.approvalPack(0, 2);

        assertStandardPage(response, 0, 2, 2L, 2);
        assertNoLegacyItemsMeta(response);
    }

    @Test
    void reviewHistoryPackReturnsStandardPage() {
        when(actualCostRepository.findAllByIsDeletedFalse()).thenReturn(List.of(
                actualCost(ActualCostStatus.APPROVED, 100, Instant.now()),
                actualCost(ActualCostStatus.PENDING, 200, null)
        ));

        Map<String, Object> response = controller.reviewHistoryPack(0, 20);

        assertStandardPage(response, 0, 20, 1L, 1);
        assertNoLegacyItemsMeta(response);
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

    private static void assertStandardPage(Map<String, Object> response,
                                           int page,
                                           int pageSize,
                                           long totalElements,
                                           int numberOfElements) {
        assertThat(response).containsKeys(
                "content",
                "pageable",
                "last",
                "totalElements",
                "totalPages",
                "first",
                "size",
                "number",
                "sort",
                "numberOfElements",
                "empty"
        );
        assertThat(response.get("totalElements")).isEqualTo(totalElements);
        assertThat(response.get("numberOfElements")).isEqualTo(numberOfElements);
        assertThat(response.get("number")).isEqualTo(page);
        assertThat(response.get("size")).isEqualTo(pageSize);
        assertThat((List<?>) response.get("content")).hasSize(numberOfElements);

        Map<?, ?> pageable = (Map<?, ?>) response.get("pageable");
        assertThat(pageable.get("pageNumber")).isEqualTo(page);
        assertThat(pageable.get("pageSize")).isEqualTo(pageSize);
    }

    private static void assertNoLegacyItemsMeta(Map<String, Object> response) {
        assertThat(response).doesNotContainKeys("items", "meta");
    }
}
