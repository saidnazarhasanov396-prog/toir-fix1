package com.toir.service.maintanance;

import com.toir.dto.maintenancedue.MaintenanceDueEventDto;
import com.toir.dto.maintenancedueforecast.MaintenanceDueForecastResponse;
import com.toir.dto.maintenanceplanning.MaintenanceDueStructuredExplanationDto;
import com.toir.entity.Department;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentType;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.repository.maintenance.EquipmentMaintenanceRuleRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.security.ScopeAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaintenanceDueForecastService {

    private static final List<String> NO_ACTIONS = List.of();

    private final MaintenanceDueEventRepository dueEventRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentTypeRepository equipmentTypeRepository;
    private final DepartmentRepository departmentRepository;
    private final MaintenanceRegulationRepository regulationRepository;
    private final EquipmentMaintenanceRuleRepository ruleRepository;
    private final ScopeAccessService scopeAccessService;

    @Transactional(readOnly = true)
    public MaintenanceDueForecastResponse getForecast(LocalDate from,
                                                      LocalDate to,
                                                      UUID departmentId,
                                                      UUID equipmentTypeId,
                                                      MaintenanceDueStatus status,
                                                      UUID criticality,
                                                      MaintenanceTriggerPolicy triggerPolicy) {
        LocalDate horizonFrom = from == null ? LocalDate.now(ZoneOffset.UTC) : from;
        LocalDate horizonTo = to == null ? horizonFrom.plusDays(90) : to;
        UUID scopedDepartmentId = scopeAccessService.enforceDepartmentScope(departmentId);

        List<MaintenanceDueEvent> events = dueEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(event -> MaintenanceDueEventService.openStatuses().contains(event.getStatus()))
                .filter(event -> isWithinForecastHorizon(event, horizonFrom, horizonTo))
                .toList();

        Map<UUID, Equipment> equipmentById = loadEquipment(events);
        Map<UUID, EquipmentType> equipmentTypeById = loadEquipmentTypes(equipmentById);
        Map<UUID, Department> departmentById = loadDepartments(equipmentById);
        Map<UUID, MaintenanceRegulation> regulationById = loadRegulations(events);
        Map<UUID, EquipmentMaintenanceRule> ruleById = loadRules(events);

        List<MaintenanceDueForecastResponse.Row> rows = events.stream()
                .map(event -> toRow(event, equipmentById, equipmentTypeById, departmentById, regulationById, ruleById, horizonFrom))
                .filter(Objects::nonNull)
                .filter(row -> scopedDepartmentId == null
                        || (row.department() != null && scopedDepartmentId.equals(row.department().id())))
                .filter(row -> equipmentTypeId == null
                        || (row.equipmentType() != null && equipmentTypeId.equals(row.equipmentType().id())))
                .filter(row -> status == null || status == row.status())
                .filter(row -> criticality == null
                        || criticality.equals(equipmentById.get(row.equipmentId()).getCriticalityClassId()))
                .filter(row -> triggerPolicy == null || triggerPolicy == row.triggerPolicy())
                .sorted(Comparator
                        .comparing(this::bucketRank)
                        .thenComparing(MaintenanceDueForecastResponse.Row::dueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return new MaintenanceDueForecastResponse(
                horizonFrom,
                horizonTo,
                counters(rows),
                buckets(rows, horizonFrom, horizonTo),
                rows
        );
    }

    private boolean isWithinForecastHorizon(MaintenanceDueEvent event, LocalDate horizonFrom, LocalDate horizonTo) {
        if (event.getDueStatus() == MaintenanceDueStatus.BLOCKED) {
            return true;
        }
        if (event.getDueStatus() == MaintenanceDueStatus.OVERDUE) {
            return true;
        }
        if (event.getDueAt() == null) {
            return false;
        }
        Instant fromInstant = horizonFrom.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toExclusive = horizonTo.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return !event.getDueAt().isBefore(fromInstant) && event.getDueAt().isBefore(toExclusive);
    }

    private Map<UUID, Equipment> loadEquipment(List<MaintenanceDueEvent> events) {
        LinkedHashSet<UUID> ids = events.stream()
                .map(MaintenanceDueEvent::getEquipmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return equipmentRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(Equipment::getId, Function.identity(), (left, right) -> left));
    }

    private Map<UUID, EquipmentType> loadEquipmentTypes(Map<UUID, Equipment> equipmentById) {
        LinkedHashSet<UUID> ids = equipmentById.values().stream()
                .map(Equipment::getEquipmentTypeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(EquipmentType::getId, Function.identity(), (left, right) -> left));
    }

    private Map<UUID, Department> loadDepartments(Map<UUID, Equipment> equipmentById) {
        LinkedHashSet<UUID> ids = equipmentById.values().stream()
                .map(this::effectiveDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return departmentRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(Department::getId, Function.identity(), (left, right) -> left));
    }

    private Map<UUID, MaintenanceRegulation> loadRegulations(List<MaintenanceDueEvent> events) {
        LinkedHashSet<UUID> ids = events.stream()
                .map(MaintenanceDueEvent::getRegulationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return regulationRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(MaintenanceRegulation::getId, Function.identity(), (left, right) -> left));
    }

    private Map<UUID, EquipmentMaintenanceRule> loadRules(List<MaintenanceDueEvent> events) {
        LinkedHashSet<UUID> ids = events.stream()
                .map(MaintenanceDueEvent::getEquipmentMaintenanceRuleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return ruleRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(EquipmentMaintenanceRule::getId, Function.identity(), (left, right) -> left));
    }

    private MaintenanceDueForecastResponse.Row toRow(MaintenanceDueEvent event,
                                                     Map<UUID, Equipment> equipmentById,
                                                     Map<UUID, EquipmentType> equipmentTypeById,
                                                     Map<UUID, Department> departmentById,
                                                     Map<UUID, MaintenanceRegulation> regulationById,
                                                     Map<UUID, EquipmentMaintenanceRule> ruleById,
                                                     LocalDate horizonFrom) {
        Equipment equipment = equipmentById.get(event.getEquipmentId());
        if (equipment == null) {
            return null;
        }
        MaintenanceDueEventDto dto = MaintenanceDueEventDto.from(event, null, null);
        MaintenanceDueStructuredExplanationDto explanation = dto.structuredExplanation();
        EquipmentType equipmentType = equipmentTypeById.get(equipment.getEquipmentTypeId());
        Department department = departmentById.get(effectiveDepartmentId(equipment));
        MaintenanceTriggerPolicy triggerPolicy = resolveTriggerPolicy(event, regulationById, ruleById);

        return new MaintenanceDueForecastResponse.Row(
                event.getId(),
                equipment.getId(),
                equipment.getCode(),
                equipment.getName(),
                equipmentType == null ? null : ref(equipmentType.getId(), equipmentType.getCode(), equipmentType.getName()),
                department == null ? null : ref(department.getId(), department.getCode(), department.getName()),
                dto.dueStatus(),
                dto.dueAt(),
                remainingDays(dto.dueAt(), horizonFrom),
                dto.meterType(),
                dto.meterCurrentValue(),
                dto.meterRemaining(),
                triggerPolicy,
                explanation == null ? dto.explanation() : explanation.reasonText(),
                explanation == null ? null : explanation.blockingCode(),
                explanation == null ? null : explanation.blockingField(),
                explanation == null ? null : explanation.fixLink(),
                dto.createdWorkOrderId(),
                allowedActions(event, dto.dueStatus())
        );
    }

    private MaintenanceDueForecastResponse.Ref ref(UUID id, String code, String name) {
        return new MaintenanceDueForecastResponse.Ref(id, code, name);
    }

    private UUID effectiveDepartmentId(Equipment equipment) {
        return equipment.getResponsibleDepartmentId() != null
                ? equipment.getResponsibleDepartmentId()
                : equipment.getDepartmentId();
    }

    private Long remainingDays(Instant dueAt, LocalDate horizonFrom) {
        if (dueAt == null) {
            return null;
        }
        LocalDate dueDate = dueAt.atZone(ZoneOffset.UTC).toLocalDate();
        return ChronoUnit.DAYS.between(horizonFrom, dueDate);
    }

    private MaintenanceTriggerPolicy resolveTriggerPolicy(MaintenanceDueEvent event,
                                                          Map<UUID, MaintenanceRegulation> regulationById,
                                                          Map<UUID, EquipmentMaintenanceRule> ruleById) {
        if (event.getEquipmentMaintenanceRuleId() != null) {
            EquipmentMaintenanceRule rule = ruleById.get(event.getEquipmentMaintenanceRuleId());
            if (rule != null) {
                return rule.getTriggerPolicy();
            }
        }
        if (event.getRegulationId() != null) {
            MaintenanceRegulation regulation = regulationById.get(event.getRegulationId());
            if (regulation != null) {
                return regulation.getTriggerPolicy();
            }
        }
        return null;
    }

    private List<String> allowedActions(MaintenanceDueEvent event, MaintenanceDueStatus dueStatus) {
        if (event.getStatus() == MaintenanceDueEventStatus.COMPLETED
                || event.getStatus() == MaintenanceDueEventStatus.CANCELLED) {
            return NO_ACTIONS;
        }
        java.util.ArrayList<String> actions = new java.util.ArrayList<>();
        if (event.getStatus() == MaintenanceDueEventStatus.AWAITING_APPROVAL) {
            actions.add("APPROVE");
        }
        actions.add("CANCEL");
        if (dueStatus != MaintenanceDueStatus.BLOCKED
                && event.getCreatedWorkOrderId() == null
                && event.getStatus() != MaintenanceDueEventStatus.WORK_ORDER_CREATED
                && event.getStatus() != MaintenanceDueEventStatus.SUPPRESSED_DUPLICATE) {
            actions.add("CREATE_WORK_ORDER");
        }
        return List.copyOf(actions);
    }

    private MaintenanceDueForecastResponse.Counters counters(List<MaintenanceDueForecastResponse.Row> rows) {
        return new MaintenanceDueForecastResponse.Counters(
                rows.stream().filter(row -> row.status() == MaintenanceDueStatus.UPCOMING).count(),
                rows.stream().filter(row -> row.status() == MaintenanceDueStatus.DUE).count(),
                rows.stream().filter(row -> row.status() == MaintenanceDueStatus.OVERDUE).count(),
                rows.stream().filter(row -> row.status() == MaintenanceDueStatus.BLOCKED).count(),
                rows.stream().filter(row -> row.allowedActions().contains("APPROVE")).count(),
                rows.stream().filter(row -> row.createdWorkOrderId() != null).count()
        );
    }

    private MaintenanceDueForecastResponse.Buckets buckets(List<MaintenanceDueForecastResponse.Row> rows,
                                                           LocalDate horizonFrom,
                                                           LocalDate horizonTo) {
        return new MaintenanceDueForecastResponse.Buckets(
                rows.stream().filter(row -> bucketRank(row) == 0).toList(),
                rows.stream().filter(row -> bucketRank(row) == 1).toList(),
                rows.stream().filter(row -> bucketRank(row) == 2).toList(),
                rows.stream().filter(row -> bucketRank(row) == 3).toList(),
                rows.stream().filter(row -> row.status() == MaintenanceDueStatus.BLOCKED).toList()
        );
    }

    private int bucketRank(MaintenanceDueForecastResponse.Row row) {
        if (row.status() == MaintenanceDueStatus.BLOCKED) {
            return 4;
        }
        if (row.status() == MaintenanceDueStatus.OVERDUE
                || (row.remainingDays() != null && row.remainingDays() < 0)) {
            return 0;
        }
        if (row.remainingDays() != null && row.remainingDays() <= 7) {
            return 1;
        }
        if (row.remainingDays() != null && row.remainingDays() <= 30) {
            return 2;
        }
        return 3;
    }
}
