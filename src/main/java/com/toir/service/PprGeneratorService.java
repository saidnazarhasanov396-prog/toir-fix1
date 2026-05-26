package com.toir.service;

import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentAttributeDefinition;
import com.toir.entity.equipment.EquipmentAttributeValue;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.entity.maintenance.MaintenanceRegulationAttributeCondition;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.enums.*;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.equipment.EquipmentAttributeDefinitionRepository;
import com.toir.repository.equipment.EquipmentAttributeValueRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceRegulationAttributeConditionRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.repository.maintenance.EquipmentMaintenanceRuleRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PprGeneratorService {

    private final PprPlanRepository planRepository;
    private final PprTaskRepository taskRepository;
    private final MaintenanceRegulationRepository regulationRepository;
    private final EquipmentMaintenanceRuleRepository equipmentMaintenanceRuleRepository;
    private final MaintenanceRegulationAttributeConditionRepository conditionRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentAttributeDefinitionRepository attributeDefinitionRepository;
    private final EquipmentAttributeValueRepository attributeValueRepository;
    private final AuditBuilderService auditBuilderService;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderService workOrderService;
    private static final Set<PlanStatus> PLAN_TASK_GENERATION_STATUSES =
            EnumSet.of(PlanStatus.DRAFT, PlanStatus.GENERATED);
    private static final Set<PlanStatus> PLAN_WORK_ORDER_GENERATION_STATUSES =
            EnumSet.of(PlanStatus.APPROVED, PlanStatus.IN_PROGRESS);
    private static final int MAX_WORK_ORDER_NUMBER_GENERATION_ATTEMPTS = 5000;

    @Transactional
    public GenerationResult generateForPlan(UUID planId) {
        PprPlan plan = planRepository.findByIdAndIsDeletedFalse(planId)
                .orElseThrow(() -> RestException.notFound("PPR plan not found: " + planId));
        if (!PLAN_TASK_GENERATION_STATUSES.contains(plan.getStatus())) {
            throw RestException.badRequest("PPR tasks can be generated only for DRAFT or GENERATED plans");
        }

        LocalDate planStart = plan.getStartDate() != null
                ? plan.getStartDate()
                : null;
        LocalDate planEnd = plan.getEndDate() != null
                ? plan.getEndDate()
                : null;
        if (planStart == null || planEnd == null) {
            throw RestException.badRequest("PPR plan date range is required before generating tasks");
        }
        if (plan.getScheduleType() == PprScheduleType.OPERATING_HOURS) {
            throw RestException.badRequest("Operating-hours PPR generation is not implemented yet");
        }
        YearMonth planMonth = YearMonth.from(planStart);
        TargetContext targetContext = targetContext(plan);

        List<MaintenanceRegulation> regulations = regulationRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(MaintenanceRegulation::isActive)
                .filter(regulation -> isAllowedForPprType(plan, regulation))
                .filter(regulation -> isAllowedForSchedule(plan, regulation, planMonth))
                .toList();
        List<EquipmentMaintenanceRule> individualRules = equipmentMaintenanceRuleRepository.findAllActive().stream()
                .filter(rule -> isAllowedForPprType(plan, rule.getMaintenanceKind()))
                .filter(rule -> isAllowedForSchedule(plan, rule, planMonth))
                .toList();
        Set<RegulationOverrideSignature> individualOverrides = individualRules.stream()
                .filter(rule -> rule.getBaseRegulationId() != null)
                .map(rule -> new RegulationOverrideSignature(rule.getBaseRegulationId(), rule.getEquipmentId()))
                .collect(Collectors.toSet());
        Map<UUID, List<MaintenanceRegulationAttributeCondition>> conditionsByRegulationId =
                loadConditionsByRegulationId(regulations);

        List<Equipment> allEquipment = equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(e -> e.getStatus() != EquipmentStatus.DECOMMISSIONED)
                .toList();
        AttributeIndex attributeIndex = loadAttributeIndex(allEquipment);

        List<PprTask> existingTasks = taskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        Set<String> existingCodes = existingTasks.stream()
                .map(PprTask::getCode)
                .collect(Collectors.toSet());
        Set<TaskSignature> existingSignatures = existingTasks.stream()
                .map(PprGeneratorService::taskSignature)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        int created = 0;
        int skipped = 0;

        for (MaintenanceRegulation reg : regulations) {
            // only generate if this plan's month aligns with periodicity:
            // DAY — always, WEEK/MONTH — always, QUARTER — if month ∈ {1,4,7,10}, YEAR/HALF — if month == 1
            if (!shouldGenerate(plan, reg, planMonth)) {
                continue;
            }

            // filter equipment by type
            List<Equipment> matching = new ArrayList<>();
            List<MaintenanceRegulationAttributeCondition> conditions =
                    conditionsByRegulationId.getOrDefault(reg.getId(), List.of());
            for (Equipment eq : allEquipment) {
                if (eq.getEquipmentTypeId() != null && eq.getEquipmentTypeId().equals(reg.getEquipmentTypeId())) {
                    // department scope for the plan (if any)
                    if (plan.getDepartmentId() != null
                            && !plan.getDepartmentId().equals(eq.getDepartmentId())) {
                        continue;
                    }
                    if (!targetContext.matches(eq)) {
                        continue;
                    }
                    if (!matchesConditions(eq, conditions, attributeIndex)) {
                        continue;
                    }
                    if (individualOverrides.contains(new RegulationOverrideSignature(reg.getId(), eq.getId()))) {
                        skipped++;
                        continue;
                    }
                    matching.add(eq);
                }
            }

            int seq = 1;
            for (Equipment eq : matching) {
                String code = String.format("PT-%s-%02d-%s-%d", reg.getCode(), planMonth.getMonthValue(), eq.getCode(), seq++);
                TaskSignature signature = new TaskSignature(plan.getId(), reg.getId(), null, eq.getId());
                if (existingCodes.contains(code) || existingSignatures.contains(signature)) {
                    skipped++;
                    continue;
                }

                PprTask task = new PprTask();
                task.setCode(code);
                task.setPlan(plan);
                task.setRegulationId(reg.getId());
                task.setEquipmentId(eq.getId());
                task.setTitle(reg.getName() + " — " + eq.getCode());
                task.setScheduledStart(planStart.atTime(LocalTime.of(9, 0)));
                task.setScheduledEnd(resolveScheduledEnd(plan, reg, planStart, planEnd));
                task.setDueDate(planEnd.atTime(LocalTime.of(18, 0)));
                task.setPlannedLaborHours(reg.getNormativeLaborHours());
                task.setPriority(PriorityLevel.MEDIUM);
                task.setStatus(PprTaskStatus.PLANNED);

                plan.getTasks().add(task);
                PprTask saved = taskRepository.save(task);

                auditBuilderService.log(
                        "ppr_task",
                        saved.getId().toString(),
                        AuditAction.CREATE,
                        AuditModule.PPR_TASK,
                        "Задача ППР создана",
                        null,
                        saved
                );

                created++;
                existingCodes.add(code);
                existingSignatures.add(signature);
            }
        }

        Map<UUID, Equipment> equipmentByIdForRules = allEquipment.stream()
                .collect(Collectors.toMap(Equipment::getId, Function.identity(), (left, right) -> left));
        for (EquipmentMaintenanceRule rule : individualRules) {
            if (!shouldGenerate(plan, rule, planMonth)) {
                continue;
            }
            Equipment eq = equipmentByIdForRules.get(rule.getEquipmentId());
            if (eq == null) {
                skipped++;
                continue;
            }
            if (plan.getDepartmentId() != null && !plan.getDepartmentId().equals(eq.getDepartmentId())) {
                continue;
            }
            if (!targetContext.matches(eq)) {
                continue;
            }

            String code = String.format("PT-%s-%02d-%s", rule.getCode(), planMonth.getMonthValue(), eq.getCode());
            TaskSignature signature = new TaskSignature(plan.getId(), null, rule.getId(), eq.getId());
            if (existingCodes.contains(code) || existingSignatures.contains(signature)) {
                skipped++;
                continue;
            }

            PprTask task = new PprTask();
            task.setCode(code);
            task.setPlan(plan);
            task.setRegulationId(rule.getBaseRegulationId());
            task.setEquipmentMaintenanceRuleId(rule.getId());
            task.setEquipmentId(eq.getId());
            task.setTitle(rule.getName() + " — " + eq.getCode());
            task.setScheduledStart(planStart.atTime(LocalTime.of(9, 0)));
            task.setScheduledEnd(resolveScheduledEnd(plan, rule, planStart, planEnd));
            task.setDueDate(planEnd.atTime(LocalTime.of(18, 0)));
            task.setPlannedLaborHours(rule.getNormativeLaborHours());
            task.setPriority(PriorityLevel.MEDIUM);
            task.setStatus(PprTaskStatus.PLANNED);

            plan.getTasks().add(task);
            PprTask saved = taskRepository.save(task);
            auditBuilderService.log(
                    "ppr_task",
                    saved.getId().toString(),
                    AuditAction.CREATE,
                    AuditModule.PPR_TASK,
                    "Задача ППР создана",
                    null,
                    saved
            );
            created++;
            existingCodes.add(code);
            existingSignatures.add(signature);
        }

        if (created > 0 && plan.getStatus() == PlanStatus.DRAFT) {
            plan.setStatus(PlanStatus.GENERATED);

            PprPlan pprPlan = planRepository.save(plan);

            auditBuilderService.log(
                    "ppr_plan",
                    pprPlan.getId().toString(),
                    AuditAction.UPDATE,
                    AuditModule.PPR_PLAN,
                    "План ППР обновлен",
                    plan,
                    pprPlan
            );
        }

        return new GenerationResult(plan.getId(), created, skipped);
    }

    @Transactional
    public WorkOrderGenerationResult generateWorkOrdersForPlan(UUID planId, UUID createdById) {
        if (createdById == null) {
            throw RestException.badRequest("createdById is required to generate PPR work orders");
        }
        PprPlan plan = planRepository.findByIdAndIsDeletedFalse(planId)
                .orElseThrow(() -> RestException.notFound("PPR plan not found: " + planId));
        if (!PLAN_WORK_ORDER_GENERATION_STATUSES.contains(plan.getStatus())) {
            throw RestException.badRequest("PPR work orders can be generated only for APPROVED or IN_PROGRESS plans");
        }

        List<PprTask> tasks = taskRepository.findAllByPlanIdAndIsDeletedFalseOrderByScheduledStartAscIdAsc(planId);
        List<WorkOrderGenerationSkippedItem> skippedItems = new ArrayList<>();
        List<PprTask> candidates = new ArrayList<>();
        for (PprTask task : tasks) {
            if (task.getStatus() != PprTaskStatus.APPROVED) {
                skippedItems.add(new WorkOrderGenerationSkippedItem(task.getId(), "TASK_STATUS_NOT_APPROVED"));
                continue;
            }
            if (task.getEquipmentId() == null) {
                skippedItems.add(new WorkOrderGenerationSkippedItem(task.getId(), "TASK_EQUIPMENT_MISSING"));
                continue;
            }
            if (workOrderRepository.existsByPprTaskIdAndIsDeletedFalse(task.getId())) {
                skippedItems.add(new WorkOrderGenerationSkippedItem(task.getId(), "WORK_ORDER_ALREADY_EXISTS"));
                continue;
            }
            candidates.add(task);
        }

        Map<UUID, Equipment> equipmentById = loadEquipmentById(candidates);
        Map<UUID, MaintenanceRegulation> regulationById = loadRegulationById(candidates);
        Map<UUID, EquipmentMaintenanceRule> maintenanceRuleById = loadMaintenanceRuleById(candidates);
        List<UUID> createdWorkOrderIds = new ArrayList<>();
        Set<String> reservedNumbers = new HashSet<>();

        for (PprTask task : candidates) {
            Equipment equipment = equipmentById.get(task.getEquipmentId());
            if (equipment == null) {
                skippedItems.add(new WorkOrderGenerationSkippedItem(task.getId(), "EQUIPMENT_NOT_FOUND"));
                continue;
            }
            UUID departmentId = equipment.getDepartmentId() != null ? equipment.getDepartmentId() : plan.getDepartmentId();
            if (departmentId == null) {
                skippedItems.add(new WorkOrderGenerationSkippedItem(task.getId(), "DEPARTMENT_MISSING"));
                continue;
            }
            MaintenanceRegulation regulation = regulationById.get(task.getRegulationId());
            EquipmentMaintenanceRule maintenanceRule = maintenanceRuleById.get(task.getEquipmentMaintenanceRuleId());
            WorkOrderRequest request = new WorkOrderRequest(
                    generateWorkOrderNumber(reservedNumbers),
                    task.getTitle(),
                    task.getEquipmentId(),
                    null,
                    departmentId,
                    null,
                    null,
                    task.getId(),
                    null,
                    workOrderType(plan, regulation, maintenanceRule),
                    workType(regulation, maintenanceRule),
                    null,
                    null,
                    task.getPriority(),
                    task.getScheduledStart().atZone(ZoneId.systemDefault()).toInstant(),
                    task.getScheduledEnd().atZone(ZoneId.systemDefault()).toInstant(),
                    createdById,
                    workOrderSummary(plan, task)
            );
            WorkOrderDto created = workOrderService.create(request);
            createdWorkOrderIds.add(created.id());
        }

        return new WorkOrderGenerationResult(
                planId,
                createdWorkOrderIds.size(),
                skippedItems.size(),
                List.copyOf(createdWorkOrderIds),
                List.copyOf(skippedItems)
        );
    }

    private boolean shouldGenerate(PprPlan plan, MaintenanceRegulation reg, YearMonth planMonth) {
        return shouldGenerate(plan, reg.getPeriodicityUnit(), reg.getPeriodicityValue(), planMonth);
    }

    private boolean shouldGenerate(PprPlan plan, EquipmentMaintenanceRule rule, YearMonth planMonth) {
        return shouldGenerate(plan, rule.getPeriodicityUnit(), rule.getPeriodicityValue(), planMonth);
    }

    private boolean shouldGenerate(PprPlan plan,
                                   PeriodicityUnit unit,
                                   int value,
                                   YearMonth planMonth) {
        if (plan.getScheduleType() == PprScheduleType.ONE_TIME) {
            return true;
        }
        int month = planMonth.getMonthValue();
        return switch (unit) {
            case DAY, WEEK, MONTH -> true;
            case QUARTER -> (month - 1) % (3 * value) == 0;
            case YEAR -> month == 1;
            case HOUR -> true; // hour-based — run every month, operator decides
        };
    }

    private boolean isAllowedForPprType(PprPlan plan, MaintenanceRegulation regulation) {
        return isAllowedForPprType(plan, regulation.getMaintenanceKind());
    }

    private boolean isAllowedForPprType(PprPlan plan, MaintenanceKind kind) {
        if (plan.getPprType() == null) {
            return true;
        }
        return switch (plan.getPprType()) {
            case PREVENTIVE_MAINTENANCE -> EnumSet.of(
                    MaintenanceKind.PREVENTIVE,
                    MaintenanceKind.INSPECTION,
                    MaintenanceKind.DIAGNOSTIC,
                    MaintenanceKind.CONDITION_BASED,
                    MaintenanceKind.SEASONAL,
                    MaintenanceKind.METROLOGICAL,
                    MaintenanceKind.ELECTRICAL,
                    MaintenanceKind.INSTRUMENTATION
            ).contains(kind);
            case PLANNED_REPAIR -> EnumSet.of(
                    MaintenanceKind.CURRENT_REPAIR,
                    MaintenanceKind.MEDIUM_REPAIR
            ).contains(kind);
            case CAPITAL_REPAIR -> kind == MaintenanceKind.OVERHAUL;
        };
    }

    private Map<UUID, Equipment> loadEquipmentById(List<PprTask> tasks) {
        List<UUID> equipmentIds = tasks.stream()
                .map(PprTask::getEquipmentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (equipmentIds.isEmpty()) {
            return new HashMap<>();
        }
        return equipmentRepository.findAllByIdInAndIsDeletedFalse(equipmentIds).stream()
                .collect(Collectors.toMap(Equipment::getId, Function.identity(), (left, right) -> left));
    }

    private Map<UUID, MaintenanceRegulation> loadRegulationById(List<PprTask> tasks) {
        List<UUID> regulationIds = tasks.stream()
                .map(PprTask::getRegulationId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (regulationIds.isEmpty()) {
            return new HashMap<>();
        }
        return regulationRepository.findAllByIdInAndIsDeletedFalse(regulationIds).stream()
                .collect(Collectors.toMap(MaintenanceRegulation::getId, Function.identity(), (left, right) -> left));
    }

    private Map<UUID, EquipmentMaintenanceRule> loadMaintenanceRuleById(List<PprTask> tasks) {
        List<UUID> ruleIds = tasks.stream()
                .map(PprTask::getEquipmentMaintenanceRuleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ruleIds.isEmpty()) {
            return new HashMap<>();
        }
        return equipmentMaintenanceRuleRepository.findAllByIdInAndIsDeletedFalse(ruleIds).stream()
                .collect(Collectors.toMap(EquipmentMaintenanceRule::getId, Function.identity(), (left, right) -> left));
    }

    private WorkOrderType workOrderType(PprPlan plan,
                                        MaintenanceRegulation regulation,
                                        EquipmentMaintenanceRule maintenanceRule) {
        PprType pprType = plan.getPprType();
        if (pprType == PprType.CAPITAL_REPAIR) {
            return WorkOrderType.OVERHAUL;
        }
        if (pprType == PprType.PREVENTIVE_MAINTENANCE
                && isInspectionOrDiagnostic(maintenanceKind(regulation, maintenanceRule))) {
            return WorkOrderType.INSPECTION;
        }
        return WorkOrderType.PLANNED;
    }

    private WorkType workType(MaintenanceRegulation regulation, EquipmentMaintenanceRule maintenanceRule) {
        return isInspectionOrDiagnostic(maintenanceKind(regulation, maintenanceRule))
                ? WorkType.DIAGNOSTICS
                : WorkType.REPAIR;
    }

    private MaintenanceKind maintenanceKind(MaintenanceRegulation regulation,
                                            EquipmentMaintenanceRule maintenanceRule) {
        if (maintenanceRule != null) {
            return maintenanceRule.getMaintenanceKind();
        }
        return regulation != null ? regulation.getMaintenanceKind() : null;
    }

    private boolean isInspectionOrDiagnostic(MaintenanceKind kind) {
        return kind == MaintenanceKind.INSPECTION || kind == MaintenanceKind.DIAGNOSTIC;
    }

    private String workOrderSummary(PprPlan plan, PprTask task) {
        return "Generated from PPR plan %s (%s), task %s"
                .formatted(plan.getCode(), plan.getName(), task.getCode());
    }

    private String generateWorkOrderNumber(Set<String> reservedNumbers) {
        int year = Year.now().getValue();
        String prefix = "WO-PPR-" + year + "-";
        for (int sequence = 1; sequence <= MAX_WORK_ORDER_NUMBER_GENERATION_ATTEMPTS; sequence++) {
            String number = prefix + String.format("%04d", sequence);
            if (reservedNumbers.contains(number)) {
                continue;
            }
            if (workOrderRepository.existsByNumberAndIsDeletedFalse(number)) {
                continue;
            }
            reservedNumbers.add(number);
            return number;
        }
        throw RestException.conflict("Could not generate unique PPR work order number");
    }

    private boolean isAllowedForSchedule(PprPlan plan,
                                         MaintenanceRegulation regulation,
                                         YearMonth planMonth) {
        return isAllowedForSchedule(plan, regulation.getPeriodicityUnit(), regulation, null, planMonth);
    }

    private boolean isAllowedForSchedule(PprPlan plan,
                                         EquipmentMaintenanceRule rule,
                                         YearMonth planMonth) {
        return isAllowedForSchedule(plan, rule.getPeriodicityUnit(), null, rule, planMonth);
    }

    private boolean isAllowedForSchedule(PprPlan plan,
                                         PeriodicityUnit periodicityUnit,
                                         MaintenanceRegulation regulation,
                                         EquipmentMaintenanceRule rule,
                                         YearMonth planMonth) {
        PprScheduleType scheduleType = plan.getScheduleType();
        if (scheduleType == null) {
            return true;
        }
        if (scheduleType == PprScheduleType.ONE_TIME) {
            return true;
        }
        if (scheduleType == PprScheduleType.CALENDAR && plan.getFrequency() != null) {
            return periodicityUnit == periodicityUnit(plan.getFrequency());
        }
        return regulation != null
                ? shouldGenerate(plan, regulation, planMonth)
                : shouldGenerate(plan, rule, planMonth);
    }

    private PeriodicityUnit periodicityUnit(PprFrequency frequency) {
        return switch (frequency) {
            case WEEKLY -> PeriodicityUnit.WEEK;
            case MONTHLY -> PeriodicityUnit.MONTH;
            case QUARTERLY -> PeriodicityUnit.QUARTER;
            case YEARLY -> PeriodicityUnit.YEAR;
        };
    }

    private LocalTime scheduledEndTime() {
        return LocalTime.of(18, 0);
    }

    private java.time.LocalDateTime resolveScheduledEnd(PprPlan plan,
                                                        MaintenanceRegulation regulation,
                                                        LocalDate planStart,
                                                        LocalDate planEnd) {
        return resolveScheduledEnd(plan, regulation.getNormativeLaborHours(), planStart, planEnd);
    }

    private java.time.LocalDateTime resolveScheduledEnd(PprPlan plan,
                                                        EquipmentMaintenanceRule rule,
                                                        LocalDate planStart,
                                                        LocalDate planEnd) {
        return resolveScheduledEnd(plan, rule.getNormativeLaborHours(), planStart, planEnd);
    }

    private java.time.LocalDateTime resolveScheduledEnd(PprPlan plan,
                                                        double normativeLaborHours,
                                                        LocalDate planStart,
                                                        LocalDate planEnd) {
        if (plan.getScheduleType() == PprScheduleType.ONE_TIME) {
            return planEnd.atTime(scheduledEndTime());
        }
        return planStart.plusDays(Math.max(1, (int) Math.ceil(normativeLaborHours / 8)))
                .atTime(scheduledEndTime());
    }

    private TargetContext targetContext(PprPlan plan) {
        if (plan.getTargets() == null || plan.getTargets().isEmpty()) {
            return TargetContext.empty();
        }
        Set<UUID> equipmentIds = new HashSet<>();
        Set<UUID> equipmentTypeIds = new HashSet<>();
        plan.getTargets().stream()
                .filter(Objects::nonNull)
                .filter(target -> !target.isDeleted())
                .forEach(target -> {
                    if (target.getTargetType() == PprTargetType.EQUIPMENT && target.getEquipmentId() != null) {
                        equipmentIds.add(target.getEquipmentId());
                    }
                    if (target.getTargetType() == PprTargetType.EQUIPMENT_TYPE && target.getEquipmentTypeId() != null) {
                        equipmentTypeIds.add(target.getEquipmentTypeId());
                    }
                });
        return new TargetContext(equipmentIds, equipmentTypeIds);
    }

    private Map<UUID, List<MaintenanceRegulationAttributeCondition>> loadConditionsByRegulationId(
            List<MaintenanceRegulation> regulations) {
        if (regulations.isEmpty()) {
            return Map.of();
        }
        List<UUID> regulationIds = regulations.stream()
                .map(MaintenanceRegulation::getId)
                .toList();
        return conditionRepository.findAllByRegulationIdInAndIsDeletedFalse(regulationIds)
                .stream()
                .collect(Collectors.groupingBy(MaintenanceRegulationAttributeCondition::getRegulationId));
    }

    private AttributeIndex loadAttributeIndex(List<Equipment> equipment) {
        if (equipment.isEmpty()) {
            return new AttributeIndex(Map.of(), Map.of());
        }
        Set<UUID> equipmentTypeIds = equipment.stream()
                .map(Equipment::getEquipmentTypeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> equipmentIds = equipment.stream()
                .map(Equipment::getId)
                .collect(Collectors.toSet());

        Map<UUID, Map<String, EquipmentAttributeDefinition>> definitionsByTypeId = equipmentTypeIds.isEmpty()
                ? Map.of()
                : attributeDefinitionRepository.findAllByEquipmentTypeIdInAndIsDeletedFalse(equipmentTypeIds)
                .stream()
                .collect(Collectors.groupingBy(
                        EquipmentAttributeDefinition::getEquipmentTypeId,
                        Collectors.toMap(EquipmentAttributeDefinition::getKey, Function.identity(), (a, b) -> a)
                ));

        Map<UUID, Map<UUID, EquipmentAttributeValue>> valuesByEquipmentId = new HashMap<>();
        List<EquipmentAttributeValue> attributeValues = equipmentIds.isEmpty()
                ? List.of()
                : attributeValueRepository.findAllByEquipmentIdInAndIsDeletedFalse(equipmentIds);
        for (EquipmentAttributeValue value : attributeValues) {
            valuesByEquipmentId
                    .computeIfAbsent(value.getEquipmentId(), ignored -> new HashMap<>())
                    .put(value.getAttributeDefinitionId(), value);
        }
        return new AttributeIndex(definitionsByTypeId, valuesByEquipmentId);
    }

    private boolean matchesConditions(Equipment equipment,
                                      List<MaintenanceRegulationAttributeCondition> conditions,
                                      AttributeIndex attributeIndex) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        Map<String, EquipmentAttributeDefinition> definitions = attributeIndex.definitionsByTypeId()
                .getOrDefault(equipment.getEquipmentTypeId(), Map.of());
        Map<UUID, EquipmentAttributeValue> values = attributeIndex.valuesByEquipmentId()
                .getOrDefault(equipment.getId(), Map.of());

        for (MaintenanceRegulationAttributeCondition condition : conditions) {
            EquipmentAttributeDefinition definition = definitions.get(condition.getAttributeKey());
            EquipmentAttributeValue value = definition == null ? null : values.get(definition.getId());
            if (!matchesCondition(condition, value)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesCondition(MaintenanceRegulationAttributeCondition condition,
                                     EquipmentAttributeValue value) {
        MaintenanceRegulationConditionOperator operator = condition.getOperator();
        if (operator == MaintenanceRegulationConditionOperator.EXISTS) {
            return value != null && hasAnyValue(value);
        }
        if (operator == MaintenanceRegulationConditionOperator.NOT_EXISTS) {
            return value == null || !hasAnyValue(value);
        }
        if (value == null || !hasAnyValue(value)) {
            return false;
        }
        if (condition.getValueNumber() != null) {
            return compareNumbers(value.getValueNumber(), condition.getValueNumber(), operator);
        }
        if (condition.getValueDate() != null) {
            return compareComparable(value.getValueDate(), condition.getValueDate(), operator);
        }
        if (condition.getValueBoolean() != null) {
            return compareEquals(value.getValueBoolean(), condition.getValueBoolean(), operator);
        }
        if (condition.getValueOption() != null) {
            return compareEquals(value.getValueOption(), condition.getValueOption(), operator);
        }
        if (condition.getValueText() != null) {
            return compareEquals(value.getValueText(), condition.getValueText(), operator);
        }
        return false;
    }

    private boolean hasAnyValue(EquipmentAttributeValue value) {
        return value.getValueText() != null
                || value.getValueNumber() != null
                || value.getValueDate() != null
                || value.getValueBoolean() != null
                || value.getValueOption() != null
                || value.getValueJson() != null;
    }

    private boolean compareNumbers(Double actual,
                                   Double expected,
                                   MaintenanceRegulationConditionOperator operator) {
        if (actual == null) {
            return false;
        }
        return switch (operator) {
            case EQUALS -> Double.compare(actual, expected) == 0;
            case NOT_EQUALS -> Double.compare(actual, expected) != 0;
            case GREATER_THAN -> actual > expected;
            case GREATER_THAN_OR_EQUALS -> actual >= expected;
            case LESS_THAN -> actual < expected;
            case LESS_THAN_OR_EQUALS -> actual <= expected;
            case EXISTS, NOT_EXISTS -> false;
        };
    }

    private <T extends Comparable<T>> boolean compareComparable(T actual,
                                                                T expected,
                                                                MaintenanceRegulationConditionOperator operator) {
        if (actual == null) {
            return false;
        }
        int comparison = actual.compareTo(expected);
        return switch (operator) {
            case EQUALS -> comparison == 0;
            case NOT_EQUALS -> comparison != 0;
            case GREATER_THAN -> comparison > 0;
            case GREATER_THAN_OR_EQUALS -> comparison >= 0;
            case LESS_THAN -> comparison < 0;
            case LESS_THAN_OR_EQUALS -> comparison <= 0;
            case EXISTS, NOT_EXISTS -> false;
        };
    }

    private boolean compareEquals(Object actual,
                                  Object expected,
                                  MaintenanceRegulationConditionOperator operator) {
        if (operator == MaintenanceRegulationConditionOperator.EQUALS) {
            return Objects.equals(actual, expected);
        }
        if (operator == MaintenanceRegulationConditionOperator.NOT_EQUALS) {
            return !Objects.equals(actual, expected);
        }
        return false;
    }

    private record AttributeIndex(
            Map<UUID, Map<String, EquipmentAttributeDefinition>> definitionsByTypeId,
            Map<UUID, Map<UUID, EquipmentAttributeValue>> valuesByEquipmentId
    ) {}

    private record TargetContext(Set<UUID> equipmentIds, Set<UUID> equipmentTypeIds) {
        static TargetContext empty() {
            return new TargetContext(Set.of(), Set.of());
        }

        boolean hasTargets() {
            return !equipmentIds.isEmpty() || !equipmentTypeIds.isEmpty();
        }

        boolean matches(Equipment equipment) {
            if (!hasTargets()) {
                return true;
            }
            return equipmentIds.contains(equipment.getId())
                    || equipmentTypeIds.contains(equipment.getEquipmentTypeId());
        }
    }

    private record RegulationOverrideSignature(UUID regulationId, UUID equipmentId) {}

    private record TaskSignature(UUID planId, UUID regulationId, UUID equipmentMaintenanceRuleId, UUID equipmentId) {}

    private static TaskSignature taskSignature(PprTask task) {
        if (task == null || task.getPlan() == null || task.getPlan().getId() == null) {
            return null;
        }
        if (task.getRegulationId() == null && task.getEquipmentMaintenanceRuleId() == null) {
            return null;
        }
        return new TaskSignature(
                task.getPlan().getId(),
                task.getRegulationId(),
                task.getEquipmentMaintenanceRuleId(),
                task.getEquipmentId()
        );
    }

    public record GenerationResult(UUID planId, int created, int skipped) {}

    public record WorkOrderGenerationResult(
            UUID planId,
            int createdCount,
            int skippedCount,
            List<UUID> createdWorkOrderIds,
            List<WorkOrderGenerationSkippedItem> skippedItems
    ) {}

    public record WorkOrderGenerationSkippedItem(UUID pprTaskId, String reason) {}
}
