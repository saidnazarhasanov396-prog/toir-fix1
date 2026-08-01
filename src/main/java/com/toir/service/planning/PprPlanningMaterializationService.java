package com.toir.service.planning;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.planning.PprPlanningSession;
import com.toir.entity.planning.PprPlanningVariantItem;
import com.toir.enums.MaterializationMode;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprFrequency;
import com.toir.enums.PprPlanOrigin;
import com.toir.enums.PprScheduleType;
import com.toir.enums.PprScopeType;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.PprType;
import com.toir.enums.PriorityLevel;
import com.toir.enums.TaskMaterializationStatus;
import com.toir.enums.planning.PprPlanningSessionStatus;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.planning.PprPlanningSessionRepository;
import com.toir.repository.planning.PprPlanningVariantItemRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PprPlanningMaterializationService {

    private final PprPlanningSessionRepository sessionRepository;
    private final PprPlanningVariantItemRepository itemRepository;
    private final PprPlanRepository planRepository;
    private final PprTaskRepository taskRepository;
    private final PprPlanningApprovalBindingService bindingService;

    @Transactional
    public PprPlan materialize(ApprovalRequest approval, UUID approverId) {
        if (approval == null || approval.getTargetId() == null) {
            throw conflict("PPR_PLANNING_APPROVAL_BINDING_REQUIRED");
        }
        PprPlanningSession session = sessionRepository
                .findByIdAndIsDeletedFalseForUpdate(approval.getTargetId())
                .orElseThrow(() -> RestException.notFound("PPR planning session not found"));
        if (session.getApprovedPlanId() != null) {
            return planRepository.findByIdAndIsDeletedFalse(session.getApprovedPlanId())
                    .orElseThrow(() -> conflict("PPR_PLANNING_MATERIALIZATION_INCONSISTENT"));
        }
        bindingService.validateApprovalCommand(approval);
        if (session.getStatus() != PprPlanningSessionStatus.PENDING_APPROVAL) {
            throw conflict("PPR_PLANNING_APPROVAL_STALE");
        }
        long revision = approval.getCalculationRevision();
        List<PprPlanningVariantItem> snapshot = itemRepository
                .findAllByVariantIdAndRevisionOrderBySourceItemKey(
                        approval.getPprPlanningVariantId(), revision);
        if (snapshot.isEmpty()) {
            throw conflict("PPR_PLANNING_SNAPSHOT_MISSING");
        }

        PprPlan plan = createPlan(session, approval, approverId, snapshot.size());
        PprPlan savedPlan = planRepository.saveAndFlush(plan);
        List<PprTask> materializedTasks = snapshot.stream()
                .map(item -> createTask(savedPlan, item))
                .toList();
        taskRepository.saveAll(materializedTasks);
        savedPlan.getTasks().addAll(materializedTasks);

        session.setApprovedPlanId(savedPlan.getId());
        session.setStatus(PprPlanningSessionStatus.APPROVED);
        sessionRepository.save(session);
        return savedPlan;
    }

    private static PprPlan createPlan(
            PprPlanningSession session,
            ApprovalRequest approval,
            UUID approverId,
            int taskCount) {
        PprPlan plan = new PprPlan();
        plan.setCode("PPR-PLANNING-" + session.getYear() + "-" + session.getId());
        plan.setName(session.getName());
        plan.setNotes(session.getNotes());
        plan.setStartDate(session.getStartDate());
        plan.setEndDate(session.getEndDate());
        plan.setDepartmentId(session.getDepartmentId());
        plan.setScopeType(PprScopeType.DEPARTMENT);
        plan.setPprType(PprType.PREVENTIVE_MAINTENANCE);
        plan.setScheduleType(PprScheduleType.CALENDAR);
        plan.setFrequency(PprFrequency.YEARLY);
        plan.setOrigin(PprPlanOrigin.MAINTENANCE_SCHEDULE);
        plan.setMaterializationMode(MaterializationMode.APPROVAL_FIRST);
        plan.setTaskMaterializationStatus(TaskMaterializationStatus.MATERIALIZED);
        plan.setStatus(PlanStatus.APPROVED);
        plan.setApprovedById(approverId);
        plan.setCalculationRevision(approval.getCalculationRevision());
        plan.setCalculationContentHash(approval.getCalculationContentHash());
        plan.setCalculationContentHashVersion(approval.getCalculationContentHashVersion());
        plan.setApprovedRevision(approval.getCalculationRevision());
        plan.setApprovedContentHash(approval.getCalculationContentHash());
        plan.setApprovedContentHashVersion(approval.getCalculationContentHashVersion());
        plan.setMaterializedRevision(approval.getCalculationRevision());
        plan.setMaterializedTaskCount(taskCount);
        plan.setPlanningSessionId(session.getId());
        plan.setSourceVariantId(approval.getPprPlanningVariantId());
        plan.setSourceVariantRevision(approval.getCalculationRevision());
        plan.setTasks(new ArrayList<>());
        return plan;
    }

    private static PprTask createTask(PprPlan plan, PprPlanningVariantItem item) {
        if (item.getScheduledStart() == null || item.getScheduledEnd() == null) {
            throw conflict("PPR_PLANNING_SCHEDULE_WINDOW_INVALID");
        }
        PprTask task = new PprTask();
        task.setCode("PPR-PLANNING-TASK-" + item.getId());
        task.setPlan(plan);
        task.setRegulationId(item.getRegulationId());
        task.setEquipmentMaintenanceRuleId(item.getMaintenanceRuleId());
        task.setEquipmentId(item.getEquipmentId());
        task.setSourceVariantItemId(item.getId());
        task.setTitle(item.getTaskTitleSnapshot());
        task.setScheduledStart(item.getScheduledStart());
        task.setScheduledEnd(item.getScheduledEnd());
        task.setDueDate(item.getDueDate() == null ? item.getScheduledEnd() : item.getDueDate());
        task.setPriority(item.getPriority() == null ? PriorityLevel.MEDIUM : item.getPriority());
        task.setPlannedLaborHours(item.getNormativeLaborHours() == null
                ? 0.0d : item.getNormativeLaborHours().doubleValue());
        task.setStatus(PprTaskStatus.APPROVED);
        task.setWorkOrderLeadDays(item.getWorkOrderLeadDays());
        task.setRequiredEvidenceTypes(item.getRequiredEvidenceTypes() == null
                ? java.util.Set.of() : java.util.Set.copyOf(item.getRequiredEvidenceTypes()));
        return task;
    }

    private static RestException conflict(String code) {
        return new RestException(code, HttpStatus.CONFLICT, code);
    }
}
