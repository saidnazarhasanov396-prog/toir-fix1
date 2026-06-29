package com.toir.repository;

import com.toir.entity.projects.ActualCostAllocationEvent;
import com.toir.entity.projects.BudgetEvent;
import com.toir.repository.actualCost.ActualCostAllocationEventRepository;
import com.toir.repository.projects.BudgetEventRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceEventRepositoryContractTest {

    @Test
    void budgetEventRepositoryExposesBudgetTimelineLookup() throws Exception {
        Method method = BudgetEventRepository.class.getMethod(
                "findAllByBudgetIdAndIsDeletedFalseOrderByOccurredAtDesc",
                UUID.class
        );

        assertThat(method.getReturnType()).isAssignableFrom(java.util.List.class);
        assertThat(BudgetEvent.class).isNotNull();
    }

    @Test
    void allocationEventRepositoryExposesActualCostTimelineLookup() throws Exception {
        Method method = ActualCostAllocationEventRepository.class.getMethod(
                "findAllByActualCostIdAndIsDeletedFalseOrderByOccurredAtDesc",
                UUID.class
        );

        assertThat(method.getReturnType()).isAssignableFrom(java.util.List.class);
        assertThat(ActualCostAllocationEvent.class).isNotNull();
    }
}
