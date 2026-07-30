package com.toir.service.maintanance;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.maintenance.MaintenanceScheduleCalculationItem;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.MaterializationMode;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprPlanOrigin;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.TaskMaterializationStatus;
import com.toir.exception.MaintenanceScheduleApprovalStaleException;
import com.toir.exception.MaintenanceScheduleApprovalStaleException.Reason;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.maintenance.MaintenanceScheduleCalculationItemRepository;
import com.toir.service.PprPlanService;
import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaintenanceScheduleMaterializationService {

    private final PprPlanRepository planRepository;
    private final MaintenanceScheduleCalculationItemRepository itemRepository;
    private final PprTaskRepository taskRepository;
    private final PprPlanService legacyPlanService;
    private final EntityManager entityManager;
    private final MaintenanceScheduleContentHasher contentHasher;
    private final MaintenanceScheduleCalculationContentFactory contentFactory;

    @Transactional
    public MaintenanceScheduleMaterializationOutcome finalizeApproval(
            ApprovalRequest request, UUID approverId) {
        UUID targetId = effectiveTargetId(request);
        PprPlan plan = planRepository.findByIdAndIsDeletedFalseForUpdate(targetId)
                .orElseThrow(() -> RestException.notFound("PPR plan not found"));
        if (!isApprovalFirst(plan)) {
            legacyPlanService.finalizeApprovalFromApprovalRequest(
                    targetId, approverId);
            return MaintenanceScheduleMaterializationOutcome.LEGACY_APPROVED;
        }

        validateTarget(request, plan);
        long revision = requireRevision(request);
        validateCurrentTuple(request, plan, revision);
        List<MaintenanceScheduleCalculationItem> items =
                itemRepository
                        .findAllByPlanIdAndCalculationRevisionOrderBySourceItemKey(
                                targetId, revision);
        if (items.isEmpty()) {
            throw conflict(
                    "PPR_CALCULATION_SNAPSHOT_MISSING",
                    "Exact calculation snapshot is missing");
        }
        contentHasher.verify(
                request.getCalculationContentHashVersion(),
                contentFactory.fromSnapshot(plan, revision, items),
                request.getCalculationContentHash());

        List<UUID> sourceIds = items.stream()
                .map(MaintenanceScheduleCalculationItem::getId)
                .toList();
        validateSnapshotWindows(items);
        List<PprTask> allPlanTasks =
                taskRepository.findAllByPlanIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                        targetId);
        if (allPlanTasks.stream().anyMatch(task ->
                task.getSourceCalculationItemId() == null
                        || !sourceIds.contains(
                                task.getSourceCalculationItemId()))) {
            throw inconsistentMaterialization();
        }
        List<PprTask> existing =
                taskRepository.findAllByPlanIdAndSourceCalculationItemIdIn(
                        targetId, sourceIds);
        if (plan.getTaskMaterializationStatus()
                == TaskMaterializationStatus.MATERIALIZED) {
            if (isExactIdempotentState(plan, revision, sourceIds, existing)) {
                return MaintenanceScheduleMaterializationOutcome
                        .IDEMPOTENT_SUCCESS;
            }
            throw inconsistentMaterialization();
        }

        Set<UUID> expectedSourceIds = new HashSet<>(sourceIds);
        Set<UUID> existingSourceIds = existing.stream()
                .map(PprTask::getSourceCalculationItemId)
                .collect(java.util.stream.Collectors.toSet());
        if (existing.stream().anyMatch(PprTask::isDeleted)
                || existingSourceIds.contains(null)
                || !expectedSourceIds.containsAll(existingSourceIds)
                || existingSourceIds.size() != existing.size()) {
            throw inconsistentMaterialization();
        }
        if (plan.getStatus() != PlanStatus.CALCULATED) {
            throw conflict(
                    "PPR_CALCULATION_STATUS_NOT_FINALIZABLE",
                    "Calculation is not in finalizable status");
        }

        List<PprTask> tasks = items.stream()
                .filter(item -> !existingSourceIds.contains(item.getId()))
                .map(item -> toApprovedTask(plan, item))
                .toList();
        if (!tasks.isEmpty()) {
            taskRepository.saveAll(tasks);
            entityManager.flush();
            plan.getTasks().addAll(tasks);
        }
        plan.setStatus(PlanStatus.APPROVED);
        plan.setApprovedById(approverId);
        plan.setTaskMaterializationStatus(
                TaskMaterializationStatus.MATERIALIZED);
        plan.setApprovedRevision(revision);
        plan.setApprovedContentHash(request.getCalculationContentHash());
        plan.setApprovedContentHashVersion(
                request.getCalculationContentHashVersion());
        plan.setMaterializedRevision(revision);
        plan.setMaterializedTaskCount(sourceIds.size());
        planRepository.saveAndFlush(plan);
        return MaintenanceScheduleMaterializationOutcome.APPROVED;
    }

    private void validateTarget(ApprovalRequest request, PprPlan plan) {
        if (request == null
                || effectiveTargetType(request) != ApprovalTargetType.PPR_PLAN
                || request.getActionType() != ApprovalActionType.APPROVE
                || request.getStatus() != ApprovalStatus.APPROVED
                || !Objects.equals(effectiveTargetId(request), plan.getId())) {
            throw new MaintenanceScheduleApprovalStaleException(
                    Reason.TARGET_MISMATCH);
        }
        if (plan.getOrigin() != PprPlanOrigin.MAINTENANCE_SCHEDULE) {
            throw conflict(
                    "PPR_CALCULATION_APPROVAL_MODE_INVALID",
                    "Approval-first plan origin is invalid");
        }
    }

    private long requireRevision(ApprovalRequest request) {
        if (request.getCalculationRevision() == null
                || request.getCalculationRevision() < 1) {
            throw conflict(
                    "PPR_CALCULATION_APPROVAL_BINDING_MISSING",
                    "Approval calculation revision is missing");
        }
        if (request.getCalculationContentHash() == null
                || request.getCalculationContentHashVersion() == null) {
            throw conflict(
                    "PPR_CALCULATION_APPROVAL_BINDING_MISSING",
                    "Approval calculation hash binding is missing");
        }
        return request.getCalculationRevision();
    }

    private void validateCurrentTuple(
            ApprovalRequest request, PprPlan plan, long revision) {
        if (!Objects.equals(plan.getCalculationRevision(), revision)) {
            throw new MaintenanceScheduleApprovalStaleException(
                    Reason.REVISION_MISMATCH);
        }
        if (!Objects.equals(
                        plan.getCalculationContentHash(),
                        request.getCalculationContentHash())
                || !Objects.equals(
                        plan.getCalculationContentHashVersion(),
                        request.getCalculationContentHashVersion())) {
            throw new MaintenanceScheduleApprovalStaleException(
                    Reason.HASH_MISMATCH);
        }
    }

    private static boolean isExactIdempotentState(
            PprPlan plan,
            long revision,
            List<UUID> sourceIds,
            List<PprTask> existing) {
        Set<UUID> expected = new HashSet<>(sourceIds);
        Set<UUID> actual = existing.stream()
                .map(PprTask::getSourceCalculationItemId)
                .collect(java.util.stream.Collectors.toSet());
        return existing.size() == expected.size()
                && existing.stream().noneMatch(PprTask::isDeleted)
                && isMaterializedPlanStatus(plan.getStatus())
                && plan.getTaskMaterializationStatus()
                == TaskMaterializationStatus.MATERIALIZED
                && Objects.equals(plan.getMaterializedRevision(), revision)
                && Objects.equals(plan.getApprovedRevision(), revision)
                && Objects.equals(
                        plan.getApprovedContentHash(),
                        plan.getCalculationContentHash())
                && Objects.equals(
                        plan.getApprovedContentHashVersion(),
                        plan.getCalculationContentHashVersion())
                && Objects.equals(
                        plan.getMaterializedTaskCount(), sourceIds.size())
                && expected.equals(actual);
    }

    private static boolean isMaterializedPlanStatus(PlanStatus status) {
        return status == PlanStatus.APPROVED
                || status == PlanStatus.IN_PROGRESS
                || status == PlanStatus.CLOSED
                || status == PlanStatus.CANCELLED;
    }

    private static PprTask toApprovedTask(
            PprPlan plan, MaintenanceScheduleCalculationItem item) {
        PprTask task = new PprTask();
        task.setCode("PPR-MAT-" + plan.getId() + "-"
                + item.getSourceItemKey().substring(0, 16));
        task.setPlan(plan);
        task.setRegulationId(item.getRegulationId());
        task.setEquipmentMaintenanceRuleId(item.getMaintenanceRuleId());
        task.setEquipmentId(item.getEquipmentId());
        task.setSourceCalculationItemId(item.getId());
        task.setTitle(item.getTaskTitleSnapshot());
        task.setScheduledStart(item.getScheduledStart());
        task.setScheduledEnd(item.getScheduledEnd());
        task.setDueDate(item.getDueDate());
        task.setPlannedLaborHours(
                item.getNormativeLaborHours() == null
                        ? 0.0d
                        : item.getNormativeLaborHours().doubleValue());
        task.setPriority(item.getPriority() == null
                ? PriorityLevel.MEDIUM
                : item.getPriority());
        task.setStatus(PprTaskStatus.APPROVED);
        return task;
    }

    private static void validateSnapshotWindows(
            List<MaintenanceScheduleCalculationItem> items) {
        boolean invalid = items.stream().anyMatch(item ->
                item.getScheduledStart() == null
                        || item.getScheduledEnd() == null
                        || item.getDueDate() == null
                        || !item.getScheduledStart().isBefore(
                                item.getScheduledEnd())
                        || item.getDueDate().isBefore(
                                item.getScheduledEnd()));
        if (invalid) {
            throw conflict(
                    "PPR_CALCULATION_SCHEDULE_WINDOW_INVALID",
                    "Approved calculation contains an invalid task schedule window");
        }
    }

    private static boolean isApprovalFirst(PprPlan plan) {
        return plan.getMaterializationMode()
                == MaterializationMode.APPROVAL_FIRST;
    }

    private static ApprovalTargetType effectiveTargetType(
            ApprovalRequest request) {
        return request.getTargetType() != null
                ? request.getTargetType()
                : ApprovalTargetType.fromDocumentType(
                        request.getDocumentType());
    }

    private static UUID effectiveTargetId(ApprovalRequest request) {
        if (request == null) {
            throw RestException.badRequest(
                    "Approval request is required");
        }
        UUID id = request.getTargetId() != null
                ? request.getTargetId()
                : request.getDocumentId();
        if (id == null) {
            throw RestException.badRequest(
                    "Approval target ID is required");
        }
        return id;
    }

    private static RestException conflict(String code, String message) {
        return new RestException(
                message,
                org.springframework.http.HttpStatus.CONFLICT,
                code);
    }

    private static RestException inconsistentMaterialization() {
        return conflict(
                "PPR_CALCULATION_MATERIALIZATION_INCONSISTENT",
                "Calculation materialization metadata is inconsistent");
    }
}
