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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
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

    @Transactional
    public EvaluationResult evaluateEquipment(UUID equipmentId, MaintenanceTriggerSource source) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        if (equipment.getStatus() == EquipmentStatus.DECOMMISSIONED) {
            return new EvaluationResult(1, 0, 0, 0, 0);
        }
        List<MaintenanceRegulation> regulations = regulationRepository
                .findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(equipment.getEquipmentTypeId());
        int createdOrUpdated = 0;
        int suppressed = 0;
        int tasks = 0;
        int workOrders = 0;
        for (MaintenanceRegulation regulation : regulations) {
            try {
                MaintenanceDueEvent event = evaluate(equipment, regulation, source, null);
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
                log.warn("Maintenance automation failed equipmentId={} regulationId={}", equipmentId, regulation.getId(), ex);
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
                MaintenanceDueEvent event = evaluate(item, regulation, source, null);
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
        if (event.getStatus() != MaintenanceDueEventStatus.AWAITING_APPROVAL
                && event.getStatus() != MaintenanceDueEventStatus.DETECTED) {
            return eventService.toDto(event);
        }
        MaintenanceRegulation regulation = regulation(event);
        if (regulation.getAutomationAction() == AutomationAction.CREATE_TASK
                || regulation.getAutomationAction() == AutomationAction.REQUIRE_APPROVAL) {
            createTask(event, regulation, userId);
        } else {
            createWorkOrder(event, regulation, userId);
        }
        return eventService.toDto(eventRepository.save(event));
    }

    @Transactional
    public MaintenanceDueEventDto createWorkOrderFromEvent(UUID eventId, UUID userId) {
        MaintenanceDueEvent event = eventService.getOrThrow(eventId);
        MaintenanceRegulation regulation = regulation(event);
        createWorkOrder(event, regulation, userId);
        return eventService.toDto(eventRepository.save(event));
    }

    private MaintenanceDueEvent evaluate(Equipment equipment,
                                         MaintenanceRegulation regulation,
                                         MaintenanceTriggerSource source,
                                         UUID userId) {
        if (regulation.getTriggerPolicy() != null && regulation.getTriggerPolicy().name().equals("MANUAL")) {
            return null;
        }
        MaintenanceDueCalculationDto due = dueCalculationService.calculate(equipment.getId(), regulation);
        if (!shouldCreateEvent(due.status())) {
            return null;
        }
        String cycleKey = cycleKey(equipment.getId(), regulation, due);
        MaintenanceDueEvent event = eventRepository.findByCycleKeyAndIsDeletedFalse(cycleKey)
                .orElseGet(MaintenanceDueEvent::new);
        boolean isNew = event.getId() == null;
        event.setEquipmentId(equipment.getId());
        event.setRegulationId(regulation.getId());
        event.setTemplateId(regulation.getTemplateId());
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
            event.setStatus(initialStatus(regulation));
        }
        if (isDuplicateSuppressed(regulation, event)) {
            event.setStatus(MaintenanceDueEventStatus.SUPPRESSED_DUPLICATE);
            return eventService.saveEvent(event, equipment);
        }
        event = eventService.saveEvent(event, equipment);
        if (due.status() == MaintenanceDueStatus.BLOCKED
                || regulation.getAutomationAction() == AutomationAction.TRACK_ONLY
                || regulation.getAutomationAction() == AutomationAction.REQUIRE_APPROVAL) {
            return event;
        }
        if (regulation.getAutomationAction() == AutomationAction.CREATE_TASK) {
            createTask(event, regulation, userId);
        } else if (regulation.getAutomationAction() == AutomationAction.CREATE_WORK_ORDER) {
            createWorkOrder(event, regulation, userId);
        }
        return eventRepository.save(event);
    }

    private boolean shouldCreateEvent(MaintenanceDueStatus status) {
        return status == MaintenanceDueStatus.UPCOMING
                || status == MaintenanceDueStatus.DUE
                || status == MaintenanceDueStatus.OVERDUE
                || status == MaintenanceDueStatus.BLOCKED;
    }

    private MaintenanceDueEventStatus initialStatus(MaintenanceRegulation regulation) {
        return regulation.getAutomationAction() == AutomationAction.REQUIRE_APPROVAL || regulation.isRequiresApproval()
                ? MaintenanceDueEventStatus.AWAITING_APPROVAL
                : MaintenanceDueEventStatus.DETECTED;
    }

    private boolean isDuplicateSuppressed(MaintenanceRegulation regulation, MaintenanceDueEvent event) {
        if (event.getCreatedTaskId() != null || event.getCreatedWorkOrderId() != null) {
            return false;
        }
        if (regulation.getDuplicatePolicy() == DuplicatePolicy.ONE_ITEM_PER_CYCLE) {
            return pprTaskRepository.existsOpenByCycleKey(event.getCycleKey())
                    || workOrderRepository.existsOpenByCycleKey(event.getCycleKey());
        }
        return false;
    }

    private PprTask createTask(MaintenanceDueEvent event, MaintenanceRegulation regulation, UUID userId) {
        if (event.getCreatedTaskId() != null) {
            return pprTaskRepository.findByIdAndIsDeletedFalse(event.getCreatedTaskId()).orElse(null);
        }
        if (regulation.getTemplateId() == null) {
            event.setStatus(MaintenanceDueEventStatus.DETECTED);
            event.setExplanation(append(event.getExplanation(), "templateId is required to create task"));
            return null;
        }
        if (pprTaskRepository.existsOpenByCycleKey(event.getCycleKey())) {
            event.setStatus(MaintenanceDueEventStatus.SUPPRESSED_DUPLICATE);
            return null;
        }
        Equipment equipment = equipment(event);
        UUID departmentId = effectiveDepartmentId(equipment, regulation);
        PprPlan plan = autoPlan(departmentId, userId);
        PprTask task = new PprTask();
        task.setCode(nextTaskCode());
        task.setPlan(plan);
        task.setRegulationId(regulation.getId());
        task.setEquipmentId(equipment.getId());
        task.setMaintenanceDueEventId(event.getId());
        task.setCycleKey(event.getCycleKey());
        task.setTitle(regulation.getName() + " - " + equipment.getCode());
        LocalDateTime start = plannedStart(event);
        task.setScheduledStart(start);
        task.setScheduledEnd(start.plusHours(Math.max(1, (long) Math.ceil(regulation.getNormativeLaborHours()))));
        task.setDueDate(dueDateTime(event));
        task.setPriority(regulation.getDefaultPriority() == null ? PriorityLevel.MEDIUM : regulation.getDefaultPriority());
        task.setPlannedLaborHours(regulation.getNormativeLaborHours());
        task.setStatus(PprTaskStatus.PLANNED);
        PprTask saved = pprTaskRepository.save(task);
        event.setCreatedTaskId(saved.getId());
        event.setStatus(MaintenanceDueEventStatus.TASK_CREATED);
        return saved;
    }

    private WorkOrderDto createWorkOrder(MaintenanceDueEvent event, MaintenanceRegulation regulation, UUID userId) {
        if (event.getCreatedWorkOrderId() != null) {
            return null;
        }
        if (workOrderRepository.existsOpenByCycleKey(event.getCycleKey())) {
            event.setStatus(MaintenanceDueEventStatus.SUPPRESSED_DUPLICATE);
            return null;
        }
        Equipment equipment = equipment(event);
        UUID departmentId = effectiveDepartmentId(equipment, regulation);
        if (departmentId == null) {
            event.setExplanation(append(event.getExplanation(), "departmentId is required to create work order"));
            return null;
        }
        WorkOrderRequest request = new WorkOrderRequest(
                nextWorkOrderNumber(),
                regulation.getName() + " - " + equipment.getCode(),
                equipment.getId(),
                null,
                departmentId,
                null,
                null,
                event.getCreatedTaskId(),
                null,
                workOrderType(regulation),
                workType(regulation),
                null,
                null,
                regulation.getDefaultPriority() == null ? PriorityLevel.MEDIUM : regulation.getDefaultPriority(),
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

    private UUID effectiveDepartmentId(Equipment equipment, MaintenanceRegulation regulation) {
        if (regulation.getDefaultDepartmentId() != null) {
            return regulation.getDefaultDepartmentId();
        }
        return equipment.getResponsibleDepartmentId() != null ? equipment.getResponsibleDepartmentId() : equipment.getDepartmentId();
    }

    private Equipment equipment(MaintenanceDueEvent event) {
        return equipmentRepository.findByIdAndIsDeletedFalse(event.getEquipmentId())
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + event.getEquipmentId()));
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

    private String cycleKey(UUID equipmentId, MaintenanceRegulation regulation, MaintenanceDueCalculationDto due) {
        String source = due.meterType() != null ? due.meterType().name() : "CALENDAR";
        String boundary;
        if (due.meterType() != null && due.meterInterval() != null && due.meterInterval() > 0 && due.meterCurrentValue() != null) {
            boundary = Long.toString((long) Math.floor(due.meterCurrentValue() / due.meterInterval()));
        } else {
            Instant dueAt = due.nextDueAt() != null ? due.nextDueAt() : Instant.now();
            boundary = dueAt.toString();
        }
        return "%s:%s:%s:%s".formatted(equipmentId, regulation.getId(), source, boundary);
    }

    private WorkOrderType workOrderType(MaintenanceRegulation regulation) {
        return isInspectionOrDiagnostic(regulation.getMaintenanceKind()) ? WorkOrderType.INSPECTION : WorkOrderType.PLANNED;
    }

    private WorkType workType(MaintenanceRegulation regulation) {
        return isInspectionOrDiagnostic(regulation.getMaintenanceKind()) ? WorkType.DIAGNOSTICS : WorkType.REPAIR;
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
