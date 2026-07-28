package com.toir.service.maintanance;

import com.toir.dto.maintenanceschedule.MaintenanceScheduleCalculationDto;
import com.toir.dto.maintenanceschedule.MaintenanceScheduleCalculationStatsResponse;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.PprPlan;
import com.toir.enums.ApprovalStatus;
import com.toir.exception.RestException;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.MaintenanceScheduleCalculationLifecycleRepository;
import com.toir.repository.MaintenanceScheduleCalculationStatsProjection;
import com.toir.service.PprPlanService;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaintenanceScheduleCalculationDashboardService {

    private static final String TARGET_TYPE = "PPR_PLAN";
    private static final Set<String> LIFECYCLE_STATUSES = Set.of(
            "SAVED",
            "PENDING_APPROVAL",
            "REJECTED",
            "APPROVED",
            "IN_PROGRESS",
            "CLOSED",
            "CANCELLED"
    );

    private final PprPlanService pprPlanService;
    private final MaintenanceScheduleCalculationLifecycleRepository lifecycleRepository;
    private final ApprovalRequestRepository approvalRequestRepository;

    @Transactional(readOnly = true)
    public Page<MaintenanceScheduleCalculationDto> list(
            UUID departmentId,
            String search,
            Integer year,
            String lifecycleStatus,
            Pageable pageable
    ) {
        String normalizedStatus = normalizeLifecycleStatus(lifecycleStatus);
        return lifecycleRepository.search(
                        departmentId,
                        normalizeSearch(search),
                        year,
                        normalizedStatus,
                        pageable
                )
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public MaintenanceScheduleCalculationStatsResponse stats(
            UUID departmentId,
            String search,
            Integer year
    ) {
        MaintenanceScheduleCalculationStatsProjection result =
                lifecycleRepository.stats(departmentId, normalizeSearch(search), year);
        return new MaintenanceScheduleCalculationStatsResponse(
                result.getTotal(),
                result.getSaved(),
                result.getPendingApproval(),
                result.getApproved()
        );
    }

    private MaintenanceScheduleCalculationDto toDto(PprPlan plan) {
        return new MaintenanceScheduleCalculationDto(
                pprPlanService.toSummaryDto(plan),
                latestApprovalStatus(plan.getId())
        );
    }

    private ApprovalStatus latestApprovalStatus(UUID planId) {
        List<ApprovalRequest> approvals =
                approvalRequestRepository.findAllByTargetTypeAndTargetIdAndIsDeletedFalse(
                        TARGET_TYPE,
                        planId
                );
        return approvals.isEmpty() ? null : approvals.getFirst().getStatus();
    }

    private String normalizeLifecycleStatus(String lifecycleStatus) {
        if (lifecycleStatus == null || lifecycleStatus.isBlank()) {
            throw RestException.badRequest("Maintenance schedule lifecycle status is required");
        }
        String normalized = lifecycleStatus.trim().toUpperCase(Locale.ROOT);
        if (!LIFECYCLE_STATUSES.contains(normalized)) {
            throw RestException.badRequest(
                    "Unknown maintenance schedule lifecycle status: " + lifecycleStatus);
        }
        return normalized;
    }

    private String normalizeSearch(String search) {
        return search == null || search.isBlank() ? null : search.trim();
    }
}
