package com.toir.pprplanning;

import com.toir.common.exception.RestException;
import com.toir.pprplanning.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PprPlanService {

    private final PprPlanRepository planRepository;
    private final PprTaskRepository taskRepository;

    public PprPlanService(PprPlanRepository planRepository, PprTaskRepository taskRepository) {
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public List<PprPlanDto> findAll() {
        return planRepository.findAll().stream().map(PprPlanDto::from).toList();
    }

    @Transactional(readOnly = true)
    public PprPlanDto findById(UUID id) {
        return PprPlanDto.from(getPlan(id));
    }

    public PprPlanDto create(PprPlanRequest request) {
        if (planRepository.existsByCode(request.code())) {
            throw RestException.conflict("Plan code already exists: " + request.code());
        }
        PprPlan plan = new PprPlan();
        plan.setCode(request.code());
        plan.setName(request.name());
        plan.setYear(request.year());
        plan.setMonth(request.month());
        plan.setDepartmentId(request.departmentId());
        plan.setCreatedById(request.createdById());
        plan.setNotes(request.notes());
        return PprPlanDto.from(planRepository.save(plan));
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
        PprTask task = taskRepository.findById(taskId)
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
        return taskRepository.findAllByPlanId(planId).stream().map(PprTaskDto::from).toList();
    }

    private PprPlan getPlan(UUID id) {
        return planRepository.findById(id)
                .orElseThrow(() -> RestException.notFound("PPR plan not found: " + id));
    }
}
