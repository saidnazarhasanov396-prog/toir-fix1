package com.toir.service.maintanance;

import com.toir.dto.maintenanceplanning.MaintenanceDueCalculationDto;
import com.toir.dto.maintenancedue.MaintenanceDueEventDto;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.AutomationAction;
import com.toir.enums.DuplicatePolicy;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceTriggerSource;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprScheduleType;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.PprType;
import com.toir.enums.PriorityLevel;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.repository.users.UserRepository;
import com.toir.service.WorkOrderService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaintenanceAutomationService {

    private static final UUID SYSTEM_USER_ID = new UUID(0L, 0L);

    private final EquipmentRepository equipmentRepository;
    private final MaintenanceRegulationRepository regulationRepository;
    private final MaintenanceDueEventRepository eventRepository;
    private final MaintenanceDueEventService eventService;
    private final MaintenanceDueCalculationService dueCalculationService;
    private final PprPlanRepository pprPlanRepository;
    private final PprTaskRepository pprTaskRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderService workOrderService;
    private final UserRepository userRepository;
    private final EquipmentMaintenanceEffectiveRuleResolver effectiveRuleResolver;

    @Transactional
    public EvaluationResult evaluateEquipment(UUID equipmentId, MaintenanceTriggerSource source) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        if (equipment.getStatus() == EquipmentStatus.DECOMMISSIONED) {
            return new EvaluationResult(1, 0, 0, 0, 0);
        }
        List<EquipmentMaintenanceEffectiveRule> rules = effectiveRuleResolver.resolveApplicable(equipmentId);
        int createdOrUpdated = 0;
        int suppressed = 0;
        int tasks = 0;
        int workOrders = 0;
        for (EquipmentMaintenanceEffectiveRule rule : rules) {
            try {
                MaintenanceDueEvent event = evaluate(equipment, rule, source, null);
                if (event == null) {
                    continue;
                }
                createdOrUpdated++;
                if (event.getStatus() == MaintenanceDueEventStatus.SUPPRESSED_DUPLICATE) {
                    suppressed++;
                }
                if (event.getCreatedTaskId() != null) {
                    tasks++;
                }
                if (event.getCreatedWorkOrderId() != null) {
                    workOrders++;
                }
            } catch (RuntimeException ex) {
                log.warn("Maintenance automation failed equipmentId={} regulationId={} ruleId={}",
                        equipmentId, rule.regulationId(), rule.equipmentMaintenanceRuleId(), ex);
            }
        }
        return new EvaluationResult(1, createdOrUpdated, suppressed, tasks, workOrders);
    }

    @Transactional
    public EvaluationResult evaluateRegulation(UUID regulationId, MaintenanceTriggerSource source) {
        MaintenanceRegulation regulation = regulationRepository.findByIdAndIsDeletedFalse(regulationId)
                .orElseThrow(() -> RestException.notFound("Maintenance regulation not found: " + regulationId));
        List<Equipment> equipment = equipmentRepository.findAllForMaintenanceRegulations(regulation.getEquipmentTypeId());
        int events = 0;
        int suppressed = 0;
        int tasks = 0;
        int workOrders = 0;
        for (Equipment item : equipment) {
            if (item.getStatus() == EquipmentStatus.DECOMMISSIONED) {
                continue;
            }
            try {
                List<EquipmentMaintenanceEffectiveRule> rules = effectiveRuleResolver.resolveApplicable(item.getId())
                        .stream()
                        .filter(rule -> Objects.equals(rule.regulationId(), regulationId))
                        .toList();
                for (EquipmentMaintenanceEffectiveRule rule : rules) {
                    MaintenanceDueEvent event = evaluate(item, rule, source, null);
                    if (event == null) {
                        continue;
                    }
                    events++;
                    if (event.getStatus() == MaintenanceDueEventStatus.SUPPRESSED_DUPLICATE) {
                        suppressed++;
                    }
                    if (event.getCreatedTaskId() != null) {
                        tasks++;
                    }
                    if (event.getCreatedWorkOrderId() != null) {
                        workOrders++;
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("Maintenance automation failed equipmentId={} regulationId={}", item.getId(), regulationId, ex);
            }
        }
        return new EvaluationResult(equipment.size(), events, suppressed, tasks, workOrders);
    }

    @Transactional
    public EvaluationResult evaluateAllCalendarRules() {
        int checked = 0;
        int events = 0;
        int suppressed = 0;
        int tasks = 0;
        int workOrders = 0;
        List<Equipment> equipment = equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        for (Equipment item : equipment) {
            if (item.getStatus() == EquipmentStatus.DECOMMISSIONED) {
                continue;
            }
            EvaluationResult result = evaluateEquipment(item.getId(), MaintenanceTriggerSource.CALENDAR_JOB);
            checked += result.checkedEquipment();
            events += result.events();
            suppressed += result.suppressed();
            tasks += result.tasksCreated();
            workOrders += result.workOrdersCreated();
        }
        return new EvaluationResult(checked, events, suppressed, tasks, workOrders);
    }

    @Transactional
    public MaintenanceDueEventDto approveDueEvent(UUID eventId, UUID userId) {
        MaintenanceDueEvent event = eventService.getOrThrow(eventId);
        if (isBlocked(event)) {
            return blockedEventDto(event);
        }
        if (event.getStatus() != MaintenanceDueEventStatus.AWAITING_APPROVAL
                && event.getStatus() != MaintenanceDueEventStatus.DETECTED) {
            return eventService.toDto(event);
        }
        EquipmentMaintenanceEffectiveRule rule = effectiveRule(event);
        if (rule.automationAction() == AutomationAction.CREATE_TASK
                || rule.automationAction() == AutomationAction.REQUIRE_APPROVAL) {
            createTask(event, rule, userId);
        } else {
            createWorkOrder(event, rule, userId);
        }
        return eventService.toDto(eventRepository.save(event));
    }

    @Transactional
    public MaintenanceDueEventDto createWorkOrderFromEvent(UUID eventId, UUID userId) {
        MaintenanceDueEvent event = eventService.getOrThrow(eventId);
        if (isBlocked(event)) {
            return blockedEventDto(event);
        }
        EquipmentMaintenanceEffectiveRule rule = effectiveRule(event);
        createWorkOrder(event, rule, userId);
        return eventService.toDto(eventRepository.save(event));
    }

    private MaintenanceDueEvent evaluate(Equipment equipment,
                                         EquipmentMaintenanceEffectiveRule rule,
                                         MaintenanceTriggerSource source,
                                         UUID userId) {
        if (!rule.applicable() || !rule.active()) {
            return null;
        }
        if (rule.triggerPolicy() != null && rule.triggerPolicy().name().equals("MANUAL")) {
            return null;
        }
        if (source == MaintenanceTriggerSource.METER_READING && !rule.hasMeterTrigger()) {
            return null;
        }
        MaintenanceDueCalculationDto due = dueCalculationService.calculate(rule);
        if (!shouldCreateEvent(due.status())) {
            return null;
        }
        String cycleKey = cycleKey(equipment.getId(), rule, due);
        MaintenanceDueEvent event = eventRepository
                .findByScopeAndCycleKey(equipment.getId(), rule.regulationId(), rule.equipmentMaintenanceRuleId(), cycleKey)
                .orElseGet(MaintenanceDueEvent::new);
        boolean isNew = event.getId() == null;
        event.setEquipmentId(equipment.getId());
        event.setRegulationId(rule.regulationId());
        event.setEquipmentMaintenanceRuleId(rule.equipmentMaintenanceRuleId());
        event.setTemplateId(rule.templateId());
        event.setDueStatus(due.status());
        event.setTriggerSource(source);
        event.setCycleKey(cycleKey);
        event.setDueAt(due.nextDueAt());
        event.setMeterType(due.meterType());
        event.setMeterCurrentValue(due.meterCurrentValue());
        event.setMeterAnchorValue(due.meterAnchorValue());
        event.setMeterInterval(due.meterInterval());
        event.setMeterRemaining(due.meterRemaining());
        event.setExplanation(due.explanation());
        if (isNew) {
            event.setDetectedAt(Instant.now());
            event.setStatus(initialStatus(rule, due.status()));
        }
        if (isDuplicateSuppressed(rule, event)) {
            event.setStatus(MaintenanceDueEventStatus.SUPPRESSED_DUPLICATE);
            return eventService.saveEvent(event, equipment);
        }
        event = eventService.saveEvent(event, equipment);
        if (due.status() == MaintenanceDueStatus.BLOCKED
                || rule.automationAction() == AutomationAction.TRACK_ONLY
                || rule.automationAction() == AutomationAction.REQUIRE_APPROVAL) {
            return event;
        }
        if (rule.automationAction() == AutomationAction.CREATE_TASK) {
            createTask(event, rule, userId);
        } else if (rule.automationAction() == AutomationAction.CREATE_WORK_ORDER) {
            createWorkOrder(event, rule, userId);
        }
        return eventRepository.save(event);
    }

    private boolean shouldCreateEvent(MaintenanceDueStatus status) {
        return status == MaintenanceDueStatus.UPCOMING
                || status == MaintenanceDueStatus.DUE
                || status == MaintenanceDueStatus.OVERDUE
                || status == MaintenanceDueStatus.BLOCKED;
    }

    private MaintenanceDueEventStatus initialStatus(EquipmentMaintenanceEffectiveRule rule, MaintenanceDueStatus dueStatus) {
        if (dueStatus == MaintenanceDueStatus.BLOCKED) {
            return MaintenanceDueEventStatus.DETECTED;
        }
        return rule.automationAction() == AutomationAction.REQUIRE_APPROVAL || rule.requiresApproval()
                ? MaintenanceDueEventStatus.AWAITING_APPROVAL
                : MaintenanceDueEventStatus.DETECTED;
    }

    private boolean isDuplicateSuppressed(EquipmentMaintenanceEffectiveRule rule, MaintenanceDueEvent event) {
        if (event.getCreatedTaskId() != null || event.getCreatedWorkOrderId() != null) {
            return false;
        }
        if (rule.duplicatePolicy() == DuplicatePolicy.ONE_ITEM_PER_CYCLE) {
            return pprTaskRepository.existsOpenByCycleKey(event.getCycleKey())
                    || workOrderRepository.existsOpenByCycleKey(event.getCycleKey());
        }
        if (rule.duplicatePolicy() == DuplicatePolicy.ONE_OPEN_ITEM_PER_RULE) {
            return eventRepository.findOpenByScope(
                            event.getEquipmentId(),
                            event.getRegulationId(),
                            event.getEquipmentMaintenanceRuleId(),
                            MaintenanceDueEventService.openStatuses())
                    .stream()
                    .anyMatch(existing -> event.getId() == null || !event.getId().equals(existing.getId()));
        }
        return false;
    }

    private PprTask createTask(MaintenanceDueEvent event, EquipmentMaintenanceEffectiveRule rule, UUID userId) {
        if (isBlocked(event)) {
            event.setStatus(MaintenanceDueEventStatus.DETECTED);
            return null;
        }
        if (event.getCreatedTaskId() != null) {
            return pprTaskRepository.findByIdAndIsDeletedFalse(event.getCreatedTaskId()).orElse(null);
        }
        if (rule.templateId() == null) {
            event.setStatus(MaintenanceDueEventStatus.DETECTED);
            event.setExplanation(append(event.getExplanation(), "templateId is required to create task"));
            return null;
        }
        if (pprTaskRepository.existsOpenByCycleKey(event.getCycleKey())) {
            event.setStatus(MaintenanceDueEventStatus.SUPPRESSED_DUPLICATE);
            return null;
        }
        Equipment equipment = equipment(event);
        UUID departmentId = effectiveDepartmentId(equipment, rule);
        PprPlan plan = autoPlan(departmentId, userId);
        PprTask task = new PprTask();
        task.setCode(nextTaskCode());
        task.setPlan(plan);
        task.setRegulationId(rule.regulationId());
        task.setEquipmentMaintenanceRuleId(rule.equipmentMaintenanceRuleId());
        task.setEquipmentId(equipment.getId());
        task.setMaintenanceDueEventId(event.getId());
        task.setCycleKey(event.getCycleKey());
        task.setTitle(rule.name() + " - " + equipment.getCode());
        LocalDateTime start = plannedStart(event);
        task.setScheduledStart(start);
        task.setScheduledEnd(start.plusHours(Math.max(1, (long) Math.ceil(rule.normativeLaborHours()))));
        task.setDueDate(dueDateTime(event));
        task.setPriority(rule.defaultPriority() == null ? PriorityLevel.MEDIUM : rule.defaultPriority());
        task.setPlannedLaborHours(rule.normativeLaborHours());
        task.setStatus(PprTaskStatus.PLANNED);
        PprTask saved = pprTaskRepository.save(task);
        event.setCreatedTaskId(saved.getId());
        event.setStatus(MaintenanceDueEventStatus.TASK_CREATED);
        return saved;
    }

    private WorkOrderDto createWorkOrder(MaintenanceDueEvent event, EquipmentMaintenanceEffectiveRule rule, UUID userId) {
        if (isBlocked(event)) {
            event.setStatus(MaintenanceDueEventStatus.DETECTED);
            return null;
        }
        if (event.getCreatedWorkOrderId() != null) {
            return null;
        }
        if (workOrderRepository.existsOpenByCycleKey(event.getCycleKey())) {
            event.setStatus(MaintenanceDueEventStatus.SUPPRESSED_DUPLICATE);
            return null;
        }
        Equipment equipment = equipment(event);
        UUID departmentId = effectiveDepartmentId(equipment, rule);
        if (departmentId == null) {
            event.setExplanation(append(event.getExplanation(), "departmentId is required to create work order"));
            return null;
        }
        WorkOrderRequest request = new WorkOrderRequest(
                nextWorkOrderNumber(),
                rule.name() + " - " + equipment.getCode(),
                equipment.getId(),
                null,
                departmentId,
                null,
                null,
                event.getCreatedTaskId(),
                null,
                workOrderType(rule),
                workType(rule),
                null,
                null,
                rule.defaultPriority() == null ? PriorityLevel.MEDIUM : rule.defaultPriority(),
                plannedStart(event).atZone(ZoneId.systemDefault()).toInstant(),
                dueDateTime(event).atZone(ZoneId.systemDefault()).toInstant(),
                effectiveUserId(userId),
                "Generated from maintenance due event " + event.getId(),
                event.getId(),
                event.getCycleKey()
        );
        WorkOrderDto dto = workOrderService.create(request);
        event.setCreatedWorkOrderId(dto.id());
        event.setStatus(MaintenanceDueEventStatus.WORK_ORDER_CREATED);
        return dto;
    }

    private PprPlan autoPlan(UUID departmentId, UUID userId) {
        YearMonth month = YearMonth.now();
        String depPart = departmentId == null ? "enterprise" : departmentId.toString();
        String code = "AUTO-MAINT-%s-%s".formatted(month, depPart);
        return pprPlanRepository.findByCodeAndIsDeletedFalse(code).orElseGet(() -> {
            PprPlan plan = new PprPlan();
            plan.setCode(code);
            plan.setName("Automatic maintenance " + month);
            plan.setStartDate(month.atDay(1));
            plan.setEndDate(month.atEndOfMonth());
            plan.setStatus(PlanStatus.GENERATED);
            plan.setDepartmentId(departmentId);
            plan.setCreatedById(effectiveUserId(userId));
            plan.setPprType(PprType.PREVENTIVE_MAINTENANCE);
            plan.setScheduleType(PprScheduleType.ONE_TIME);
            return pprPlanRepository.save(plan);
        });
    }

    private UUID effectiveDepartmentId(Equipment equipment, EquipmentMaintenanceEffectiveRule rule) {
        if (rule.defaultDepartmentId() != null) {
            return rule.defaultDepartmentId();
        }
        return equipment.getResponsibleDepartmentId() != null ? equipment.getResponsibleDepartmentId() : equipment.getDepartmentId();
    }

    private Equipment equipment(MaintenanceDueEvent event) {
        return equipmentRepository.findByIdAndIsDeletedFalse(event.getEquipmentId())
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + event.getEquipmentId()));
    }

    private EquipmentMaintenanceEffectiveRule effectiveRule(MaintenanceDueEvent event) {
        return effectiveRuleResolver.resolveApplicable(event.getEquipmentId())
                .stream()
                .filter(rule -> Objects.equals(rule.regulationId(), event.getRegulationId())
                        && Objects.equals(rule.equipmentMaintenanceRuleId(), event.getEquipmentMaintenanceRuleId()))
                .findFirst()
                .orElseGet(() -> EquipmentMaintenanceEffectiveRule.fromRegulation(event.getEquipmentId(), regulation(event)));
    }

    private MaintenanceRegulation regulation(MaintenanceDueEvent event) {
        return regulationRepository.findByIdAndIsDeletedFalse(event.getRegulationId())
                .orElseThrow(() -> RestException.notFound("Maintenance regulation not found: " + event.getRegulationId()));
    }

    private UUID effectiveUserId(UUID userId) {
        if (userId != null) {
            return userId;
        }
        return userRepository.findByUsernameAndIsDeletedFalse("admin")
                .map(com.toir.entity.users.User::getId)
                .orElse(SYSTEM_USER_ID);
    }

    private LocalDateTime plannedStart(MaintenanceDueEvent event) {
        Instant dueAt = event.getDueAt() == null ? Instant.now() : event.getDueAt();
        return LocalDateTime.ofInstant(dueAt, ZoneId.systemDefault()).with(LocalTime.of(9, 0));
    }

    private LocalDateTime dueDateTime(MaintenanceDueEvent event) {
        Instant dueAt = event.getDueAt() == null ? Instant.now() : event.getDueAt();
        return LocalDateTime.ofInstant(dueAt, ZoneId.systemDefault()).with(LocalTime.of(18, 0));
    }

    private String cycleKey(UUID equipmentId, EquipmentMaintenanceEffectiveRule rule, MaintenanceDueCalculationDto due) {
        String scope = cycleScope(equipmentId, rule);
        if (due.status() == MaintenanceDueStatus.BLOCKED) {
            String explanation = due.explanation() == null ? "" : due.explanation().toLowerCase(Locale.ROOT);
            if (explanation.contains("calendar")) {
                return scope + ":CALENDAR:BLOCKED:NO_ANCHOR";
            }
            if (due.meterType() != null) {
                return "%s:METER:%s:BLOCKED:MISSING_METER".formatted(scope, due.meterType().name());
            }
            return scope + ":CALENDAR:BLOCKED:UNKNOWN";
        }
        if (due.dueByMeter()
                && due.meterType() != null
                && due.meterInterval() != null
                && due.meterInterval() > 0
                && due.meterCurrentValue() != null) {
            double cycleBucket = Math.floor(due.meterCurrentValue() / due.meterInterval()) * due.meterInterval();
            return "%s:METER:%s:%s".formatted(scope, due.meterType().name(), formatCycleBucket(cycleBucket));
        }
        if (due.nextDueAt() != null) {
            LocalDate dueDate = due.nextDueAt().atZone(ZoneOffset.UTC).toLocalDate();
            return "%s:CALENDAR:%s".formatted(scope, dueDate);
        }
        return scope + ":CALENDAR:BLOCKED:NO_ANCHOR";
    }

    private String cycleScope(UUID equipmentId, EquipmentMaintenanceEffectiveRule rule) {
        if (rule.equipmentMaintenanceRuleId() != null) {
            return "%s:RULE:%s".formatted(equipmentId, rule.equipmentMaintenanceRuleId());
        }
        return "%s:REG:%s".formatted(equipmentId, rule.regulationId());
    }

    private String formatCycleBucket(double value) {
        if (!Double.isFinite(value)) {
            return Double.toString(value);
        }
        if (Math.abs(value) == 0.0) {
            return "0";
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private boolean isBlocked(MaintenanceDueEvent event) {
        return event.getDueStatus() == MaintenanceDueStatus.BLOCKED;
    }

    private MaintenanceDueEventDto blockedEventDto(MaintenanceDueEvent event) {
        if (event.getStatus() == MaintenanceDueEventStatus.AWAITING_APPROVAL) {
            event.setStatus(MaintenanceDueEventStatus.DETECTED);
            return eventService.toDto(eventRepository.save(event));
        }
        return eventService.toDto(event);
    }

    private WorkOrderType workOrderType(EquipmentMaintenanceEffectiveRule rule) {
        return isInspectionOrDiagnostic(rule.maintenanceKind()) ? WorkOrderType.INSPECTION : WorkOrderType.PLANNED;
    }

    private WorkType workType(EquipmentMaintenanceEffectiveRule rule) {
        return isInspectionOrDiagnostic(rule.maintenanceKind()) ? WorkType.DIAGNOSTICS : WorkType.REPAIR;
    }

    private boolean isInspectionOrDiagnostic(MaintenanceKind kind) {
        return kind == MaintenanceKind.INSPECTION || kind == MaintenanceKind.DIAGNOSTIC;
    }

    private String nextTaskCode() {
        int year = Year.now().getValue();
        String prefix = "AT-" + year + "-";
        long seq = pprTaskRepository.maxSequenceByCodePrefix(prefix) + 1;
        return "%s%04d".formatted(prefix, seq);
    }

    private String nextWorkOrderNumber() {
        int year = Year.now().getValue();
        String prefix = "WO-AUTO-" + year + "-";
        for (long seq = workOrderRepository.countByIsDeletedFalse() + 1; seq < workOrderRepository.countByIsDeletedFalse() + 5000; seq++) {
            String number = "%s%04d".formatted(prefix, seq);
            if (!workOrderRepository.existsByNumberAndIsDeletedFalse(number)) {
                return number;
            }
        }
        throw RestException.conflict("Could not generate automatic work order number");
    }

    private String append(String base, String addition) {
        if (base == null || base.isBlank()) {
            return addition;
        }
        return base + "; " + addition;
    }

    public record EvaluationResult(
            int checkedEquipment,
            int events,
            int suppressed,
            int tasksCreated,
            int workOrdersCreated
    ) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "checked=%d events=%d suppressed=%d tasks=%d workOrders=%d",
                    checkedEquipment, events, suppressed, tasksCreated, workOrdersCreated);
        }
    }
}
