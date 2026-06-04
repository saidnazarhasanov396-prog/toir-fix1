package com.toir.service.maintanance;

import com.toir.dto.maintenancedue.MaintenanceDueEventDto;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceTriggerSource;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.OperationalIssueType;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.OperationalIssueService;
import com.toir.util.PaginationUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaintenanceDueEventService {

    private static final Collection<MaintenanceDueEventStatus> OPEN_STATUSES = List.of(
            MaintenanceDueEventStatus.DETECTED,
            MaintenanceDueEventStatus.AWAITING_APPROVAL,
            MaintenanceDueEventStatus.TASK_CREATED,
            MaintenanceDueEventStatus.WORK_ORDER_CREATED,
            MaintenanceDueEventStatus.SUPPRESSED_DUPLICATE
    );

    private final MaintenanceDueEventRepository repository;
    private final EquipmentRepository equipmentRepository;
    private final MaintenanceRegulationRepository regulationRepository;
    private final OperationalIssueService operationalIssueService;
    private final ScopeAccessService scopeAccessService;

    @Transactional(readOnly = true)
    public Page<MaintenanceDueEventDto> search(UUID equipmentId,
                                               UUID departmentId,
                                               UUID regulationId,
                                               MaintenanceDueEventStatus status,
                                               MaintenanceDueStatus dueStatus,
                                               Instant from,
                                               Instant to,
                                               int page,
                                               int size) {
        Page<MaintenanceDueEvent> result = repository.findAll(
                searchSpecification(
                        equipmentId,
                        departmentId,
                        regulationId,
                        status,
                        dueStatus,
                        from,
                        to
                ),
                PaginationUtils.pageRequest(page, size).withSort(
                        Sort.by(Sort.Direction.DESC, "detectedAt")
                                .and(Sort.by(Sort.Direction.DESC, "updatedAt"))
                )
        );
        return toDtoPage(result);
    }

    private static Specification<MaintenanceDueEvent> searchSpecification(UUID equipmentId,
                                                                          UUID departmentId,
                                                                          UUID regulationId,
                                                                          MaintenanceDueEventStatus status,
                                                                          MaintenanceDueStatus dueStatus,
                                                                          Instant from,
                                                                          Instant to) {
        return (event, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Root<Equipment> equipment = query.from(Equipment.class);

            predicates.add(cb.isFalse(event.get("isDeleted")));
            predicates.add(cb.isFalse(equipment.get("isDeleted")));
            predicates.add(cb.equal(equipment.get("id"), event.get("equipmentId")));

            if (equipmentId != null) {
                predicates.add(cb.equal(event.get("equipmentId"), equipmentId));
            }
            if (departmentId != null) {
                CriteriaBuilder.Coalesce<UUID> department = cb.coalesce();
                department.value(equipment.get("responsibleDepartmentId"));
                department.value(equipment.get("departmentId"));
                predicates.add(cb.equal(department, departmentId));
            }
            if (regulationId != null) {
                predicates.add(cb.equal(event.get("regulationId"), regulationId));
            }
            if (status != null) {
                predicates.add(cb.equal(event.get("status"), status));
            }
            if (dueStatus != null) {
                predicates.add(cb.equal(event.get("dueStatus"), dueStatus));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(event.get("dueAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(event.get("dueAt"), to));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Transactional
    public MaintenanceDueEvent saveEvent(MaintenanceDueEvent event, Equipment equipment) {
        normalizeMeterDueStatus(event);
        MaintenanceDueEvent saved = repository.save(event);
        syncOperationalIssue(saved, equipment);
        return saved;
    }

    @Transactional
    public MaintenanceDueEvent cancel(UUID id, String reason) {
        return cancel(getOrThrow(id), reason);
    }

    @Transactional
    public MaintenanceDueEvent cancel(MaintenanceDueEvent event, String reason) {
        assertCanAccessEvent(event);
        if (event.getStatus() == MaintenanceDueEventStatus.COMPLETED
                || event.getStatus() == MaintenanceDueEventStatus.CANCELLED) {
            throw RestException.badRequest("Maintenance due event is already closed");
        }
        event.setStatus(MaintenanceDueEventStatus.CANCELLED);
        event.setResolvedAt(Instant.now());
        event.setResolutionReason(reason);
        MaintenanceDueEvent saved = repository.save(event);
        operationalIssueService.resolveOpen("MaintenanceDueEvent", event.getId());
        return saved;
    }

    @Transactional
    public MaintenanceDueEvent completeFromWorkOrder(UUID id, String reason) {
        return completeFromWorkOrder(getOrThrow(id), reason);
    }

    @Transactional
    public MaintenanceDueEvent completeFromWorkOrder(MaintenanceDueEvent event, String reason) {
        if (event.getStatus() == MaintenanceDueEventStatus.COMPLETED) {
            operationalIssueService.resolveOpen("MaintenanceDueEvent", event.getId());
            return event;
        }
        event.setStatus(MaintenanceDueEventStatus.COMPLETED);
        event.setResolvedAt(Instant.now());
        event.setResolutionReason(reason);
        MaintenanceDueEvent saved = repository.save(event);
        operationalIssueService.resolveOpen("MaintenanceDueEvent", event.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public MaintenanceDueEvent getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Maintenance due event not found: " + id));
    }

    @Transactional(readOnly = true)
    public UUID departmentIdForEvent(MaintenanceDueEvent event) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(event.getEquipmentId())
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + event.getEquipmentId()));
        return effectiveDepartmentId(equipment);
    }

    @Transactional(readOnly = true)
    public void assertCanMutate(MaintenanceDueEvent event) {
        assertCanAccessEvent(event);
    }

    @Transactional(readOnly = true)
    public void assertCanAccessEvent(MaintenanceDueEvent event) {
        UUID departmentId = departmentIdForEvent(event);
        if (departmentId == null && scopeAccessService.isScopeAdmin()) {
            return;
        }
        scopeAccessService.assertCanAccessDepartment(departmentId);
    }

    @Transactional(readOnly = true)
    public MaintenanceDueEventDto toDto(MaintenanceDueEvent event) {
        normalizeMeterDueStatus(event);
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(event.getEquipmentId()).orElse(null);
        MaintenanceRegulation regulation = event.getRegulationId() == null
                ? null
                : regulationRepository.findByIdAndIsDeletedFalse(event.getRegulationId()).orElse(null);
        return MaintenanceDueEventDto.from(
                event,
                equipment == null ? null : new MaintenanceDueEventDto.Ref(
                        equipment.getId(), equipment.getCode(), equipment.getName()),
                regulation == null ? null : new MaintenanceDueEventDto.Ref(
                        regulation.getId(), regulation.getCode(), regulation.getName())
        );
    }

    private Page<MaintenanceDueEventDto> toDtoPage(Page<MaintenanceDueEvent> page) {
        List<MaintenanceDueEvent> events = page.getContent();
        Map<UUID, Equipment> equipmentById = equipmentRepository.findAllByIdInAndIsDeletedFalse(
                        events.stream().map(MaintenanceDueEvent::getEquipmentId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Equipment::getId, Function.identity(), (left, right) -> left));
        List<UUID> regulationIds = events.stream()
                .map(MaintenanceDueEvent::getRegulationId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, MaintenanceRegulation> regulationById = regulationIds.isEmpty()
                ? Map.of()
                : regulationRepository.findAllByIdInAndIsDeletedFalse(regulationIds).stream()
                .collect(Collectors.toMap(MaintenanceRegulation::getId, Function.identity(), (left, right) -> left));
        return page.map(event -> {
            normalizeMeterDueStatus(event);
            Equipment equipment = equipmentById.get(event.getEquipmentId());
            MaintenanceRegulation regulation = event.getRegulationId() == null ? null : regulationById.get(event.getRegulationId());
            return MaintenanceDueEventDto.from(
                    event,
                    equipment == null ? null : new MaintenanceDueEventDto.Ref(
                            equipment.getId(), equipment.getCode(), equipment.getName()),
                    regulation == null ? null : new MaintenanceDueEventDto.Ref(
                            regulation.getId(), regulation.getCode(), regulation.getName())
            );
        });
    }

    private void syncOperationalIssue(MaintenanceDueEvent event, Equipment equipment) {
        if (event.getDueStatus() != MaintenanceDueStatus.OVERDUE
                && event.getDueStatus() != MaintenanceDueStatus.BLOCKED) {
            operationalIssueService.resolveOpen("MaintenanceDueEvent", event.getId());
            return;
        }
        OperationalIssueType type = switch (event.getDueStatus()) {
            case OVERDUE -> OperationalIssueType.MAINTENANCE_OVERDUE;
            case BLOCKED -> OperationalIssueType.MISSING_METERS;
            default -> throw new IllegalStateException("Unsupported issue due status: " + event.getDueStatus());
        };
        NotificationSeverity severity = switch (event.getDueStatus()) {
            case OVERDUE, BLOCKED -> NotificationSeverity.CRITICAL;
            default -> throw new IllegalStateException("Unsupported issue due status: " + event.getDueStatus());
        };
        operationalIssueService.openOrUpdate(
                type,
                severity,
                event.getEquipmentId(),
                equipment == null ? null : java.util.Objects.requireNonNullElse(
                        equipment.getResponsibleDepartmentId(), equipment.getDepartmentId()),
                "MaintenanceDueEvent",
                event.getId(),
                "Maintenance due: " + event.getCycleKey(),
                event.getExplanation()
        );
    }

    private UUID effectiveDepartmentId(Equipment equipment) {
        return equipment.getResponsibleDepartmentId() != null
                ? equipment.getResponsibleDepartmentId()
                : equipment.getDepartmentId();
    }

    private void normalizeMeterDueStatus(MaintenanceDueEvent event) {
        if (!meterDominant(event)) {
            return;
        }
        int remaining = BigDecimal.valueOf(event.getMeterRemaining()).compareTo(BigDecimal.ZERO);
        if (remaining < 0) {
            event.setDueStatus(MaintenanceDueStatus.OVERDUE);
            event.setExplanation(replaceMeterExplanation(event.getExplanation(), "Meter trigger overdue"));
        } else if (remaining == 0) {
            event.setDueStatus(MaintenanceDueStatus.DUE);
            event.setExplanation(replaceMeterExplanation(event.getExplanation(), "Meter trigger due"));
        } else if (isWithinMeterLeadWindow(event)) {
            event.setDueStatus(MaintenanceDueStatus.UPCOMING);
            event.setExplanation(replaceMeterExplanation(event.getExplanation(), "Meter trigger upcoming"));
        } else {
            event.setDueStatus(MaintenanceDueStatus.NOT_DUE);
            event.setExplanation(replaceMeterExplanation(event.getExplanation(), "Meter trigger not due"));
        }
    }

    private boolean isWithinMeterLeadWindow(MaintenanceDueEvent event) {
        if (event.getMeterInterval() == null || event.getMeterInterval() <= 0) {
            return true;
        }
        return BigDecimal.valueOf(event.getMeterRemaining())
                .compareTo(BigDecimal.valueOf(event.getMeterInterval() * 0.05)) <= 0;
    }

    private boolean meterDominant(MaintenanceDueEvent event) {
        if (event.getMeterType() == null || event.getMeterRemaining() == null) {
            return false;
        }
        if (event.getTriggerSource() == MaintenanceTriggerSource.METER_READING) {
            return true;
        }
        String explanation = event.getExplanation();
        return explanation == null || explanation.isBlank() || explanation.startsWith("Meter trigger");
    }

    private String replaceMeterExplanation(String explanation, String replacement) {
        if (explanation == null || explanation.isBlank()) {
            return replacement;
        }
        return explanation
                .replace("Meter trigger overdue", replacement)
                .replace("Meter trigger upcoming", replacement)
                .replace("Meter trigger not due", replacement)
                .replace("Meter trigger due", replacement);
    }

    public static Collection<MaintenanceDueEventStatus> openStatuses() {
        return OPEN_STATUSES;
    }
}
