package com.toir.service;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.PlanStatus;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.enums.PprTaskStatus;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;

import com.toir.enums.PriorityLevel;
import com.toir.exception.RestException;
import com.toir.entity.equipment.Equipment;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.enums.EquipmentStatus;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.enums.PeriodicityUnit;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PprGeneratorService {

    private final PprPlanRepository planRepository;
    private final PprTaskRepository taskRepository;
    private final MaintenanceRegulationRepository regulationRepository;
    private final EquipmentRepository equipmentRepository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;

    @Transactional
    public GenerationResult generateForPlan(UUID planId) {
        PprPlan plan = planRepository.findByIdAndIsDeletedFalse(planId)
                .orElseThrow(() -> RestException.notFound("PPR plan not found: " + planId));
        if (plan.getStatus() == PlanStatus.CLOSED || plan.getStatus() == PlanStatus.CANCELLED) {
            throw RestException.badRequest("Cannot generate tasks for closed/cancelled plan");
        }
        String oldPlanJson = auditSerializationService.toJson(plan);

        YearMonth planMonth = YearMonth.of(plan.getYear(), plan.getMonth());
        LocalDate monthStart = planMonth.atDay(1);
        LocalDate monthEnd = planMonth.atEndOfMonth();

        List<MaintenanceRegulation> regulations = regulationRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(MaintenanceRegulation::isActive)
                .toList();

        List<Equipment> allEquipment = equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(e -> e.getStatus() != EquipmentStatus.DECOMMISSIONED)
                .toList();

        java.util.Set<String> existingCodes = taskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .map(PprTask::getCode)
                .collect(java.util.stream.Collectors.toSet());

        int created = 0;
        int skipped = 0;

        for (MaintenanceRegulation reg : regulations) {
            // only generate if this plan's month aligns with periodicity:
            // DAY — always, WEEK/MONTH — always, QUARTER — if month ∈ {1,4,7,10}, YEAR/HALF — if month == 1
            if (!shouldGenerate(reg, planMonth)) {
                continue;
            }

            // filter equipment by type
            List<Equipment> matching = new ArrayList<>();
            for (Equipment eq : allEquipment) {
                if (eq.getEquipmentTypeId() != null && eq.getEquipmentTypeId().equals(reg.getEquipmentTypeId())) {
                    // department scope for the plan (if any)
                    if (plan.getDepartmentId() != null
                            && !plan.getDepartmentId().equals(eq.getDepartmentId())) {
                        continue;
                    }
                    matching.add(eq);
                }
            }

            int seq = 1;
            for (Equipment eq : matching) {
                String code = String.format("PT-%s-%02d-%s-%d", reg.getCode(), plan.getMonth(), eq.getCode(), seq++);
                if (existingCodes.contains(code)) {
                    skipped++;
                    continue;
                }

                PprTask task = new PprTask();
                task.setCode(code);
                task.setPlan(plan);
                task.setRegulationId(reg.getId());
                task.setEquipmentId(eq.getId());
                task.setTitle(reg.getName() + " — " + eq.getCode());
                task.setScheduledStart(monthStart.atTime(LocalTime.of(9, 0)));
                task.setScheduledEnd(monthStart.plusDays(Math.max(1, (int) Math.ceil(reg.getNormativeLaborHours() / 8)))
                        .atTime(LocalTime.of(18, 0)));
                task.setDueDate(monthEnd.atTime(LocalTime.of(18, 0)));
                task.setPlannedLaborHours(reg.getNormativeLaborHours());
                task.setPriority(PriorityLevel.MEDIUM);
                task.setStatus(PprTaskStatus.PLANNED);

                plan.getTasks().add(task);
                PprTask saved = taskRepository.save(task);
                auditTask(AuditAction.CREATE, saved.getId(), null, saved);
                created++;
            }
        }

        if (created > 0 && plan.getStatus() == PlanStatus.DRAFT) {
            plan.setStatus(PlanStatus.GENERATED);
            auditPlan(AuditAction.UPDATE, plan.getId(), oldPlanJson, plan);
        }

        return new GenerationResult(plan.getId(), created, skipped);
    }

    private boolean shouldGenerate(MaintenanceRegulation reg, YearMonth planMonth) {
        PeriodicityUnit unit = reg.getPeriodicityUnit();
        int value = reg.getPeriodicityValue();
        int month = planMonth.getMonthValue();
        return switch (unit) {
            case DAY, WEEK, MONTH -> true;
            case QUARTER -> (month - 1) % (3 * value) == 0;
            case YEAR -> month == 1;
            case HOUR -> true; // hour-based — run every month, operator decides
        };
    }

    private void auditTask(AuditAction action, UUID id, String oldJson, PprTask current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "ppr_task",
                id != null ? id.toString() : null,
                action,
                AuditModule.PPR_TASK,
                auditTaskMessage(action),
                oldJson,
                newJson
        );
    }

    private void auditPlan(AuditAction action, UUID id, String oldJson, PprPlan current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "ppr_plan",
                id != null ? id.toString() : null,
                action,
                AuditModule.PPR_PLAN,
                auditPlanMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditTaskMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Задача ППР создана";
            case UPDATE -> "Задача ППР обновлена";
            case DELETE -> "Задача ППР удалена";
            default -> "Действие выполнено над задачей ППР";
        };
    }

    private String auditPlanMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "План ППР создан";
            case UPDATE -> "План ППР обновлен";
            case DELETE -> "План ППР удален";
            default -> "Действие выполнено над планом ППР";
        };
    }

    public record GenerationResult(UUID planId, int created, int skipped) {}
}
