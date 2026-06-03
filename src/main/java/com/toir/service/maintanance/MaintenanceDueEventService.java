package com.toir.service.maintanance;

import com.toir.dto.maintenancedue.MaintenanceDueEventDto;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.OperationalIssueType;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.service.OperationalIssueService;
import com.toir.util.PaginationUtils;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
        Page<MaintenanceDueEvent> result = repository.search(
                equipmentId,
                departmentId,
                regulationId,
                status,
                dueStatus,
                from,
                to,
                PaginationUtils.pageRequest(page, size)
        );
        return toDtoPage(result);
    }

    @Transactional
    public MaintenanceDueEvent saveEvent(MaintenanceDueEvent event, Equipment equipment) {
        MaintenanceDueEvent saved = repository.save(event);
        openIssue(saved, equipment);
        return saved;
    }

    @Transactional
    public MaintenanceDueEvent cancel(UUID id, String reason) {
        MaintenanceDueEvent event = getOrThrow(id);
        if (event.getStatus() == MaintenanceDueEventStatus.COMPLETED
                || event.getStatus() == MaintenanceDueEventStatus.CANCELLED) {
            throw RestException.badRequest("Maintenance due event is already closed");
        }
        event.setStatus(MaintenanceDueEventStatus.CANCELLED);
        event.setResolvedAt(Instant.now());
        event.setResolutionReason(reason);
        return repository.save(event);
    }

    @Transactional(readOnly = true)
    public MaintenanceDueEvent getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Maintenance due event not found: " + id));
    }

    @Transactional(readOnly = true)
    public MaintenanceDueEventDto toDto(MaintenanceDueEvent event) {
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

    private void openIssue(MaintenanceDueEvent event, Equipment equipment) {
        OperationalIssueType type = switch (event.getDueStatus()) {
            case OVERDUE -> OperationalIssueType.MAINTENANCE_OVERDUE;
            case BLOCKED -> OperationalIssueType.MISSING_METERS;
            default -> OperationalIssueType.MAINTENANCE_DUE;
        };
        NotificationSeverity severity = switch (event.getDueStatus()) {
            case OVERDUE, BLOCKED -> NotificationSeverity.CRITICAL;
            case DUE -> NotificationSeverity.WARNING;
            default -> NotificationSeverity.INFO;
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

    public static Collection<MaintenanceDueEventStatus> openStatuses() {
        return OPEN_STATUSES;
    }
}
