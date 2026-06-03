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
import java.util.EnumMap;
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
    private final MaintenanceDueCalculationService maintenanceDueCalculationService;
    private static final Set<PlanStatus> PLAN_TASK_GENERATION_STATUSES =
            EnumSet.of(PlanStatus.DRAFT, PlanStatus.GENERATED);
    private static final Set<PlanStatus> PLAN_WORK_ORDER_GENERATION_STATUSES =
            EnumSet.of(PlanStatus.APPROVED, PlanStatus.IN_PROGRESS);
    private static final int MAX_WORK_ORDER_NUMBER_GENERATION_ATTEMPTS = 5000;

    @Transactional
    public GenerationResult generateForPlan(UUID planId) {
        PprPlan plan = planRepository.findByIdAndIsDeletedFalse(planId)
                .orElseThrow(() -> RestException.notFound("PPR plan not found: " + planId));
        EnumMap<SkipReason, Integer> skippedReasons = emptySkippedReasons();
        List<UUID> createdTaskIds = new ArrayList<>();
        List<String> createdTaskCodes = new ArrayList<>();
        GenerationPlanFields planFields = generationPlanFields(plan);
        if (!PLAN_TASK_GENERATION_STATUSES.contains(plan.getStatus())) {
            increment(skippedReasons, SkipReason.SKIP_STATUS);
            logGenerationSummary(plan, 0, 1, skippedReasons, createdTaskIds, createdTaskCodes,
                    "PPR task generation rejected because plan status is not DRAFT or GENERATED");
            throw RestException.badRequest("PPR tasks can be generated only for DRAFT or GENERATED plans");
        }

        LocalDate planStart = plan.getStartDate() != null
                ? plan.getStartDate()
                : null;
        LocalDate planEnd = plan.getEndDate() != null
                ? plan.getEndDate()
                : null;
        if (planStart == null || planEnd == null) {
            increment(skippedReasons, SkipReason.SKIP_MISSING_DATE);
            logGenerationSummary(plan, 0, 1, skippedReasons, createdTaskIds, createdTaskCodes,
                    "PPR task generation rejected because plan date range is missing");
            throw RestException.badRequest("PPR plan date range is required before generating tasks");
        }
        YearMonth planMonth = YearMonth.from(planStart);
        boolean dueStatusRequired = plan.getScheduleType() == PprScheduleType.OPERATING_HOURS;
        TargetContext targetContext = targetContext(plan);

        List<MaintenanceRegulation> allRegulations = regulationRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        List<MaintenanceRegulation> activeRegulations = allRegulations.stream()
                .filter(MaintenanceRegulation::isActive)
                .toList();
        for (MaintenanceRegulation regulation : allRegulations) {
            if (!regulation.isActive()) {
                skip(skippedReasons, SkipReason.SKIP_INACTIVE_REGULATION,
                        "planId={} regulationId={} regulationCode={} reason={}",
                        plan.getId(), regulation.getId(), regulation.getCode(), SkipReason.SKIP_INACTIVE_REGULATION);
            }
        }
        List<MaintenanceRegulation> regulations = new ArrayList<>();
        for (MaintenanceRegulation regulation : activeRegulations) {
            if (!isAllowedForPprType(plan, regulation)) {
                skip(skippedReasons, SkipReason.SKIP_REGULATION_TARGET_MISMATCH,
                        "planId={} regulationId={} regulationCode={} maintenanceKind={} pprType={} reason={}",
                        plan.getId(), regulation.getId(), regulation.getCode(), regulation.getMaintenanceKind(),
                        plan.getPprType(), SkipReason.SKIP_REGULATION_TARGET_MISMATCH);
                continue;
            }
            if (!isAllowedForSchedule(plan, regulation, planMonth)) {
                skip(skippedReasons, scheduleSkipReason(plan, regulation.getPeriodicityUnit()),
                        "planId={} regulationId={} regulationCode={} scheduleType={} frequency={} periodicityUnit={} reason={}",
                        plan.getId(), regulation.getId(), regulation.getCode(), plan.getScheduleType(),
                        plan.getFrequency(), regulation.getPeriodicityUnit(), scheduleSkipReason(plan, regulation.getPeriodicityUnit()));
                continue;
            }
            regulations.add(regulation);
        }
        List<EquipmentMaintenanceRule> allIndividualRules = equipmentMaintenanceRuleRepository.findAll().stream()
                .filter(rule -> !rule.isDeleted())
                .toList();
        for (EquipmentMaintenanceRule rule : allIndividualRules) {
            if (!rule.isActive()) {
                skip(skippedReasons, SkipReason.SKIP_INACTIVE_RULE,
                        "planId={} ruleId={} ruleCode={} reason={}",
                        plan.getId(), rule.getId(), rule.getCode(), SkipReason.SKIP_INACTIVE_RULE);
            }
        }
        List<EquipmentMaintenanceRule> activeIndividualRules = allIndividualRules.stream()
                .filter(EquipmentMaintenanceRule::isActive)
                .toList();
        List<EquipmentMaintenanceRule> individualRules = new ArrayList<>();
        for (EquipmentMaintenanceRule rule : activeIndividualRules) {
            if (!isAllowedForPprType(plan, rule.getMaintenanceKind())) {
                skip(skippedReasons, SkipReason.SKIP_REGULATION_TARGET_MISMATCH,
                        "planId={} ruleId={} ruleCode={} maintenanceKind={} pprType={} reason={}",
                        plan.getId(), rule.getId(), rule.getCode(), rule.getMaintenanceKind(), plan.getPprType(),
                        SkipReason.SKIP_REGULATION_TARGET_MISMATCH);
                continue;
            }
            if (!isAllowedForSchedule(plan, rule, planMonth)) {
                skip(skippedReasons, scheduleSkipReason(plan, rule.getPeriodicityUnit()),
                        "planId={} ruleId={} ruleCode={} scheduleType={} frequency={} periodicityUnit={} reason={}",
                        plan.getId(), rule.getId(), rule.getCode(), plan.getScheduleType(), plan.getFrequency(),
                        rule.getPeriodicityUnit(), scheduleSkipReason(plan, rule.getPeriodicityUnit()));
                continue;
            }
            individualRules.add(rule);
        }
        Set<RegulationOverrideSignature> individualOverrides = individualRules.stream()
                .filter(rule -> rule.getBaseRegulationId() != null)
                .map(rule -> new RegulationOverrideSignature(rule.getBaseRegulationId(), rule.getEquipmentId()))
                .collect(Collectors.toSet());
        Map<UUID, List<MaintenanceRegulationAttributeCondition>> conditionsByRegulationId =
                loadConditionsByRegulationId(regulations);

        List<Equipment> persistedEquipment = equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        List<Equipment> allEquipment = new ArrayList<>();
        for (Equipment equipment : persistedEquipment) {
            if (equipment.getStatus() == EquipmentStatus.DECOMMISSIONED) {
                skip(skippedReasons, SkipReason.SKIP_DECOMMISSIONED_EQUIPMENT,
                        "planId={} equipmentId={} equipmentCode={} equipmentStatus={} reason={}",
                        plan.getId(), equipment.getId(), equipment.getCode(), equipment.getStatus(),
                        SkipReason.SKIP_DECOMMISSIONED_EQUIPMENT);
                continue;
            }
            allEquipment.add(equipment);
        }
        long matchingEquipmentCount = allEquipment.stream()
                .filter(equipment -> plan.getDepartmentId() == null || plan.getDepartmentId().equals(equipment.getDepartmentId()))
                .filter(targetContext::matches)
                .count();
        GenerationCandidateCounts candidateCounts = new GenerationCandidateCounts(
                activeRegulations.size(),
                activeIndividualRules.size(),
                allEquipment.size(),
                matchingEquipmentCount
        );
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
                skip(skippedReasons, SkipReason.SKIP_PERIODICITY_MISMATCH,
                        "planId={} regulationId={} regulationCode={} scheduleType={} frequency={} periodicityUnit={} reason={}",
                        plan.getId(), reg.getId(), reg.getCode(), plan.getScheduleType(), plan.getFrequency(),
                        reg.getPeriodicityUnit(), SkipReason.SKIP_PERIODICITY_MISMATCH);
                skipped++;
                continue;
            }

            // filter equipment by type
            List<Equipment> matching = new ArrayList<>();
            List<MaintenanceRegulationAttributeCondition> conditions =
                    conditionsByRegulationId.getOrDefault(reg.getId(), List.of());
            for (Equipment eq : allEquipment) {
                if (eq.getEquipmentTypeId() == null || !eq.getEquipmentTypeId().equals(reg.getEquipmentTypeId())) {
                    skip(skippedReasons, SkipReason.SKIP_EQUIPMENT_TYPE_MISMATCH,
                            "planId={} regulationId={} regulationCode={} equipmentId={} equipmentCode={} equipmentTypeId={} expectedEquipmentTypeId={} reason={}",
                            plan.getId(), reg.getId(), reg.getCode(), eq.getId(), eq.getCode(),
                            eq.getEquipmentTypeId(), reg.getEquipmentTypeId(), SkipReason.SKIP_EQUIPMENT_TYPE_MISMATCH);
                    skipped++;
                    continue;
                }
                // department scope for the plan (if any)
                if (plan.getDepartmentId() != null
                        && !plan.getDepartmentId().equals(eq.getDepartmentId())) {
                    skip(skippedReasons, SkipReason.SKIP_DEPARTMENT_MISMATCH,
                            "planId={} regulationId={} regulationCode={} equipmentId={} equipmentCode={} planDepartmentId={} equipmentDepartmentId={} reason={}",
                            plan.getId(), reg.getId(), reg.getCode(), eq.getId(), eq.getCode(),
                            plan.getDepartmentId(), eq.getDepartmentId(), SkipReason.SKIP_DEPARTMENT_MISMATCH);
                    skipped++;
                    continue;
                }
                if (!targetContext.matches(eq)) {
                    skip(skippedReasons, SkipReason.SKIP_TARGET_MISMATCH,
                            "planId={} regulationId={} regulationCode={} equipmentId={} equipmentCode={} targetEquipmentIds={} targetEquipmentTypeIds={} reason={}",
                            plan.getId(), reg.getId(), reg.getCode(), eq.getId(), eq.getCode(),
                            targetContext.equipmentIds(), targetContext.equipmentTypeIds(), SkipReason.SKIP_TARGET_MISMATCH);
                    skipped++;
                    continue;
                }
                if (!matchesConditions(eq, conditions, attributeIndex)) {
                    skip(skippedReasons, SkipReason.SKIP_ATTRIBUTE_CONDITION,
                            "planId={} regulationId={} regulationCode={} equipmentId={} equipmentCode={} conditionCount={} reason={}",
                            plan.getId(), reg.getId(), reg.getCode(), eq.getId(), eq.getCode(), conditions.size(),
                            SkipReason.SKIP_ATTRIBUTE_CONDITION);
                    skipped++;
                    continue;
                }
                if (individualOverrides.contains(new RegulationOverrideSignature(reg.getId(), eq.getId()))) {
                    skip(skippedReasons, SkipReason.SKIP_DUPLICATE_SIGNATURE,
                            "planId={} regulationId={} regulationCode={} equipmentId={} equipmentCode={} reason={}",
                            plan.getId(), reg.getId(), reg.getCode(), eq.getId(), eq.getCode(),
                            SkipReason.SKIP_DUPLICATE_SIGNATURE);
                    skipped++;
                    continue;
                }
                if (dueStatusRequired && !isDueForGeneration(eq.getId(), reg)) {
                    skip(skippedReasons, SkipReason.SKIP_OPERATING_HOURS_NOT_DUE,
                            "planId={} regulationId={} regulationCode={} equipmentId={} equipmentCode={} reason={}",
                            plan.getId(), reg.getId(), reg.getCode(), eq.getId(), eq.getCode(),
                            SkipReason.SKIP_OPERATING_HOURS_NOT_DUE);
                    skipped++;
                    continue;
                }
                matching.add(eq);
            }

            int seq = 1;
            for (Equipment eq : matching) {
                String code = String.format("PT-%s-%02d-%s-%d", reg.getCode(), planMonth.getMonthValue(), eq.getCode(), seq++);
                TaskSignature signature = new TaskSignature(plan.getId(), reg.getId(), null, eq.getId());
                if (existingSignatures.contains(signature)) {
                    skip(skippedReasons, SkipReason.SKIP_DUPLICATE_SIGNATURE,
                            "planId={} regulationId={} regulationCode={} equipmentId={} equipmentCode={} reason={}",
                            plan.getId(), reg.getId(), reg.getCode(), eq.getId(), eq.getCode(),
                            SkipReason.SKIP_DUPLICATE_SIGNATURE);
                    skipped++;
                    continue;
                }
                if (existingCodes.contains(code)) {
                    skip(skippedReasons, SkipReason.SKIP_DUPLICATE_CODE,
                            "planId={} regulationId={} regulationCode={} equipmentId={} equipmentCode={} taskCode={} reason={}",
                            plan.getId(), reg.getId(), reg.getCode(), eq.getId(), eq.getCode(), code,
                            SkipReason.SKIP_DUPLICATE_CODE);
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
                createdTaskIds.add(saved.getId());
                createdTaskCodes.add(saved.getCode());
                existingCodes.add(code);
                existingSignatures.add(signature);
            }
        }

        Map<UUID, Equipment> equipmentByIdForRules = allEquipment.stream()
                .collect(Collectors.toMap(Equipment::getId, Function.identity(), (left, right) -> left));
        for (EquipmentMaintenanceRule rule : individualRules) {
            if (!shouldGenerate(plan, rule, planMonth)) {
                skip(skippedReasons, SkipReason.SKIP_PERIODICITY_MISMATCH,
                        "planId={} ruleId={} ruleCode={} scheduleType={} frequency={} periodicityUnit={} reason={}",
                        plan.getId(), rule.getId(), rule.getCode(), plan.getScheduleType(), plan.getFrequency(),
                        rule.getPeriodicityUnit(), SkipReason.SKIP_PERIODICITY_MISMATCH);
                skipped++;
                continue;
            }
            Equipment eq = equipmentByIdForRules.get(rule.getEquipmentId());
            if (eq == null) {
                skip(skippedReasons, SkipReason.SKIP_INACTIVE_RULE,
                        "planId={} ruleId={} ruleCode={} equipmentId={} reason={}",
                        plan.getId(), rule.getId(), rule.getCode(), rule.getEquipmentId(), SkipReason.SKIP_INACTIVE_RULE);
                skipped++;
                continue;
            }
            if (plan.getDepartmentId() != null && !plan.getDepartmentId().equals(eq.getDepartmentId())) {
                skip(skippedReasons, SkipReason.SKIP_DEPARTMENT_MISMATCH,
                        "planId={} ruleId={} ruleCode={} equipmentId={} equipmentCode={} planDepartmentId={} equipmentDepartmentId={} reason={}",
                        plan.getId(), rule.getId(), rule.getCode(), eq.getId(), eq.getCode(), plan.getDepartmentId(),
                        eq.getDepartmentId(), SkipReason.SKIP_DEPARTMENT_MISMATCH);
                skipped++;
                continue;
            }
            if (!targetContext.matches(eq)) {
                skip(skippedReasons, SkipReason.SKIP_TARGET_MISMATCH,
                        "planId={} ruleId={} ruleCode={} equipmentId={} equipmentCode={} targetEquipmentIds={} targetEquipmentTypeIds={} reason={}",
                        plan.getId(), rule.getId(), rule.getCode(), eq.getId(), eq.getCode(), targetContext.equipmentIds(),
                        targetContext.equipmentTypeIds(), SkipReason.SKIP_TARGET_MISMATCH);
                skipped++;
                continue;
            }
            if (dueStatusRequired && !isDueForGeneration(eq.getId(), rule)) {
                skip(skippedReasons, SkipReason.SKIP_OPERATING_HOURS_NOT_DUE,
                        "planId={} ruleId={} ruleCode={} equipmentId={} equipmentCode={} reason={}",
                        plan.getId(), rule.getId(), rule.getCode(), eq.getId(), eq.getCode(),
                        SkipReason.SKIP_OPERATING_HOURS_NOT_DUE);
                skipped++;
                continue;
            }

            String code = String.format("PT-%s-%02d-%s", rule.getCode(), planMonth.getMonthValue(), eq.getCode());
            TaskSignature signature = new TaskSignature(plan.getId(), null, rule.getId(), eq.getId());
            if (existingSignatures.contains(signature)) {
                skip(skippedReasons, SkipReason.SKIP_DUPLICATE_SIGNATURE,
                        "planId={} ruleId={} ruleCode={} equipmentId={} equipmentCode={} reason={}",
                        plan.getId(), rule.getId(), rule.getCode(), eq.getId(), eq.getCode(),
                        SkipReason.SKIP_DUPLICATE_SIGNATURE);
                skipped++;
                continue;
            }
            if (existingCodes.contains(code)) {
                skip(skippedReasons, SkipReason.SKIP_DUPLICATE_CODE,
                        "planId={} ruleId={} ruleCode={} equipmentId={} equipmentCode={} taskCode={} reason={}",
                        plan.getId(), rule.getId(), rule.getCode(), eq.getId(), eq.getCode(), code,
                        SkipReason.SKIP_DUPLICATE_CODE);
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
            createdTaskIds.add(saved.getId());
            createdTaskCodes.add(saved.getCode());
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

        String message = created == 0
                ? "PPR generation completed with 0 created tasks; inspect generationDiagnostics.skippedReasons."
                : "PPR generation completed with %d created task(s).".formatted(created);
        GenerationDiagnostics diagnostics = new GenerationDiagnostics(
                true,
                planFields,
                candidateCounts,
                created,
                skipped,
                List.copyOf(createdTaskIds),
                List.copyOf(createdTaskCodes),
                stringSkippedReasons(skippedReasons),
                message
        );
        logGenerationSummary(plan, created, skipped, skippedReasons, createdTaskIds, createdTaskCodes, message);
        return new GenerationResult(plan.getId(), created, skipped, createdTaskIds, createdTaskCodes,
                stringSkippedReasons(skippedReasons), message, diagnostics);
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

    private static EnumMap<SkipReason, Integer> emptySkippedReasons() {
        EnumMap<SkipReason, Integer> reasons = new EnumMap<>(SkipReason.class);
        for (SkipReason reason : SkipReason.values()) {
            reasons.put(reason, 0);
        }
        return reasons;
    }

    private static void increment(EnumMap<SkipReason, Integer> reasons, SkipReason reason) {
        reasons.merge(reason, 1, Integer::sum);
    }

    private void skip(EnumMap<SkipReason, Integer> reasons,
                      SkipReason reason,
                      String debugMessage,
                      Object... args) {
        increment(reasons, reason);
        log.debug(debugMessage, args);
    }

    private SkipReason scheduleSkipReason(PprPlan plan, PeriodicityUnit periodicityUnit) {
        if (plan.getScheduleType() != null
                && plan.getScheduleType() != PprScheduleType.ONE_TIME
                && plan.getScheduleType() != PprScheduleType.CALENDAR) {
            return SkipReason.SKIP_SCHEDULE_TYPE_MISMATCH;
        }
        if (plan.getScheduleType() == PprScheduleType.CALENDAR
                && plan.getFrequency() != null
                && periodicityUnit != periodicityUnit(plan.getFrequency())) {
            return SkipReason.SKIP_FREQUENCY_MISMATCH;
        }
        return SkipReason.SKIP_PERIODICITY_MISMATCH;
    }

    private static Map<String, Integer> stringSkippedReasons(EnumMap<SkipReason, Integer> reasons) {
        return reasons.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().name(),
                        Map.Entry::getValue,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));
    }

    private void logGenerationSummary(PprPlan plan,
                                      int created,
                                      int skipped,
                                      EnumMap<SkipReason, Integer> skippedReasons,
                                      List<UUID> createdTaskIds,
                                      List<String> createdTaskCodes,
                                      String message) {
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
                stringSkippedReasons(skippedReasons),
                message
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

    private enum SkipReason {
        SKIP_STATUS,
        SKIP_MISSING_DATE,
        SKIP_INACTIVE_REGULATION,
        SKIP_INACTIVE_RULE,
        SKIP_DECOMMISSIONED_EQUIPMENT,
        SKIP_DEPARTMENT_MISMATCH,
        SKIP_TARGET_MISMATCH,
        SKIP_REGULATION_TARGET_MISMATCH,
        SKIP_EQUIPMENT_TYPE_MISMATCH,
        SKIP_ATTRIBUTE_CONDITION,
        SKIP_SCHEDULE_TYPE_MISMATCH,
        SKIP_FREQUENCY_MISMATCH,
        SKIP_PERIODICITY_MISMATCH,
        SKIP_OPERATING_HOURS_NOT_DUE,
        SKIP_DUPLICATE_SIGNATURE,
        SKIP_DUPLICATE_CODE
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
