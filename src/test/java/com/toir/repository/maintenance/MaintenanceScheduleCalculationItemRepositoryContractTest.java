package com.toir.repository.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;

class MaintenanceScheduleCalculationItemRepositoryContractTest {

    @Test
    void exposesOnlyInsertAndExactRevisionReadContracts() {
        Class<MaintenanceScheduleCalculationItemRepository> repositoryType =
                MaintenanceScheduleCalculationItemRepository.class;
        Set<String> declaredMethods = Arrays.stream(repositoryType.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        assertThat(Repository.class).isAssignableFrom(repositoryType);
        assertThat(CrudRepository.class.isAssignableFrom(repositoryType)).isFalse();
        assertThat(declaredMethods).containsExactlyInAnyOrder(
                "saveAll",
                "findAllByPlanIdAndCalculationRevisionOrderBySourceItemKey",
                "countByPlanIdAndCalculationRevision",
                "existsByPlanIdAndCalculationRevisionAndSourceItemKey"
        );
        assertThat(declaredMethods).noneMatch(name ->
                name.startsWith("delete") || name.startsWith("update"));
    }
}
