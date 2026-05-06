package com.toir.service;

import com.toir.dto.pprplanning.*;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprTaskStatus;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class PprPlanService {

    private final PprPlanRepository planRepository;
    private final PprTaskRepository taskRepository;
    private final AuditBuilderService auditBuilderService;


    @Transactional(readOnly = true)
    public List<PprPlanDto> findAll() {
        return planRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(PprPlanDto::from).toList();
    }

    @Transactional(readOnly = true)
    public PprPlanDto findById(UUID id) {
        return PprPlanDto.from(getPlan(id));
    }

    @Transactional
    public PprPlanDto create(PprPlanRequest request) {
        PprPlan plan = new PprPlan();
        plan.setCode(nextCode());
        plan.setName(request.name());
        plan.setYear(request.year());
        plan.setMonth(request.month());
        plan.setDepartmentId(request.departmentId());
        plan.setCreatedById(request.createdById());
        plan.setNotes(request.notes());
        PprPlan saved = planRepository.save(plan);

        auditBuilderService.log(
                "ppr_plan",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.PPR_PLAN,
                "ППР план создан",
                null,
                saved
        );

        return PprPlanDto.from(saved);
    }

    @Transactional
    public PprPlanDto update(UUID id, PprPlanRequest request) {
        PprPlan plan = getPlan(id);
        if (plan.getStatus() != PlanStatus.DRAFT) {
            throw RestException.badRequest("Only DRAFT plans can be edited");
        }
        plan.setName(request.name());
        plan.setYear(request.year());
        plan.setMonth(request.month());
        plan.setDepartmentId(request.departmentId());
        plan.setNotes(request.notes());

        PprPlan saved = planRepository.save(plan);
        auditBuilderService.log(
                "ppr_plan",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PPR_PLAN,
                "ППР план обновлен",
                plan,
                saved
        );
        return PprPlanDto.from(plan);
    }

    @Transactional
    public void delete(UUID id) {
        PprPlan plan = getPlan(id);
        if (plan.getStatus() != PlanStatus.DRAFT) {
            throw RestException.badRequest("Only DRAFT plans can be deleted");
        }
        plan.setDeleted(true);
        PprPlan saved = planRepository.save(plan);

        auditBuilderService.log(
                "ppr_plan",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.PPR_PLAN,
                "ППР план удален",
                saved,
                null
        );
    }

    public PprPlanDto approve(UUID planId, UUID approverId) {
        PprPlan plan = getPlan(planId);
        if (plan.getStatus() != PlanStatus.DRAFT && plan.getStatus() != PlanStatus.GENERATED) {
            throw RestException.badRequest("Only DRAFT/GENERATED plans can be approved");
        }
        plan.setStatus(PlanStatus.APPROVED);
        plan.setApprovedById(approverId);
        return PprPlanDto.from(plan);
    }

    public PprTaskDto addTask(UUID planId, PprTaskRequest request) {
        PprPlan plan = getPlan(planId);
        PprTask task = new PprTask();
        task.setCode(request.code());
        task.setPlan(plan);
        task.setRegulationId(request.regulationId());
        task.setEquipmentId(request.equipmentId());
        task.setTitle(request.title());
        task.setScheduledStart(request.scheduledStart());
        task.setScheduledEnd(request.scheduledEnd());
        task.setDueDate(request.dueDate());
        task.setPlannedLaborHours(request.plannedLaborHours());
        if (request.priority() != null) task.setPriority(request.priority());
        plan.getTasks().add(task);
        return PprTaskDto.from(taskRepository.save(task));
    }

    public PprTaskDto postponeTask(UUID taskId, PostponeTaskRequest request) {
        PprTask task = taskRepository.findByIdAndIsDeletedFalse(taskId)
                .orElseThrow(() -> RestException.notFound("PPR task not found: " + taskId));
        if (request.reason() == null || request.reason().isBlank()) {
            throw RestException.badRequest("Postpone reason is required");
        }
        task.setDueDate(request.newDueDate());
        task.setPostponeReason(request.reason());
        task.setStatus(PprTaskStatus.POSTPONED);
        return PprTaskDto.from(task);
    }

    @Transactional(readOnly = true)
    public List<PprTaskDto> findTasksByPlan(UUID planId) {
        return taskRepository.findAllByPlanIdAndIsDeletedFalseOrderByUpdatedAtDesc(planId).stream().map(PprTaskDto::from).toList();
    }

    private PprPlan getPlan(UUID id) {
        return planRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("PPR plan not found: " + id));
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "PPR-" + year + "-";
        long sequence = planRepository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("PPR", year, sequence);
        while (planRepository.existsByCodeAndIsDeletedFalse(code)) {
            sequence++;
            code = formatCode("PPR", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }

}
