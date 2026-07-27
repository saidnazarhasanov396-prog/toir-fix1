package com.toir.service;

import com.toir.entity.PprTask;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.PlanStatus;
import com.toir.repository.PprTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PprTaskQueryService {

    private static final PprTaskStatus OVERDUE_STATUS = PprTaskStatus.OVERDUE;
    private static final Set<PprTaskStatus> DUE_DATE_OVERDUE_STATUSES = EnumSet.of(
            PprTaskStatus.PLANNED,
            PprTaskStatus.APPROVED,
            PprTaskStatus.IN_PROGRESS
    );

    private final PprTaskRepository taskRepository;
    private final PprPlanVisibilityPolicy visibilityPolicy;
    private Clock clock = Clock.systemDefaultZone();

    public Page<PprTask> findTasks(UUID departmentId,
                                   UUID equipmentId,
                                   PprTaskStatus status,
                                   boolean overdue,
                                   Pageable pageable) {
        if (visibilityPolicy != null) {
            return findTasksByStatuses(departmentId, equipmentId, taskStatuses(status), overdue, pageable);
        }
        return taskRepository.searchTasks(
                departmentId,
                equipmentId,
                status,
                overdue,
                now(),
                OVERDUE_STATUS,
                DUE_DATE_OVERDUE_STATUSES,
                pageable
        );
    }

    public Page<PprTask> findTasksByStatuses(UUID departmentId,
                                   UUID equipmentId,
                                   Set<PprTaskStatus> taskStatuses,
                                   boolean overdue,
                                   Pageable pageable) {
        Set<PlanStatus> planStatuses = visibilityPolicy == null
                ? EnumSet.allOf(PlanStatus.class)
                : visibilityPolicy.visibleStatuses(Set.of());
        if (planStatuses.isEmpty() || taskStatuses == null || taskStatuses.isEmpty()) {
            return Page.empty(pageable);
        }
        return taskRepository.searchVisibleTasks(
                departmentId,
                equipmentId,
                taskStatuses,
                planStatuses,
                overdue,
                now(),
                OVERDUE_STATUS,
                DUE_DATE_OVERDUE_STATUSES,
                pageable
        );
    }

    public List<PprTask> findTasks(UUID departmentId,
                                   UUID equipmentId,
                                   PprTaskStatus status,
                                   boolean overdue) {
        if (visibilityPolicy != null) {
            return findTasksByStatuses(departmentId, equipmentId, taskStatuses(status), overdue);
        }
        return taskRepository.searchTasks(
                departmentId,
                equipmentId,
                status,
                overdue,
                now(),
                OVERDUE_STATUS,
                DUE_DATE_OVERDUE_STATUSES
        );
    }

    public List<PprTask> findTasksByStatuses(UUID departmentId,
                                   UUID equipmentId,
                                   Set<PprTaskStatus> taskStatuses,
                                   boolean overdue) {
        Set<PlanStatus> planStatuses = visibilityPolicy == null
                ? EnumSet.allOf(PlanStatus.class)
                : visibilityPolicy.visibleStatuses(Set.of());
        if (planStatuses.isEmpty() || taskStatuses == null || taskStatuses.isEmpty()) {
            return List.of();
        }
        return taskRepository.searchVisibleTasks(
                departmentId,
                equipmentId,
                taskStatuses,
                planStatuses,
                overdue,
                now(),
                OVERDUE_STATUS,
                DUE_DATE_OVERDUE_STATUSES
        );
    }

    public long countOverdueTasks(UUID departmentId, UUID equipmentId, PprTaskStatus status) {
        if (visibilityPolicy != null) {
            return findTasksByStatuses(departmentId, equipmentId, taskStatuses(status), true).size();
        }
        return taskRepository.countTasks(
                departmentId,
                equipmentId,
                status,
                true,
                now(),
                OVERDUE_STATUS,
                DUE_DATE_OVERDUE_STATUSES
        );
    }

    private Set<PprTaskStatus> taskStatuses(PprTaskStatus status) {
        return status == null ? EnumSet.allOf(PprTaskStatus.class) : EnumSet.of(status);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
