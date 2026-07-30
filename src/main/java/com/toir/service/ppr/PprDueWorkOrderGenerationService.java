package com.toir.service.ppr;

import com.toir.config.PprLifecycleProperties;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.PprType;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.EquipmentMaintenanceRuleRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.service.WorkOrderNumberService;
import com.toir.service.WorkOrderService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PprDueWorkOrderGenerationService {

    private final PprTaskRepository tasks;
    private final WorkOrderRepository workOrders;
    private final EquipmentRepository equipment;
    private final MaintenanceRegulationRepository regulations;
    private final EquipmentMaintenanceRuleRepository rules;
    private final WorkOrderNumberService numbers;
    private final WorkOrderService workOrderService;
    private final PprWorkOrderEligibilityService eligibility;
    private final PprLifecycleProperties properties;

    @Transactional
    public GenerationRunResult generateDue(Instant now) {
        if (!properties.isDueWorkOrderGenerationEnabled()) {
            return new GenerationRunResult(0, List.of());
        }
        UUID actorId = properties.getWorkOrderGeneration().getActorId();
        if (actorId == null) {
            throw new IllegalStateException(
                    "toir.ppr-lifecycle.work-order-generation.actor-id is required when due generation is enabled");
        }
        ZoneId zone = properties.getWorkOrderGeneration().getTimezone();
        LocalDateTime localNow = LocalDateTime.ofInstant(now, zone);
        List<PprTask> candidates = tasks.findDueForWorkOrderGeneration(
                localNow, properties.getWorkOrderGeneration().getBatchSize());
        List<SkippedItem> skipped = new ArrayList<>();
        int created = 0;
        for (PprTask task : candidates) {
            String skipReason = generateOne(task, localNow, zone, actorId);
            if (skipReason == null) {
                created++;
            } else {
                skipped.add(new SkippedItem(task.getId(), skipReason));
            }
        }
        return new GenerationRunResult(created, List.copyOf(skipped));
    }

    private String generateOne(PprTask task, LocalDateTime now, ZoneId zone, UUID actorId) {
        boolean alreadyExists = workOrders.existsByPprTaskIdAndIsDeletedFalse(task.getId());
        PprWorkOrderEligibilityService.Eligibility decision =
                eligibility.evaluate(task, now, alreadyExists);
        if (!decision.eligible()) {
            return decision.reason();
        }
        if (task.getEquipmentId() == null) {
            return "TASK_EQUIPMENT_MISSING";
        }
        Optional<Equipment> equipmentResult =
                equipment.findByIdAndIsDeletedFalse(task.getEquipmentId());
        if (equipmentResult.isEmpty()) {
            return "EQUIPMENT_NOT_FOUND";
        }
        PprPlan plan = task.getPlan();
        Equipment machine = equipmentResult.get();
        UUID departmentId = machine.getDepartmentId() != null
                ? machine.getDepartmentId()
                : plan.getDepartmentId();
        if (departmentId == null) {
            return "DEPARTMENT_MISSING";
        }
        MaintenanceRegulation regulation = task.getRegulationId() == null
                ? null
                : regulations.findByIdAndIsDeletedFalse(task.getRegulationId()).orElse(null);
        EquipmentMaintenanceRule rule = task.getEquipmentMaintenanceRuleId() == null
                ? null
                : rules.findByIdAndIsDeletedFalse(task.getEquipmentMaintenanceRuleId()).orElse(null);
        WorkOrderRequest request = new WorkOrderRequest(
                numbers.nextPprNumber(),
                task.getTitle(),
                task.getEquipmentId(),
                null,
                departmentId,
                null,
                null,
                task.getId(),
                null,
                workOrderType(plan, regulation, rule),
                workType(regulation, rule),
                null,
                null,
                task.getPriority(),
                task.getScheduledStart().atZone(zone).toInstant(),
                task.getScheduledEnd().atZone(zone).toInstant(),
                null,
                summary(plan, task),
                task.getMaintenanceDueEventId(),
                task.getCycleKey())
                .withGenerationKey("ppr-task:" + task.getId());
        workOrderService.createGenerated(request, actorId);
        return null;
    }

    private WorkOrderType workOrderType(
            PprPlan plan,
            MaintenanceRegulation regulation,
            EquipmentMaintenanceRule rule) {
        if (plan.getPprType() == PprType.CAPITAL_REPAIR) {
            return WorkOrderType.OVERHAUL;
        }
        MaintenanceKind kind = maintenanceKind(regulation, rule);
        if (plan.getPprType() == PprType.PREVENTIVE_MAINTENANCE
                && (kind == MaintenanceKind.INSPECTION || kind == MaintenanceKind.DIAGNOSTIC)) {
            return WorkOrderType.INSPECTION;
        }
        return WorkOrderType.PLANNED;
    }

    private WorkType workType(
            MaintenanceRegulation regulation,
            EquipmentMaintenanceRule rule) {
        MaintenanceKind kind = maintenanceKind(regulation, rule);
        return kind == MaintenanceKind.INSPECTION || kind == MaintenanceKind.DIAGNOSTIC
                ? WorkType.DIAGNOSTICS
                : WorkType.REPAIR;
    }

    private MaintenanceKind maintenanceKind(
            MaintenanceRegulation regulation,
            EquipmentMaintenanceRule rule) {
        if (rule != null) {
            return rule.getMaintenanceKind();
        }
        return regulation == null ? null : regulation.getMaintenanceKind();
    }

    private String summary(PprPlan plan, PprTask task) {
        return "Generated from PPR plan %s (%s), task %s"
                .formatted(plan.getCode(), plan.getName(), task.getCode());
    }

    public record GenerationRunResult(int createdCount, List<SkippedItem> skipped) {
    }

    public record SkippedItem(UUID taskId, String reason) {
    }
}
