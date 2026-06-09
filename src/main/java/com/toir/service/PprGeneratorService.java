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
import com.toir.service.maintanance.MaintenanceDueCalculationService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
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
    private final WorkOrderNumberService workOrderNumberService;
    private final MaintenanceDueCalculationService maintenanceDueCalculationService;
    private static final Set<PlanStatus> PLAN_TASK_GENERATION_STATUSES =
            EnumSet.of(PlanStatus.DRAFT, PlanStatus.GENERATED);
    private static final Set<PlanStatus> PLAN_WORK_ORDER_GENERATION_STATUSES =
            EnumSet.of(PlanStatus.APPROVED, PlanStatus.IN_PROGRESS);
    @Transactional
    public GenerationResult generateForPlan(UUID planId) {
        PprPlan plan = planRepository.findByIdAndIsDeletedFalse(planId)
                .orElseThrow(() -> RestException.notFound("PPR plan not found: " + planId));
        if (!PLAN_TASK_GENERATION_STATUSES.contains(plan.getStatus())) {
            log.info("PPR generation rejected: planId={} planCode={} status={} reason={}",
                    plan.getId(), plan.getCode(), plan.getStatus(), "SKIP_STATUS");
            throw RestException.badRequest("PPR tasks can be generated only for DRAFT or GENERATED plans");
        }

        LocalDate planStart = plan.getStartDate();
        LocalDate planEnd = plan.getEndDate();
        if (planStart == null || planEnd == null) {
            log.info("PPR generation rejected: planId={} planCode={} startDate={} endDate={} reason={}",
                    plan.getId(), plan.getCode(), planStart, planEnd, "SKIP_MISSING_DATE");
            throw RestException.badRequest("PPR plan date range is required before generating tasks");
        }
        return generateForPlanFixed(plan, planStart, planEnd);
    }
    private GenerationResult generateForPlanFixed(PprPlan plan, LocalDate planStart, LocalDate planEnd) {
        YearMonth planMonth = YearMonth.from(planStart);
        boolean dueStatusRequired = plan.getScheduleType() == PprScheduleType.OPERATING_HOURS;
        TargetContext targetContext = targetContext(plan);
        GenerationTracker tracker = new GenerationTracker(generationPlanFields(plan));

        List<MaintenanceRegulation> regulations = eligibleRegulations(plan, planMonth, targetContext, tracker);
        List<EquipmentMaintenanceRule> individualRules = eligibleRules(plan, planMonth, targetContext, tracker);
        Set<RegulationOverrideSignature> individualOverrides = individualRules.stream()
                .filter(rule -> rule.getBaseRegulationId() != null)
                .map(rule -> new RegulationOverrideSignature(rule.getBaseRegulationId(), rule.getEquipmentId()))
                .collect(Collectors.toSet());
        Map<UUID, List<MaintenanceRegulationAttributeCondition>> conditionsByRegulationId =
                loadConditionsByRegulationId(regulations);

        List<Equipment> allEquipment = activeEquipment(tracker);
        tracker.candidateCounts(new GenerationCandidateCounts(
                activeRegulationsCount(),
                activeRulesCount(),
                allEquipment.size(),
                matchingEquipmentCount(plan, targetContext, allEquipment)
        ));
        AttributeIndex attributeIndex = loadAttributeIndex(allEquipment);

        List<PprTask> existingTasks = taskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        Set<String> existingCodes = existingTasks.stream()
                .map(PprTask::getCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Set<TaskSignature> existingSignatures = existingTasks.stream()
                .map(PprGeneratorService::taskSignature)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        int created = 0;
        for (MaintenanceRegulation reg : regulations) {
            if (!shouldGenerate(plan, reg, planMonth)) {
                tracker.skip(SkipReason.SKIP_PERIODICITY);
                continue;
            }

            List<MaintenanceRegulationAttributeCondition> conditions =
                    conditionsByRegulationId.getOrDefault(reg.getId(), List.of());
            int seq = 1;
            for (Equipment eq : allEquipment) {
                if (eq.getEquipmentTypeId() == null || !eq.getEquipmentTypeId().equals(reg.getEquipmentTypeId())) {
                    tracker.skip(SkipReason.SKIP_EQUIPMENT_TYPE);
                    continue;
                }
                if (plan.getDepartmentId() != null && !plan.getDepartmentId().equals(eq.getDepartmentId())) {
                    tracker.skip(SkipReason.SKIP_DEPARTMENT);
                    continue;
                }
                if (!targetContext.matchesEquipment(eq)) {
                    tracker.skip(SkipReason.SKIP_TARGET);
                    continue;
                }
                if (!matchesConditions(eq, conditions, attributeIndex)) {
                    tracker.skip(SkipReason.SKIP_ATTRIBUTE_CONDITION);
                    continue;
                }
                if (individualOverrides.contains(new RegulationOverrideSignature(reg.getId(), eq.getId()))) {
                    tracker.skip(SkipReason.SKIP_DUPLICATE_SIGNATURE);
                    continue;
                }
                if (dueStatusRequired && !isDueForGeneration(eq.getId(), reg)) {
                    tracker.skip(SkipReason.SKIP_NOT_DUE);
                    continue;
                }
                TaskSignature signature = new TaskSignature(plan.getId(), reg.getId(), null, eq.getId());
                if (existingSignatures.contains(signature)) {
                    tracker.skip(SkipReason.SKIP_DUPLICATE_SIGNATURE);
                    continue;
                }

                PprTask saved = saveGeneratedTask(
                        plan,
                        uniqueTaskCode(existingCodes, plan.getCode(), reg.getCode(), eq.getCode(), seq++),
                        reg.getId(),
                        null,
                        eq.getId(),
                        reg.getName() + " вЂ” " + eq.getCode(),
                        planStart,
                        resolveScheduledEnd(plan, reg, planStart, planEnd),
                        planEnd,
                        reg.getNormativeLaborHours()
                );
                created++;
                existingCodes.add(saved.getCode());
                existingSignatures.add(signature);
                tracker.created(saved.getId(), saved.getCode());
            }
        }

        Map<UUID, Equipment> equipmentByIdForRules = allEquipment.stream()
                .collect(Collectors.toMap(Equipment::getId, Function.identity(), (left, right) -> left));
        for (EquipmentMaintenanceRule rule : individualRules) {
            if (!shouldGenerate(plan, rule, planMonth)) {
                tracker.skip(SkipReason.SKIP_PERIODICITY);
                continue;
            }
            Equipment eq = equipmentByIdForRules.get(rule.getEquipmentId());
            if (eq == null) {
                tracker.skip(SkipReason.SKIP_DECOMMISSIONED_EQUIPMENT);
                continue;
            }
            if (plan.getDepartmentId() != null && !plan.getDepartmentId().equals(eq.getDepartmentId())) {
                tracker.skip(SkipReason.SKIP_DEPARTMENT);
                continue;
            }
            if (!targetContext.matchesEquipment(eq)) {
                tracker.skip(SkipReason.SKIP_TARGET);
                continue;
            }
            if (dueStatusRequired && !isDueForGeneration(eq.getId(), rule)) {
                tracker.skip(SkipReason.SKIP_NOT_DUE);
                continue;
            }

            TaskSignature signature = new TaskSignature(plan.getId(), null, rule.getId(), eq.getId());
            if (existingSignatures.contains(signature)) {
                tracker.skip(SkipReason.SKIP_DUPLICATE_SIGNATURE);
                continue;
            }

            PprTask saved = saveGeneratedTask(
                    plan,
                    uniqueTaskCode(existingCodes, plan.getCode(), rule.getCode(), eq.getCode(), 1),
                    rule.getBaseRegulationId(),
                    rule.getId(),
                    eq.getId(),
                    rule.getName() + " вЂ” " + eq.getCode(),
                    planStart,
                    resolveScheduledEnd(plan, rule, planStart, planEnd),
                    planEnd,
                    rule.getNormativeLaborHours()
            );
            created++;
            existingCodes.add(saved.getCode());
            existingSignatures.add(signature);
            tracker.created(saved.getId(), saved.getCode());
        }

        if (created > 0 && plan.getStatus() == PlanStatus.DRAFT) {
            plan.setStatus(PlanStatus.GENERATED);
            PprPlan pprPlan = planRepository.save(plan);
            auditBuilderService.log(
                    "ppr_plan",
                    pprPlan.getId().toString(),
                    AuditAction.UPDATE,
                    AuditModule.PPR_PLAN,
                    "РџР»Р°РЅ РџРџР  РѕР±РЅРѕРІР»РµРЅ",
                    plan,
                    pprPlan
            );
        }

        return tracker.result(plan, created);
    }

    private List<MaintenanceRegulation> eligibleRegulations(PprPlan plan,
                                                            YearMonth planMonth,
                                                            TargetContext targetContext,
                                                            GenerationTracker tracker) {
        List<MaintenanceRegulation> regulations = new ArrayList<>();
        for (MaintenanceRegulation regulation : regulationRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            if (!regulation.isActive()) {
                tracker.skip(SkipReason.SKIP_INACTIVE_REGULATION);
                continue;
            }
            if (!targetContext.matchesRegulation(regulation.getId())) {
                tracker.skip(SkipReason.SKIP_TARGET);
                continue;
            }
            if (!isAllowedForPprType(plan, regulation)) {
                tracker.skip(SkipReason.SKIP_SCHEDULE_TYPE);
                continue;
            }
            if (!isAllowedForSchedule(plan, regulation, planMonth, tracker)) {
                continue;
            }
            regulations.add(regulation);
        }
        return regulations;
    }

    private List<EquipmentMaintenanceRule> eligibleRules(PprPlan plan,
                                                         YearMonth planMonth,
                                                         TargetContext targetContext,
                                                         GenerationTracker tracker) {
        List<EquipmentMaintenanceRule> rules = new ArrayList<>();
        for (EquipmentMaintenanceRule rule : equipmentMaintenanceRuleRepository.findAllActive()) {
            if (!rule.isActive()) {
                tracker.skip(SkipReason.SKIP_INACTIVE_RULE);
                continue;
            }
            if (!targetContext.matchesRegulation(rule.getBaseRegulationId())) {
                tracker.skip(SkipReason.SKIP_TARGET);
                continue;
            }
            if (!isAllowedForPprType(plan, rule.getMaintenanceKind())) {
                tracker.skip(SkipReason.SKIP_SCHEDULE_TYPE);
                continue;
            }
            if (!isAllowedForSchedule(plan, rule, planMonth, tracker)) {
                continue;
            }
            rules.add(rule);
        }
        return rules;
    }

    private List<Equipment> activeEquipment(GenerationTracker tracker) {
        List<Equipment> equipment = new ArrayList<>();
        for (Equipment item : equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            if (item.getStatus() == EquipmentStatus.DECOMMISSIONED) {
                tracker.skip(SkipReason.SKIP_DECOMMISSIONED_EQUIPMENT);
                continue;
            }
            equipment.add(item);
        }
        return equipment;
    }

    private long activeRegulationsCount() {
        return regulationRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(MaintenanceRegulation::isActive)
                .count();
    }

    private long activeRulesCount() {
        return equipmentMaintenanceRuleRepository.findAllActive().stream()
                .filter(EquipmentMaintenanceRule::isActive)
                .count();
    }

    private long matchingEquipmentCount(PprPlan plan, TargetContext targetContext, List<Equipment> allEquipment) {
        return allEquipment.stream()
                .filter(equipment -> plan.getDepartmentId() == null || plan.getDepartmentId().equals(equipment.getDepartmentId()))
                .filter(targetContext::matchesEquipment)
                .count();
    }

    private PprTask saveGeneratedTask(PprPlan plan,
                                      String code,
                                      UUID regulationId,
                                      UUID equipmentMaintenanceRuleId,
                                      UUID equipmentId,
                                      String title,
                                      LocalDate planStart,
                                      java.time.LocalDateTime scheduledEnd,
                                      LocalDate planEnd,
                                      double plannedLaborHours) {
        PprTask task = new PprTask();
        task.setCode(code);
        task.setPlan(plan);
        task.setRegulationId(regulationId);
        task.setEquipmentMaintenanceRuleId(equipmentMaintenanceRuleId);
        task.setEquipmentId(equipmentId);
        task.setTitle(title);
        task.setScheduledStart(planStart.atTime(LocalTime.of(9, 0)));
        task.setScheduledEnd(scheduledEnd);
        task.setDueDate(planEnd.atTime(LocalTime.of(18, 0)));
        task.setPlannedLaborHours(plannedLaborHours);
        task.setPriority(PriorityLevel.MEDIUM);
        task.setStatus(PprTaskStatus.PLANNED);

        plan.getTasks().add(task);
        PprTask saved = taskRepository.save(task);
        auditBuilderService.log(
                "ppr_task",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.PPR_TASK,
                "Р—Р°РґР°С‡Р° РџРџР  СЃРѕР·РґР°РЅР°",
                null,
                saved
        );
        return saved;
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
        Set<String> reservedWorkOrderNumbers = new HashSet<>();

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
            String workOrderNumber = workOrderNumberService.nextPprNumber(reservedWorkOrderNumbers);
            reservedWorkOrderNumbers.add(workOrderNumber);
            WorkOrderRequest request = new WorkOrderRequest(
                    workOrderNumber,
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

    private boolean isDueForGeneration(UUID equipmentId, MaintenanceRegulation regulation) {
        MaintenanceDueStatus status = maintenanceDueCalculationService.calculate(equipmentId, regulation).status();
        return status == MaintenanceDueStatus.DUE || status == MaintenanceDueStatus.OVERDUE;
    }

    private boolean isDueForGeneration(UUID equipmentId, EquipmentMaintenanceRule rule) {
        MaintenanceDueStatus status = maintenanceDueCalculationService.calculate(equipmentId, rule).status();
        return status == MaintenanceDueStatus.DUE || status == MaintenanceDueStatus.OVERDUE;
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

    private boolean isAllowedForSchedule(PprPlan plan,
                                         MaintenanceRegulation regulation,
                                         YearMonth planMonth) {
        return isAllowedForSchedule(plan, regulation.getPeriodicityUnit(), regulation, null, planMonth, null);
    }

    private boolean isAllowedForSchedule(PprPlan plan,
                                         EquipmentMaintenanceRule rule,
                                         YearMonth planMonth) {
        return isAllowedForSchedule(plan, rule.getPeriodicityUnit(), null, rule, planMonth, null);
    }

    private boolean isAllowedForSchedule(PprPlan plan,
                                         MaintenanceRegulation regulation,
                                         YearMonth planMonth,
                                         GenerationTracker tracker) {
        return isAllowedForSchedule(plan, regulation.getPeriodicityUnit(), regulation, null, planMonth, tracker);
    }

    private boolean isAllowedForSchedule(PprPlan plan,
                                         EquipmentMaintenanceRule rule,
                                         YearMonth planMonth,
                                         GenerationTracker tracker) {
        return isAllowedForSchedule(plan, rule.getPeriodicityUnit(), null, rule, planMonth, tracker);
    }

    private boolean isAllowedForSchedule(PprPlan plan,
                                         PeriodicityUnit periodicityUnit,
                                         MaintenanceRegulation regulation,
                                         EquipmentMaintenanceRule rule,
                                         YearMonth planMonth,
                                         GenerationTracker tracker) {
        PprScheduleType scheduleType = plan.getScheduleType();
        if (scheduleType == null) {
            return true;
        }
        if (scheduleType == PprScheduleType.ONE_TIME) {
            return true;
        }
        if (scheduleType == PprScheduleType.CALENDAR && plan.getFrequency() != null) {
            boolean matches = periodicityUnit == periodicityUnit(plan.getFrequency());
            if (!matches && tracker != null) {
                tracker.skip(SkipReason.SKIP_FREQUENCY);
            }
            return matches;
        }
        boolean matches = regulation != null
                ? shouldGenerate(plan, regulation, planMonth)
                : shouldGenerate(plan, rule, planMonth);
        if (!matches && tracker != null) {
            tracker.skip(SkipReason.SKIP_SCHEDULE_TYPE);
        }
        return matches;
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

    private String uniqueTaskCode(Set<String> existingCodes,
                                  String planCode,
                                  String sourceCode,
                                  String equipmentCode,
                                  int sequence) {
        String base = "PT-%s-%s-%s".formatted(
                safeCodePart(planCode),
                safeCodePart(sourceCode),
                safeCodePart(equipmentCode)
        );
        String code = "%s-%d".formatted(base, sequence);
        while (existingCodes.contains(code)) {
            sequence++;
            code = "%s-%d".formatted(base, sequence);
        }
        return code;
    }

    private String safeCodePart(String value) {
        if (value == null || value.isBlank()) {
            return "NA";
        }
        return value.replaceAll("[^A-Za-z0-9-]", "-");
    }

    private TargetContext targetContext(PprPlan plan) {
        if (plan.getTargets() == null || plan.getTargets().isEmpty()) {
            return TargetContext.empty();
        }
        Set<UUID> equipmentIds = new HashSet<>();
        Set<UUID> equipmentTypeIds = new HashSet<>();
        Set<UUID> regulationIds = new HashSet<>();
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
                    if (target.getTargetType() == PprTargetType.REGULATION && target.getRegulationId() != null) {
                        regulationIds.add(target.getRegulationId());
                    }
                });
        return new TargetContext(equipmentIds, equipmentTypeIds, regulationIds);
    }

    private GenerationPlanFields generationPlanFields(PprPlan plan) {
        List<GenerationPlanTarget> targets = plan.getTargets() == null
                ? List.of()
                : plan.getTargets().stream()
                .filter(Objects::nonNull)
                .filter(target -> !target.isDeleted())
                .map(target -> new GenerationPlanTarget(
                        target.getTargetType(),
                        target.getEquipmentId(),
                        target.getEquipmentTypeId(),
                        target.getRegulationId()
                ))
                .toList();
        return new GenerationPlanFields(
                plan.getId(),
                plan.getCode(),
                plan.getStatus(),
                plan.getDepartmentId(),
                plan.getPprType(),
                plan.getScheduleType(),
                plan.getFrequency(),
                plan.getStartDate(),
                plan.getEndDate(),
                targets
        );
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

    private record TargetContext(Set<UUID> equipmentIds, Set<UUID> equipmentTypeIds, Set<UUID> regulationIds) {
        static TargetContext empty() {
            return new TargetContext(Set.of(), Set.of(), Set.of());
        }

        boolean hasEquipmentTargets() {
            return !equipmentIds.isEmpty() || !equipmentTypeIds.isEmpty();
        }

        boolean matches(Equipment equipment) {
            return matchesEquipment(equipment);
        }

        boolean matchesEquipment(Equipment equipment) {
            if (!hasEquipmentTargets()) {
                return true;
            }
            return equipmentIds.contains(equipment.getId())
                    || equipmentTypeIds.contains(equipment.getEquipmentTypeId());
        }

        boolean hasRegulationTargets() {
            return !regulationIds.isEmpty();
        }

        boolean matchesRegulation(UUID regulationId) {
            if (!hasRegulationTargets()) {
                return true;
            }
            return regulationId != null && regulationIds.contains(regulationId);
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

    private enum SkipReason {
        SKIP_DEPARTMENT,
        SKIP_TARGET,
        SKIP_EQUIPMENT_TYPE,
        SKIP_ATTRIBUTE_CONDITION,
        SKIP_SCHEDULE_TYPE,
        SKIP_FREQUENCY,
        SKIP_PERIODICITY,
        SKIP_NOT_DUE,
        SKIP_DUPLICATE_SIGNATURE,
        SKIP_INACTIVE_REGULATION,
        SKIP_INACTIVE_RULE,
        SKIP_DECOMMISSIONED_EQUIPMENT
    }

    private static class GenerationTracker {
        private final GenerationPlanFields planFields;
        private final Map<String, Integer> skippedReasons = new LinkedHashMap<>();
        private final List<UUID> createdTaskIds = new ArrayList<>();
        private final List<String> createdTaskCodes = new ArrayList<>();
        private GenerationCandidateCounts candidateCounts;

        private GenerationTracker(GenerationPlanFields planFields) {
            this.planFields = planFields;
        }

        void skip(SkipReason reason) {
            skippedReasons.merge(reason.name(), 1, Integer::sum);
        }

        void candidateCounts(GenerationCandidateCounts counts) {
            this.candidateCounts = counts;
        }

        void created(UUID taskId, String taskCode) {
            if (taskId != null) {
                createdTaskIds.add(taskId);
            }
            if (taskCode != null && !taskCode.isBlank()) {
                createdTaskCodes.add(taskCode);
            }
        }

        GenerationResult result(PprPlan plan, int created) {
            int skipped = skippedReasons.values().stream().mapToInt(Integer::intValue).sum();
            String message = created > 0
                    ? "Generated %d PPR task(s).".formatted(created)
                    : "No PPR tasks were generated. Skipped candidates: %d. Reasons: %s".formatted(skipped, skippedReasons);
            Map<String, Integer> diagnosticReasons = diagnosticSkippedReasons(skippedReasons);
            GenerationDiagnostics diagnostics = new GenerationDiagnostics(
                    true,
                    planFields,
                    candidateCounts,
                    created,
                    skipped,
                    List.copyOf(createdTaskIds),
                    List.copyOf(createdTaskCodes),
                    diagnosticReasons,
                    message
            );
            log.info(
                    "PPR generation diagnostics: planId={} planCode={} status={} departmentId={} pprType={} scheduleType={} frequency={} startDate={} endDate={} created={} skipped={} createdTaskIds={} createdTaskCodes={} skippedReasons={} message={}",
                    plan.getId(),
                    plan.getCode(),
                    plan.getStatus(),
                    plan.getDepartmentId(),
                    plan.getPprType(),
                    plan.getScheduleType(),
                    plan.getFrequency(),
                    plan.getStartDate(),
                    plan.getEndDate(),
                    created,
                    skipped,
                    createdTaskIds,
                    createdTaskCodes,
                    diagnosticReasons,
                    message
            );
            return new GenerationResult(
                    plan.getId(),
                    created,
                    skipped,
                    List.copyOf(createdTaskIds),
                    List.copyOf(createdTaskCodes),
                    Map.copyOf(skippedReasons),
                    message,
                    diagnostics
            );
        }

        private Map<String, Integer> diagnosticSkippedReasons(Map<String, Integer> source) {
            Map<String, Integer> reasons = new LinkedHashMap<>();
            reasons.put("SKIP_STATUS", 0);
            reasons.put("SKIP_MISSING_DATE", 0);
            reasons.put("SKIP_INACTIVE_REGULATION", source.getOrDefault(SkipReason.SKIP_INACTIVE_REGULATION.name(), 0));
            reasons.put("SKIP_INACTIVE_RULE", source.getOrDefault(SkipReason.SKIP_INACTIVE_RULE.name(), 0));
            reasons.put("SKIP_DECOMMISSIONED_EQUIPMENT", source.getOrDefault(SkipReason.SKIP_DECOMMISSIONED_EQUIPMENT.name(), 0));
            reasons.put("SKIP_DEPARTMENT_MISMATCH", source.getOrDefault(SkipReason.SKIP_DEPARTMENT.name(), 0));
            reasons.put("SKIP_TARGET_MISMATCH", source.getOrDefault(SkipReason.SKIP_TARGET.name(), 0));
            reasons.put("SKIP_REGULATION_TARGET_MISMATCH", source.getOrDefault(SkipReason.SKIP_TARGET.name(), 0));
            reasons.put("SKIP_EQUIPMENT_TYPE_MISMATCH", source.getOrDefault(SkipReason.SKIP_EQUIPMENT_TYPE.name(), 0));
            reasons.put("SKIP_ATTRIBUTE_CONDITION", source.getOrDefault(SkipReason.SKIP_ATTRIBUTE_CONDITION.name(), 0));
            reasons.put("SKIP_SCHEDULE_TYPE_MISMATCH", source.getOrDefault(SkipReason.SKIP_SCHEDULE_TYPE.name(), 0));
            reasons.put("SKIP_FREQUENCY_MISMATCH", source.getOrDefault(SkipReason.SKIP_FREQUENCY.name(), 0));
            reasons.put("SKIP_PERIODICITY_MISMATCH", source.getOrDefault(SkipReason.SKIP_PERIODICITY.name(), 0));
            reasons.put("SKIP_OPERATING_HOURS_NOT_DUE", source.getOrDefault(SkipReason.SKIP_NOT_DUE.name(), 0));
            reasons.put("SKIP_DUPLICATE_SIGNATURE", source.getOrDefault(SkipReason.SKIP_DUPLICATE_SIGNATURE.name(), 0));
            reasons.put("SKIP_DUPLICATE_CODE", 0);
            return reasons;
        }
    }

    public record GenerationResult(
            UUID planId,
            int created,
            int skipped,
            List<UUID> createdTaskIds,
            List<String> createdTaskCodes,
            Map<String, Integer> skippedReasons,
            String message,
            GenerationDiagnostics generationDiagnostics
    ) {
        public GenerationResult(UUID planId, int created, int skipped) {
            this(planId, created, skipped, List.of(), List.of(), Map.of(), null, null);
        }
    }

    public record GenerationDiagnostics(
            boolean generateForPlanCalled,
            GenerationPlanFields planFields,
            GenerationCandidateCounts candidateCounts,
            int createdCount,
            int skippedCount,
            List<UUID> createdTaskIds,
            List<String> createdTaskCodes,
            Map<String, Integer> skippedReasons,
            String message
    ) {}

    public record GenerationPlanFields(
            UUID planId,
            String planCode,
            PlanStatus status,
            UUID departmentId,
            PprType pprType,
            PprScheduleType scheduleType,
            PprFrequency frequency,
            LocalDate startDate,
            LocalDate endDate,
            List<GenerationPlanTarget> targets
    ) {}

    public record GenerationPlanTarget(
            PprTargetType targetType,
            UUID equipmentId,
            UUID equipmentTypeId,
            UUID regulationId
    ) {}

    public record GenerationCandidateCounts(
            long activeRegulationsCount,
            long activeEquipmentMaintenanceRulesCount,
            long activeEquipmentCount,
            long matchingEquipmentCount
    ) {}

    public record WorkOrderGenerationResult(
            UUID planId,
            int createdCount,
            int skippedCount,
            List<UUID> createdWorkOrderIds,
            List<WorkOrderGenerationSkippedItem> skippedItems
    ) {}

    public record WorkOrderGenerationSkippedItem(UUID pprTaskId, String reason) {}
}
