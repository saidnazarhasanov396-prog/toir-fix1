package com.toir.service;
import com.toir.dto.rcm.EquipmentRiskScore;

import com.toir.enums.PriorityLevel;
import com.toir.exception.RestException;
import com.toir.entity.Equipment;
import com.toir.repository.EquipmentRepository;
import com.toir.entity.MaintenanceRegulation;
import com.toir.repository.MaintenanceRegulationRepository;
import com.toir.entity.PprPlan;
import com.toir.repository.PprPlanRepository;
import com.toir.entity.PprTask;
import com.toir.repository.PprTaskRepository;
import com.toir.enums.PprTaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Автоматическая генерация задач ППР для оборудования с высоким RCM-риском.
 * Берёт top-N по рискам, для каждой позиции находит активную регламентную
 * карту по типу оборудования и создаёт PprTask в выбранном плане. Если план
 * не указан — используется первый план по текущему месяцу.
 */
@Service
@RequiredArgsConstructor
public class RcmAutoPlannerService {

    private final RcmService rcmService;
    private final EquipmentRepository equipmentRepository;
    private final MaintenanceRegulationRepository regulationRepository;
    private final PprPlanRepository planRepository;
    private final PprTaskRepository taskRepository;



    public AutoPlanResult generate(int riskThreshold, UUID planId) {
        List<EquipmentRiskScore> scores = rcmService.computeAll().stream()
                .filter(s -> s.riskScore() >= riskThreshold)
                .toList();
        if (scores.isEmpty()) {
            return new AutoPlanResult(0, 0, 0, List.of());
        }

        PprPlan plan = resolvePlan(planId);
        List<String> created = new ArrayList<>();
        int matched = 0;
        int skipped = 0;

        for (EquipmentRiskScore s : scores) {
            Equipment eq = equipmentRepository.findByIdAndIsDeletedFalse(s.equipmentId()).orElse(null);
            if (eq == null || eq.getEquipmentTypeId() == null) {
                skipped++;
                continue;
            }
            List<MaintenanceRegulation> regs = regulationRepository
                    .findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(eq.getEquipmentTypeId());
            if (regs.isEmpty()) {
                skipped++;
                continue;
            }
            MaintenanceRegulation reg = regs.get(0);
            String code = "RCM-" + eq.getCode() + "-" + System.currentTimeMillis() + "-" + matched;
            LocalDateTime now = LocalDateTime.now();
            PprTask task = new PprTask();
            task.setCode(code);
            task.setPlan(plan);
            task.setRegulationId(reg.getId());
            task.setEquipmentId(eq.getId());
            task.setTitle("RCM-инициированное " + reg.getName() + " (risk=" + s.riskScore() + ")");
            task.setScheduledStart(now.plusDays(1));
            task.setScheduledEnd(now.plusDays(1).plusHours((long) reg.getNormativeLaborHours()));
            task.setDueDate(now.plusDays(7));
            task.setStatus(PprTaskStatus.PLANNED);
            task.setPriority(priorityFor(s));
            task.setPlannedLaborHours(reg.getNormativeLaborHours());
            taskRepository.save(task);
            matched++;
            created.add(code);
        }
        return new AutoPlanResult(scores.size(), matched, skipped, created);
    }

    private PriorityLevel priorityFor(EquipmentRiskScore s) {
        if (s.riskScore() >= 60) return PriorityLevel.HIGH;
        if (s.riskScore() >= 30) return PriorityLevel.MEDIUM;
        return PriorityLevel.LOW;
    }

    private PprPlan resolvePlan(UUID planId) {
        if (planId != null) {
            return planRepository.findByIdAndIsDeletedFalse(planId)
                    .orElseThrow(() -> RestException.notFound("PprPlan not found: " + planId));
        }
        LocalDateTime now = LocalDateTime.now();
        List<PprPlan> plans = planRepository.findAllByYearAndMonthAndIsDeletedFalse(now.getYear(), now.getMonthValue());
        if (plans.isEmpty()) {
            List<PprPlan> any = planRepository.findAllByIsDeletedFalse();
            if (any.isEmpty()) throw RestException.badRequest("No PprPlan exists — create one first");
            return any.get(0);
        }
        return plans.get(0);
    }

    public record AutoPlanResult(int candidates, int tasksCreated, int skipped, List<String> createdCodes) {}
}
