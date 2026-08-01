package com.toir.service.maintanance;

import com.toir.dto.maintenanceschedule.MaintenanceScheduleCalculationDto;
import com.toir.dto.maintenanceschedule.MaintenanceScheduleCalculationItemDto;
import com.toir.dto.maintenanceschedule.MaintenanceScheduleCalculationRequest;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewResponse;
import com.toir.config.AnnualMaintenanceApprovalFirstFeature;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.PprPlan;
import com.toir.entity.maintenance.MaintenanceScheduleCalculationItem;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.MaterializationMode;
import com.toir.enums.PprPlanOrigin;
import com.toir.enums.PlanStatus;
import com.toir.enums.TaskMaterializationStatus;
import com.toir.exception.RestException;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.MaintenanceScheduleCalculationRepository;
import com.toir.repository.maintenance.MaintenanceScheduleCalculationItemRepository;
import com.toir.service.PprPlanService;
import com.toir.security.ScopeAccessService;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    private final MaintenanceScheduleCalculationItemRepository calculationItemRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final AnnualMaintenanceApprovalFirstFeature approvalFirstFeature;
    private final MaintenanceScheduleSnapshotDraftFactory snapshotDraftFactory;
    private final MaintenanceScheduleSnapshotService snapshotService;
    private final MaintenanceScheduleCalculationContentFactory contentFactory;
    private final MaintenanceScheduleContentHasher contentHasher;
    private final MaintenanceScheduleRevisionService revisionService;
    private final MaintenanceScheduleApprovalBindingService approvalBindingService;
    private final ScopeAccessService scopeAccessService;

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
                latestApprovalStatus(plan.getId()),
                plan.isShiftFromExcludedWeekdays(),
                Set.copyOf(plan.getExcludedWeekdays()),
                plan.getRecurrenceAnchor(),
                currentRevisionItems(plan)
        );
    }

    private List<MaintenanceScheduleCalculationItemDto> currentRevisionItems(PprPlan plan) {
        Long revision = plan.getCalculationRevision();
        if (revision == null || revision < 1L) {
            return List.of();
        }
        return calculationItemRepository
                .findAllByPlanIdAndCalculationRevisionOrderBySourceItemKey(
                        plan.getId(), revision)
                .stream()
                .map(MaintenanceScheduleCalculationItemDto::from)
                .toList();
    }

    @Transactional
    public MaintenanceScheduleCalculationDto create(MaintenanceScheduleCalculationRequest request) {
        MaintenanceSchedulePreviewResponse preview = requireNonEmptyPreview(request);
        var created = pprPlanService.createScheduleCalculation(request.toPprPlanRequest());
        if (!approvalFirstEnabled()) {
            return calculationDto(created, null, request);
        }
        PprPlan plan = planRepository.findByIdAndIsDeletedFalseForUpdate(created.id())
                .orElseThrow(() -> RestException.notFound(
                        "Maintenance schedule calculation not found: " + created.id()));
        if (plan.getTasks().stream().anyMatch(task -> !task.isDeleted())) {
            throw conflict(
                    "PPR_APPROVAL_FIRST_PREAPPROVAL_TASK_INTEGRITY",
                    "Approval-first calculation must not contain tasks before approval");
        }
        List<MaintenanceScheduleCalculationItem> items =
                snapshotDraftFactory.create(plan, 1L, preview.items());
        MaintenanceScheduleRevisionTransition transition =
                revisionService.initial(contentFactory.fromSnapshot(plan, 1L, items));
        snapshotService.insertRevision(plan.getId(), 1L, items);
        plan.setMaterializationMode(MaterializationMode.APPROVAL_FIRST);
        plan.setTaskMaterializationStatus(TaskMaterializationStatus.NOT_MATERIALIZED);
        plan.setStatus(PlanStatus.CALCULATED);
        plan.initializeApprovalFirstCalculation(
                transition.nextRevision(),
                transition.hashVersion().persistedValue(),
                transition.calculatedHash());
        planRepository.saveAndFlush(plan);
        var persisted = pprPlanService.findById(plan.getId())
                .withGenerationMessage("REVISION_CREATED:1");
        return calculationDto(persisted, null, request);
    }

    private MaintenanceScheduleCalculationDto calculationDto(
            com.toir.dto.pprplanning.PprPlanDto plan,
            ApprovalStatus approvalStatus,
            MaintenanceScheduleCalculationRequest request) {
        return new MaintenanceScheduleCalculationDto(
                plan,
                approvalStatus,
                request.shiftFromExcludedWeekdays(),
                request.excludedWeekdays() == null ? Set.of() : Set.copyOf(request.excludedWeekdays()),
                request.recurrenceAnchor() == null
                        ? com.toir.enums.MaintenanceScheduleRecurrenceAnchor.REGULATION_DATE
                        : request.recurrenceAnchor()
        );
    }

    @Transactional
    public MaintenanceScheduleCalculationDto update(
            UUID id,
            MaintenanceScheduleCalculationRequest request
    ) {
        PprPlan current = schedulePlan(id);
        boolean approvalFirst = isApprovalFirst(current);
        assertMutable(current, approvalFirst);
        MaintenanceSchedulePreviewResponse preview = requireNonEmptyPreview(request);
        var updated = pprPlanService.updateScheduleCalculation(
                id, request.toPprPlanRequest());
        if (!approvalFirst) {
            return calculationDto(updated, latestApprovalStatus(id), request);
        }

        PprPlan plan = planRepository.findByIdAndIsDeletedFalseForUpdate(id)
                .orElseThrow(() -> RestException.notFound(
                        "Maintenance schedule calculation not found: " + id));
        long currentRevision = requireCurrentRevision(plan);
        List<MaintenanceScheduleCalculationItem> items =
                snapshotDraftFactory.create(plan, currentRevision, preview.items());
        String candidateCurrentRevisionHash = contentHasher.compute(
                plan.getCalculationContentHashVersion(),
                contentFactory.fromSnapshot(plan, currentRevision, items));
        if (Objects.equals(
                candidateCurrentRevisionHash, plan.getCalculationContentHash())) {
            var noChange = pprPlanService.findById(id)
                    .withGenerationMessage(
                            "NO_CHANGES: calculation content matches revision "
                                    + currentRevision);
            return calculationDto(noChange, latestApprovalStatus(id), request);
        }

        long nextRevision;
        try {
            nextRevision = Math.addExact(currentRevision, 1L);
        } catch (ArithmeticException exception) {
            throw conflict(
                    "PPR_CALCULATION_REVISION_OVERFLOW",
                    "Maintenance schedule calculation revision overflow");
        }
        items.forEach(item -> item.setCalculationRevision(nextRevision));
        MaintenanceScheduleRevisionTransition transition = revisionService.next(
                currentRevision,
                contentFactory.fromSnapshot(plan, nextRevision, items));
        snapshotService.insertRevision(id, nextRevision, items);
        plan.advanceApprovalFirstCalculation(
                currentRevision,
                transition.nextRevision(),
                transition.hashVersion().persistedValue(),
                transition.calculatedHash());
        planRepository.saveAndFlush(plan);
        approvalBindingService.supersedeForNewRevision(
                id,
                nextRevision,
                transition.calculatedHash(),
                transition.hashVersion().persistedValue(),
                scopeAccessService.currentUserIdOrNull());
        var revised = pprPlanService.findById(id)
                .withGenerationMessage("REVISION_CREATED:" + nextRevision);
        return calculationDto(revised, latestApprovalStatus(id), request);
    }

    @Transactional
    public void delete(UUID id) {
        assertMutable(schedulePlan(id), false);
        pprPlanService.deleteScheduleCalculation(id);
    }

    private MaintenanceScheduleCalculationDto toDto(PprPlan plan) {
        return new MaintenanceScheduleCalculationDto(
                pprPlanService.toSummaryDto(plan),
                latestApprovalStatus(plan.getId()),
                plan.isShiftFromExcludedWeekdays(),
                Set.copyOf(plan.getExcludedWeekdays()),
                plan.getRecurrenceAnchor()
        );
    }

    private MaintenanceSchedulePreviewResponse requireNonEmptyPreview(
            MaintenanceScheduleCalculationRequest request) {
        MaintenanceSchedulePreviewResponse preview = scheduleService.preview(request.toPreviewRequest());
        if (preview.diagnostics().stream()
                .anyMatch(item -> "BLOCKING".equalsIgnoreCase(item.severity()))) {
            throw RestException.badRequest("Maintenance schedule calculation has blocking diagnostics");
        }
        if (preview.summary().totalOccurrences() <= 0) {
            throw RestException.badRequest("Maintenance schedule calculation has no occurrences");
        }
        if (preview.items() == null || preview.items().isEmpty()) {
            throw RestException.badRequest(
                    "Maintenance schedule calculation snapshot has no items");
        }
        return preview;
    }

    private void assertMutable(PprPlan plan, boolean allowPendingReviewAmend) {
        boolean editableStatus = isApprovalFirst(plan)
                ? plan.getStatus() == PlanStatus.CALCULATED
                : plan.getStatus() == PlanStatus.DRAFT
                    || plan.getStatus() == PlanStatus.GENERATED;
        if (!editableStatus) {
            throw RestException.conflict("Approved maintenance schedule calculation cannot be changed");
        }
        boolean pending = approvalRequestRepository.findFirstPendingByTargetAndAction(
                TARGET_TYPE,
                plan.getId(),
                ACTION_TYPE,
                PENDING
        ).isPresent();
        if (pending && !allowPendingReviewAmend) {
            throw RestException.conflict("Maintenance schedule calculation is pending approval");
        }
    }

    private boolean approvalFirstEnabled() {
        return approvalFirstFeature != null && approvalFirstFeature.isEnabled();
    }

    private boolean isApprovalFirst(PprPlan plan) {
        return plan.getOrigin() == PprPlanOrigin.MAINTENANCE_SCHEDULE
                && plan.getMaterializationMode() == MaterializationMode.APPROVAL_FIRST;
    }

    private long requireCurrentRevision(PprPlan plan) {
        if (plan.getCalculationRevision() == null
                || plan.getCalculationRevision() < 1
                || plan.getCalculationContentHashVersion() == null
                || plan.getCalculationContentHash() == null) {
            throw conflict(
                    "PPR_CALCULATION_REVISION_INTEGRITY",
                    "Approval-first calculation revision metadata is incomplete");
        }
        return plan.getCalculationRevision();
    }

    private RestException conflict(String code, String message) {
        return new RestException(
                message,
                org.springframework.http.HttpStatus.CONFLICT,
                code);
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
