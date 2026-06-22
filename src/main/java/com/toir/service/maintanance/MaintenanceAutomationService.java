package com.toir.service.maintanance;

import com.toir.dto.maintenanceplanning.MaintenanceDueCalculationDto;
import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.dto.maintenancedue.MaintenanceDueEventDto;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.AutomationAction;
import com.toir.enums.ApprovalResultAction;
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
import com.toir.security.SecurityAccessService;
import com.toir.service.WorkOrderNumberService;
import com.toir.service.WorkOrderService;
import com.toir.service.ApprovalService;
import com.toir.service.equipment.OperationalEquipmentPolicy;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private final WorkOrderNumberService workOrderNumberService;
    private final UserRepository userRepository;
    private final EquipmentMaintenanceEffectiveRuleResolver effectiveRuleResolver;
    private final SecurityAccessService securityAccessService;
    private final MaintenanceAutomationNotificationService notificationService;
    private final ObjectProvider<ApprovalService> approvalServiceProvider;
    private final OperationalEquipmentPolicy operationalEquipmentPolicy;

    @Transactional
    public EvaluationResult evaluateEquipment(UUID equipmentId, MaintenanceTriggerSource source) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        if (!isOperational(equipment)) {
            return new EvaluationResult(1, 0, 0, 0, 0);
        }
        List<EquipmentMaintenanceEffectiveRule> rules = effectiveRuleResolver.resolveApplicable(equipmentId);
        int createdOrUpdated = 0;
        int blocked = 0;
        int suppressed = 0;
        int tasks = 0;
        int workOrders = 0;
        int rulesChecked = rules.size();
        int eventsCreated = 0;
        int eventsUpdated = 0;
        int notifications = 0;
        int failures = 0;
        for (EquipmentMaintenanceEffectiveRule rule : rules) {
            try {
                EvaluationOutcome outcome = evaluate(equipment, rule, source, null);
                MaintenanceDueEvent event = outcome.event();
                if (event == null) {
                    continue;
                }
                createdOrUpdated++;
                if (outcome.created()) {
                    eventsCreated++;
                } else {
                    eventsUpdated++;
                }
                notifications += outcome.notificationsCreated();
                if (event.getDueStatus() == MaintenanceDueStatus.BLOCKED) {
                    blocked++;
                }
                if (event.getStatus() == MaintenanceDueEventStatus.SUPPRESSED_DUPLICATE) {
                    suppressed++;
                }
                tasks += outcome.tasksCreated();
                workOrders += outcome.workOrdersCreated();
            } catch (RuntimeException ex) {
                failures++;
                log.warn("Maintenance automation failed equipmentId={} regulationId={} ruleId={}",
                        equipmentId, rule.regulationId(), rule.equipmentMaintenanceRuleId(), ex);
            }
        }
        log.info("maintenance_automation_evaluation equipmentId={} source={} rulesChecked={} events={} eventsCreated={} eventsUpdated={} blocked={} suppressed={} tasks={} workOrders={} notifications={} failures={}",
                equipmentId, source, rulesChecked, createdOrUpdated, eventsCreated, eventsUpdated, blocked, suppressed, tasks, workOrders, notifications, failures);
        return new EvaluationResult(1, createdOrUpdated, blocked, suppressed, tasks, workOrders,
                rulesChecked, eventsCreated, eventsUpdated, notifications, failures);
    }

    @Transactional
    public EvaluationResult evaluateRegulation(UUID regulationId, MaintenanceTriggerSource source) {
        MaintenanceRegulation regulation = regulationRepository.findByIdAndIsDeletedFalse(regulationId)
                .orElseThrow(() -> RestException.notFound("Maintenance regulation not found: " + regulationId));
        List<Equipment> equipment = equipmentRepository.findAllForMaintenanceRegulations(regulation.getEquipmentTypeId());
        int events = 0;
        int blocked = 0;
        int suppressed = 0;
        int tasks = 0;
        int workOrders = 0;
        int rulesChecked = 0;
        int eventsCreated = 0;
        int eventsUpdated = 0;
        int notifications = 0;
        int failures = 0;
        for (Equipment item : equipment) {
            if (!isOperational(item)) {
                continue;
            }
            try {
                List<EquipmentMaintenanceEffectiveRule> rules = effectiveRuleResolver.resolveApplicable(item.getId())
                        .stream()
                        .filter(rule -> Objects.equals(rule.regulationId(), regulationId))
                        .toList();
                rulesChecked += rules.size();
                for (EquipmentMaintenanceEffectiveRule rule : rules) {
                    EvaluationOutcome outcome = evaluate(item, rule, source, null);
                    MaintenanceDueEvent event = outcome.event();
                    if (event == null) {
                        continue;
                    }
                    events++;
                    if (outcome.created()) {
                        eventsCreated++;
                    } else {
                        eventsUpdated++;
                    }
                    notifications += outcome.notificationsCreated();
                    if (event.getDueStatus() == MaintenanceDueStatus.BLOCKED) {
                        blocked++;
                    }
                    if (event.getStatus() == MaintenanceDueEventStatus.SUPPRESSED_DUPLICATE) {
                        suppressed++;
                    }
                    tasks += outcome.tasksCreated();
                    workOrders += outcome.workOrdersCreated();
                }
            } catch (RuntimeException ex) {
                failures++;
                log.warn("Maintenance automation failed equipmentId={} regulationId={}", item.getId(), regulationId, ex);
            }
        }
        return new EvaluationResult(equipment.size(), events, blocked, suppressed, tasks, workOrders,
                rulesChecked, eventsCreated, eventsUpdated, notifications, failures);
    }

    @Transactional
    public EvaluationResult evaluateAllCalendarRules() {
        int checked = 0;
        int events = 0;
        int blocked = 0;
        int suppressed = 0;
        int tasks = 0;
        int workOrders = 0;
        int rulesChecked = 0;
        int eventsCreated = 0;
        int eventsUpdated = 0;
        int notifications = 0;
        int failures = 0;
        List<Equipment> equipment = equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        for (Equipment item : equipment) {
            if (!isOperational(item)) {
                continue;
            }
            try {
                EvaluationResult result = evaluateEquipment(item.getId(), MaintenanceTriggerSource.CALENDAR_JOB);
                checked += result.checkedEquipment();
                events += result.events();
                blocked += result.blockedEvents();
                suppressed += result.suppressed();
                tasks += result.tasksCreated();
                workOrders += result.workOrdersCreated();
                rulesChecked += result.rulesChecked();
                eventsCreated += result.eventsCreated();
                eventsUpdated += result.eventsUpdated();
                notifications += result.notificationsCreated();
                failures += result.failures();
            } catch (RuntimeException ex) {
                failures++;
                log.warn("maintenance_automation_equipment_batch_failed equipmentId={} source={}",
                        item.getId(), MaintenanceTriggerSource.CALENDAR_JOB, ex);
            }
        }
        log.info("maintenance_automation_nightly_summary equipmentChecked={} rulesChecked={} events={} eventsCreated={} eventsUpdated={} blocked={} suppressed={} tasks={} workOrders={} notifications={} failures={}",
                checked, rulesChecked, events, eventsCreated, eventsUpdated, blocked, suppressed, tasks, workOrders, notifications, failures);
        return new EvaluationResult(checked, events, blocked, suppressed, tasks, workOrders,
                rulesChecked, eventsCreated, eventsUpdated, notifications, failures);
    }

    @Transactional
    public ApprovalRequestDto approveDueEvent(UUID eventId, UUID userId) {
        return approveDueEvent(eventId, userId, null);
    }

    @Transactional
    public ApprovalRequestDto approveDueEvent(UUID eventId, UUID userId, ApprovalActionType requestedActionType) {
        MaintenanceDueEvent event = eventService.getOrThrow(eventId);
        eventService.assertCanAccessEvent(event);
        if (isBlocked(event)) {
            blockedEventDto(event);
            throw RestException.badRequest("Blocked maintenance due events cannot be approved");
        }
        if (event.getStatus() != MaintenanceDueEventStatus.AWAITING_APPROVAL
                && event.getStatus() != MaintenanceDueEventStatus.DETECTED) {
            throw RestException.conflict("Maintenance due event is already processed: " + event.getStatus());
        }
        EquipmentMaintenanceEffectiveRule rule = effectiveRule(event);
        enforceApprovalAuthority(rule);
        Equipment equipment = equipment(event);
        event.setStatus(MaintenanceDueEventStatus.AWAITING_APPROVAL);
        eventRepository.save(event);
        return createOrReuseApprovalRequest(event, rule, equipment, userId, requestedActionType);
    }

    @Transactional
    public MaintenanceDueEventDto createWorkOrderFromEvent(UUID eventId, UUID userId) {
        MaintenanceDueEvent event = eventService.getOrThrow(eventId);
        eventService.assertCanAccessEvent(event);
        assertCanCreateWorkOrderForDueStatus(event);
        EquipmentMaintenanceEffectiveRule rule = effectiveRule(event);
        WorkOrderDto workOrder = createWorkOrder(event, rule, userId);
        if (workOrder != null) {
            notificationService.notifyWorkOrderCreated(event, rule, equipment(event));
        }
        return eventService.toDto(eventRepository.save(event));
    }

    private EvaluationOutcome evaluate(Equipment equipment,
                                       EquipmentMaintenanceEffectiveRule rule,
                                       MaintenanceTriggerSource source,
                                       UUID userId) {
        if (!rule.applicable() || !rule.active()) {
            return EvaluationOutcome.none();
        }
        if (rule.triggerPolicy() != null && rule.triggerPolicy().name().equals("MANUAL")) {
            return EvaluationOutcome.none();
        }
        if (source == MaintenanceTriggerSource.METER_READING && !rule.hasMeterTrigger()) {
            return EvaluationOutcome.none();
        }
        MaintenanceDueCalculationDto due = dueCalculationService.calculate(rule);
        if (!shouldCreateEvent(due.status())) {
            return EvaluationOutcome.none();
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
            return new EvaluationOutcome(eventService.saveEvent(event, equipment), isNew, 0, 0, 0);
        }
        event = eventService.saveEvent(event, equipment);
        int notifications = notificationService.notifyEventStatus(event, rule, equipment);
        if (event.getStatus() == MaintenanceDueEventStatus.AWAITING_APPROVAL) {
            notifications += notificationService.notifyRequiresApproval(event, rule, equipment);
            createOrReuseApprovalRequest(event, rule, equipment, userId);
        }
        if (!canCreateDownstream(due.status())
                || rule.automationAction() == AutomationAction.TRACK_ONLY
                || rule.automationAction() == AutomationAction.REQUIRE_APPROVAL) {
            return new EvaluationOutcome(event, isNew, notifications, 0, 0);
        }
        boolean alreadyHadTask = event.getCreatedTaskId() != null;
        boolean alreadyHadWorkOrder = event.getCreatedWorkOrderId() != null;
        int tasksCreated = 0;
        int workOrdersCreated = 0;
        if (rule.automationAction() == AutomationAction.CREATE_TASK) {
            PprTask task = createTask(event, rule, userId);
            if (task != null && !alreadyHadTask) {
                tasksCreated = 1;
            }
        } else if (rule.automationAction() == AutomationAction.CREATE_WORK_ORDER) {
            WorkOrderDto workOrder = createWorkOrder(event, rule, userId);
            if (workOrder != null) {
                if (!alreadyHadWorkOrder) {
                    workOrdersCreated = 1;
                }
                notifications += notificationService.notifyWorkOrderCreated(event, rule, equipment);
            }
        }
        return new EvaluationOutcome(eventRepository.save(event), isNew, notifications, tasksCreated, workOrdersCreated);
    }

    private boolean shouldCreateEvent(MaintenanceDueStatus status) {
        return status == MaintenanceDueStatus.UPCOMING
                || status == MaintenanceDueStatus.DUE
                || status == MaintenanceDueStatus.OVERDUE
                || status == MaintenanceDueStatus.BLOCKED;
    }

    private boolean canCreateDownstream(MaintenanceDueStatus status) {
        return status == MaintenanceDueStatus.DUE || status == MaintenanceDueStatus.OVERDUE;
    }

    private MaintenanceDueEventStatus initialStatus(EquipmentMaintenanceEffectiveRule rule, MaintenanceDueStatus dueStatus) {
        if (dueStatus == MaintenanceDueStatus.BLOCKED) {
            return MaintenanceDueEventStatus.DETECTED;
        }
        if (dueStatus == MaintenanceDueStatus.UPCOMING) {
            return MaintenanceDueEventStatus.DETECTED;
        }
        return rule.automationAction() == AutomationAction.REQUIRE_APPROVAL
                ? MaintenanceDueEventStatus.AWAITING_APPROVAL
                : MaintenanceDueEventStatus.DETECTED;
    }

    private ApprovalResultAction approvalResultAction(EquipmentMaintenanceEffectiveRule rule) {
        if (rule.automationAction() == AutomationAction.REQUIRE_APPROVAL) {
            return rule.approvalResultAction() == null
                    ? ApprovalResultAction.CREATE_TASK
                    : rule.approvalResultAction();
        }
        return rule.automationAction() == AutomationAction.CREATE_WORK_ORDER
                ? ApprovalResultAction.CREATE_WORK_ORDER
                : ApprovalResultAction.CREATE_TASK;
    }

    @Transactional
    public String finalizeDueEventApproval(UUID eventId, ApprovalActionType actionType, UUID userId) {
        MaintenanceDueEvent event = eventService.getOrThrow(eventId);
        if (event.getStatus() != MaintenanceDueEventStatus.AWAITING_APPROVAL
                && event.getStatus() != MaintenanceDueEventStatus.DETECTED) {
            throw RestException.conflict("Maintenance due event is already processed: " + event.getStatus());
        }
        if (event.getCreatedTaskId() != null || event.getCreatedWorkOrderId() != null) {
            throw RestException.conflict("Maintenance due event already has a downstream item");
        }
        if (isBlocked(event)) {
            event.setStatus(MaintenanceDueEventStatus.DETECTED);
            eventRepository.save(event);
            throw RestException.badRequest("Blocked maintenance due events cannot be finalized");
        }
        EquipmentMaintenanceEffectiveRule rule = effectiveRule(event);
        Equipment equipment = equipment(event);
        if (actionType == ApprovalActionType.CREATE_WORK_ORDER) {
            WorkOrderDto workOrder = createWorkOrder(event, rule, userId);
            if (workOrder != null) {
                notificationService.notifyWorkOrderCreated(event, rule, equipment);
            }
            eventRepository.save(event);
            return "{\"status\":\"WORK_ORDER_CREATED\"}";
        }
        PprTask task = createTask(event, rule, userId);
        eventRepository.save(event);
        return task == null ? "{\"status\":\"SUPPRESSED_DUPLICATE\"}" : "{\"status\":\"TASK_CREATED\"}";
    }

    @Transactional
    public String rejectDueEventApproval(UUID eventId, String reason) {
        MaintenanceDueEvent event = eventService.getOrThrow(eventId);
        if (event.getCreatedTaskId() != null || event.getCreatedWorkOrderId() != null) {
            throw RestException.conflict("Maintenance due event already has a downstream item");
        }
        event.setStatus(MaintenanceDueEventStatus.CANCELLED);
        event.setResolvedAt(Instant.now());
        event.setResolutionReason(StringUtils.hasText(reason) ? reason.trim() : "Approval rejected");
        eventRepository.save(event);
        return "{\"status\":\"CANCELLED\"}";
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
        assertCanCreateWorkOrderForDueStatus(event);
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
        UUID pprTaskId = validApprovedPprTaskId(event.getCreatedTaskId());
        WorkOrderRequest request = new WorkOrderRequest(
                workOrderNumberService.nextAutoNumber(),
                rule.name() + " - " + equipment.getCode(),
                equipment.getId(),
                null,
                departmentId,
                null,
                null,
                pprTaskId,
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
        copyTemplateOperationsToWorkOrder(event, rule, dto.id());
        event.setStatus(MaintenanceDueEventStatus.WORK_ORDER_CREATED);
        return dto;
    }

    private void copyTemplateOperationsToWorkOrder(MaintenanceDueEvent event,
                                                   EquipmentMaintenanceEffectiveRule rule,
                                                   UUID workOrderId) {
        if (rule.templateId() == null || workOrderId == null) {
            return;
        }
        WorkOrderService.TemplateTaskSyncResult result = workOrderService.syncTemplateTasks(workOrderId, rule.templateId());
        if (result.operationsCount() == 0) {
            event.setExplanation(append(event.getExplanation(),
                    "template has no operations/checklist items"));
        }
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
        if (equipment.getDepartmentId() != null) {
            return equipment.getDepartmentId();
        }
        if (equipment.getResponsibleDepartmentId() != null) {
            return equipment.getResponsibleDepartmentId();
        }
        return rule.defaultDepartmentId();
    }

    private UUID validApprovedPprTaskId(UUID taskId) {
        if (taskId == null) {
            return null;
        }
        return pprTaskRepository.findByIdAndIsDeletedFalseWithPlan(taskId)
                .filter(task -> task.getStatus() == PprTaskStatus.APPROVED)
                .filter(task -> task.getPlan() != null)
                .filter(task -> task.getPlan().getStatus() == PlanStatus.APPROVED
                        || task.getPlan().getStatus() == PlanStatus.IN_PROGRESS)
                .map(PprTask::getId)
                .orElse(null);
    }

    private void assertCanCreateWorkOrderForDueStatus(MaintenanceDueEvent event) {
        if (event.getDueStatus() == MaintenanceDueStatus.DUE || event.getDueStatus() == MaintenanceDueStatus.OVERDUE) {
            return;
        }
        if (event.getDueStatus() == MaintenanceDueStatus.BLOCKED) {
            event.setStatus(MaintenanceDueEventStatus.DETECTED);
        }
        throw RestException.badRequest("Work order can be created only for DUE or OVERDUE maintenance due events");
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

    private void enforceApprovalAuthority(EquipmentMaintenanceEffectiveRule rule) {
        requireApprovalAuthority(rule.approvalPermission());
        requireApprovalAuthority(rule.approvalRole());
    }

    private void requireApprovalAuthority(String permissionOrRole) {
        if (!StringUtils.hasText(permissionOrRole)) {
            return;
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!securityAccessService.hasPermission(authentication, permissionOrRole)) {
            throw new AccessDeniedException("Missing approval permission: " + permissionOrRole);
        }
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
        if (due.status() == MaintenanceDueStatus.BLOCKED) {
            String explanation = due.explanation() == null ? "" : due.explanation().toLowerCase(Locale.ROOT);
            if (explanation.contains("calendar")) {
                return calendarCycleScope(equipmentId, rule) + ":CALENDAR:BLOCKED:" + calendarBlockedReason(explanation);
            }
            if (due.meterType() != null) {
                String scope = cycleScope(equipmentId, rule);
                return "%s:METER:%s:BLOCKED:MISSING_METER".formatted(scope, due.meterType().name());
            }
            return calendarCycleScope(equipmentId, rule) + ":CALENDAR:BLOCKED:UNKNOWN";
        }
        if (due.dueByMeter()
                && due.meterType() != null
                && due.meterInterval() != null
                && due.meterInterval() > 0
                && due.meterCurrentValue() != null) {
            String scope = cycleScope(equipmentId, rule);
            double cycleBucket = due.nextMeterDueValue() != null
                    ? due.nextMeterDueValue()
                    : Math.floor(due.meterCurrentValue() / due.meterInterval()) * due.meterInterval();
            return "%s:METER:%s:%s".formatted(scope, due.meterType().name(), formatCycleBucket(cycleBucket));
        }
        if (due.nextDueAt() != null) {
            LocalDate dueDate = due.nextDueAt().atZone(ZoneOffset.UTC).toLocalDate();
            return "%s:CALENDAR:%s".formatted(calendarCycleScope(equipmentId, rule), dueDate);
        }
        return calendarCycleScope(equipmentId, rule) + ":CALENDAR:BLOCKED:NO_ANCHOR";
    }

    private String cycleScope(UUID equipmentId, EquipmentMaintenanceEffectiveRule rule) {
        if (rule.equipmentMaintenanceRuleId() != null) {
            return "%s:RULE:%s".formatted(equipmentId, rule.equipmentMaintenanceRuleId());
        }
        return "%s:REG:%s".formatted(equipmentId, rule.regulationId());
    }

    private String calendarCycleScope(UUID equipmentId, EquipmentMaintenanceEffectiveRule rule) {
        UUID scopeId = rule.equipmentMaintenanceRuleId() != null
                ? rule.equipmentMaintenanceRuleId()
                : rule.regulationId();
        return "%s:%s".formatted(equipmentId, scopeId);
    }

    private String calendarBlockedReason(String explanation) {
        if (explanation.contains("initial completion anchor")) {
            return "INITIAL_ANCHOR_REQUIRED";
        }
        if (explanation.contains("no completion anchor") || explanation.contains("no anchor")) {
            return "NO_ANCHOR";
        }
        return "UNKNOWN";
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

    private String append(String base, String addition) {
        if (base == null || base.isBlank()) {
            return addition;
        }
        return base + "; " + addition;
    }

    private ApprovalRequestDto createOrReuseApprovalRequest(MaintenanceDueEvent event,
                                                            EquipmentMaintenanceEffectiveRule rule,
                                                            Equipment equipment,
                                                            UUID requesterId) {
        return createOrReuseApprovalRequest(event, rule, equipment, requesterId, null);
    }

    private ApprovalRequestDto createOrReuseApprovalRequest(MaintenanceDueEvent event,
                                                            EquipmentMaintenanceEffectiveRule rule,
                                                            Equipment equipment,
                                                            UUID requesterId,
                                                            ApprovalActionType requestedActionType) {
        ApprovalActionType actionType = requestedActionType == ApprovalActionType.CREATE_WORK_ORDER
                || requestedActionType == ApprovalActionType.CREATE_TASK
                ? requestedActionType
                : approvalResultAction(rule) == ApprovalResultAction.CREATE_WORK_ORDER
                ? ApprovalActionType.CREATE_WORK_ORDER
                : ApprovalActionType.CREATE_TASK;
        UUID effectiveRequesterId = requesterId == null ? effectiveUserId(null) : requesterId;
        UUID approverId = effectiveApproverId(rule, equipment, effectiveRequesterId);
        return approvalServiceProvider.getObject().createOrReuseSystemApprovalForDocument(
                "MAINTENANCE_DUE_EVENT",
                event.getId(),
                actionType,
                effectiveRequesterId,
                approverId,
                StringUtils.hasText(rule.approvalRole()) ? rule.approvalRole().trim() : null,
                "Maintenance due event approval: " + event.getCycleKey(),
                "Approval request for maintenance due event " + event.getCycleKey()
        );
    }

    private UUID effectiveApproverId(EquipmentMaintenanceEffectiveRule rule, Equipment equipment, UUID fallbackUserId) {
        if (rule.defaultResponsibleId() != null) {
            return rule.defaultResponsibleId();
        }
        if (equipment.getResponsibleId() != null) {
            return equipment.getResponsibleId();
        }
        return fallbackUserId;
    }

    private record EvaluationOutcome(
            MaintenanceDueEvent event,
            boolean created,
            int notificationsCreated,
            int tasksCreated,
            int workOrdersCreated
    ) {
        private static EvaluationOutcome none() {
            return new EvaluationOutcome(null, false, 0, 0, 0);
        }
    }

    private boolean isOperational(Equipment equipment) {
        return operationalEquipmentPolicy == null
                ? equipment != null && equipment.getStatus() != EquipmentStatus.DECOMMISSIONED
                : operationalEquipmentPolicy.isOperational(equipment);
    }

    public record EvaluationResult(
            int checkedEquipment,
            int events,
            int blockedEvents,
            int suppressed,
            int tasksCreated,
            int workOrdersCreated,
            int rulesChecked,
            int eventsCreated,
            int eventsUpdated,
            int notificationsCreated,
            int failures
    ) {
        public EvaluationResult(int checkedEquipment,
                                int events,
                                int blockedEvents,
                                int suppressed,
                                int tasksCreated,
                                int workOrdersCreated) {
            this(checkedEquipment, events, blockedEvents, suppressed, tasksCreated, workOrdersCreated,
                    0, 0, 0, 0, 0);
        }

        public EvaluationResult(int checkedEquipment,
                                int events,
                                int suppressed,
                                int tasksCreated,
                                int workOrdersCreated) {
            this(checkedEquipment, events, 0, suppressed, tasksCreated, workOrdersCreated);
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "checked=%d rules=%d events=%d created=%d updated=%d blocked=%d suppressed=%d tasks=%d workOrders=%d notifications=%d failures=%d",
                    checkedEquipment, rulesChecked, events, eventsCreated, eventsUpdated, blockedEvents, suppressed,
                    tasksCreated, workOrdersCreated, notificationsCreated, failures);
        }
    }
}
