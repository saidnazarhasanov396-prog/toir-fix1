package com.toir.service;
import com.toir.enums.PlanStatus;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.enums.PprTaskStatus;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;

import com.toir.enums.PriorityLevel;
import com.toir.exception.RestException;
import com.toir.entity.Equipment;
import com.toir.repository.EquipmentRepository;
import com.toir.enums.EquipmentStatus;
import com.toir.entity.MaintenanceRegulation;
import com.toir.repository.MaintenanceRegulationRepository;
import com.toir.enums.PeriodicityUnit;
import com.toir.entity.PeriodicityUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Автогенерация ППР-задач из регламентов: для заданного плана проходит по
 * всем активным регламентам, находит оборудование соответствующего типа
 * (кроме DECOMMISSIONED) и создаёт задачу с датами исходя из периодичности
 * регламента и месяца плана.
 *
 * Идемпотентен: если задача с таким же code уже существует — пропускает.
 */
@Service
@RequiredArgsConstructor
public class PprGeneratorService {

    private final PprPlanRepository planRepository;
    private final PprTaskRepository taskRepository;
    private final MaintenanceRegulationRepository regulationRepository;
    private final EquipmentRepository equipmentRepository;


    public GenerationResult generateForPlan(UUID planId) {
        PprPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> RestException.notFound("PPR plan not found: " + planId));
        if (plan.getStatus() == PlanStatus.CLOSED || plan.getStatus() == PlanStatus.CANCELLED) {
            throw RestException.badRequest("Cannot generate tasks for closed/cancelled plan");
        }

        YearMonth planMonth = YearMonth.of(plan.getYear(), plan.getMonth());
        LocalDate monthStart = planMonth.atDay(1);
        LocalDate monthEnd = planMonth.atEndOfMonth();

        List<MaintenanceRegulation> regulations = regulationRepository.findAll().stream()
                .filter(MaintenanceRegulation::isActive)
                .toList();

        List<Equipment> allEquipment = equipmentRepository.findAll().stream()
                .filter(e -> e.getStatus() != EquipmentStatus.DECOMMISSIONED)
                .toList();

        java.util.Set<String> existingCodes = taskRepository.findAll().stream()
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
                taskRepository.save(task);
                created++;
            }
        }

        if (created > 0 && plan.getStatus() == PlanStatus.DRAFT) {
            plan.setStatus(PlanStatus.GENERATED);
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

    public record GenerationResult(UUID planId, int created, int skipped) {}
}
