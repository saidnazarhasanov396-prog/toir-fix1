package com.toir.service;

import com.toir.entity.PprTask;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprTaskStatus;
import com.toir.repository.PprTaskRepository;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PprTaskQueryVisibilityTest {

    @Test
    void taskQueryAlwaysPassesWorkerVisibleParentPlanStatusesToRepository() {
        PprTaskRepository repository = mock(PprTaskRepository.class);
        PprPlanVisibilityPolicy policy = mock(PprPlanVisibilityPolicy.class);
        Set<PlanStatus> visiblePlans = EnumSet.of(
                PlanStatus.APPROVED,
                PlanStatus.IN_PROGRESS,
                PlanStatus.CLOSED,
                PlanStatus.CANCELLED
        );
        when(policy.visibleStatuses(Set.of())).thenReturn(visiblePlans);
        PageRequest pageable = PageRequest.of(0, 20);
        when(repository.searchVisibleTasks(
                any(), any(), any(), eq(visiblePlans), anyBoolean(), any(), any(), any(), eq(pageable)
        )).thenReturn(new PageImpl<PprTask>(java.util.List.of(), pageable, 0));
        PprTaskQueryService service = new PprTaskQueryService(repository, policy);

        var result = service.findTasks(
                UUID.randomUUID(), null, null, false, pageable);

        assertThat(result).isEmpty();
        verify(repository).searchVisibleTasks(
                any(), any(), eq(EnumSet.allOf(PprTaskStatus.class)), eq(visiblePlans),
                eq(false), any(), eq(PprTaskStatus.OVERDUE),
                eq(EnumSet.of(PprTaskStatus.PLANNED, PprTaskStatus.APPROVED, PprTaskStatus.IN_PROGRESS)),
                eq(pageable)
        );
    }
}
