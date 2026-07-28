package com.toir.service.maintanance;

import com.toir.dto.maintenanceschedule.MaintenanceScheduleCalculationDto;
import com.toir.dto.maintenanceschedule.MaintenanceScheduleCalculationRequest;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewResponse;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.PprPlan;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.PprPlanOrigin;
import com.toir.enums.PlanStatus;
import com.toir.exception.RestException;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.MaintenanceScheduleCalculationRepository;
import com.toir.service.PprPlanService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaintenanceScheduleCalculationService {

    private static final String TARGET_TYPE = "PPR_PLAN";
    private static final String ACTION_TYPE = "APPROVE";
    private static final String PENDING = "PENDING";

    private final MaintenanceScheduleService scheduleService;
    private final PprPlanService pprPlanService;
    private final PprPlanRepository planRepository;
    private final MaintenanceScheduleCalculationRepository calculationRepository;
    private final ApprovalRequestRepository approvalRequestRepository;

    @Transactional(readOnly = true)
    public Page<MaintenanceScheduleCalculationDto> list(
            UUID departmentId,
            String search,
            Integer year,
            PlanStatus status,
            Pageable pageable
    ) {
        return calculationRepository.search(
                        departmentId,
                        normalizeSearch(search),
                        year,
                        status == null ? null : status.name(),
                        pageable
                )
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public MaintenanceScheduleCalculationDto findById(UUID id) {
        PprPlan plan = schedulePlan(id);
        return new MaintenanceScheduleCalculationDto(
                pprPlanService.findById(plan.getId()),
                latestApprovalStatus(plan.getId())
        );
    }

    @Transactional
    public MaintenanceScheduleCalculationDto create(MaintenanceScheduleCalculationRequest request) {
        requireNonEmptyPreview(request);
        return new MaintenanceScheduleCalculationDto(
                pprPlanService.createScheduleCalculation(request.toPprPlanRequest()),
                null
        );
    }

    @Transactional
    public MaintenanceScheduleCalculationDto update(
            UUID id,
            MaintenanceScheduleCalculationRequest request
    ) {
        assertMutable(schedulePlan(id));
        requireNonEmptyPreview(request);
        return new MaintenanceScheduleCalculationDto(
                pprPlanService.updateScheduleCalculation(id, request.toPprPlanRequest()),
                latestApprovalStatus(id)
        );
    }

    @Transactional
    public void delete(UUID id) {
        assertMutable(schedulePlan(id));
        pprPlanService.deleteScheduleCalculation(id);
    }

    private MaintenanceScheduleCalculationDto toDto(PprPlan plan) {
        return new MaintenanceScheduleCalculationDto(
                pprPlanService.toSummaryDto(plan),
                latestApprovalStatus(plan.getId())
        );
    }

    private void requireNonEmptyPreview(MaintenanceScheduleCalculationRequest request) {
        MaintenanceSchedulePreviewResponse preview = scheduleService.preview(request.toPreviewRequest());
        if (preview.summary().totalOccurrences() <= 0) {
            throw RestException.badRequest("Maintenance schedule calculation has no occurrences");
        }
    }

    private void assertMutable(PprPlan plan) {
        if (plan.getStatus() != PlanStatus.DRAFT && plan.getStatus() != PlanStatus.GENERATED) {
            throw RestException.conflict("Approved maintenance schedule calculation cannot be changed");
        }
        boolean pending = approvalRequestRepository.findFirstPendingByTargetAndAction(
                TARGET_TYPE,
                plan.getId(),
                ACTION_TYPE,
                PENDING
        ).isPresent();
        if (pending) {
            throw RestException.conflict("Maintenance schedule calculation is pending approval");
        }
    }

    private PprPlan schedulePlan(UUID id) {
        PprPlan plan = planRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound(
                        "Maintenance schedule calculation not found: " + id));
        if (plan.getOrigin() != PprPlanOrigin.MAINTENANCE_SCHEDULE) {
            throw RestException.notFound("Maintenance schedule calculation not found: " + id);
        }
        return plan;
    }

    private ApprovalStatus latestApprovalStatus(UUID planId) {
        List<ApprovalRequest> approvals =
                approvalRequestRepository.findAllByTargetTypeAndTargetIdAndIsDeletedFalse(
                        TARGET_TYPE,
                        planId
                );
        return approvals.isEmpty() ? null : approvals.getFirst().getStatus();
    }

    private String normalizeSearch(String search) {
        return search == null || search.isBlank() ? null : search.trim();
    }
}
