package com.toir.service.sparepartlifecycle;

import com.toir.dto.sparepartlifecycle.SparePartOperationalReadinessDto;
import com.toir.dto.sparepartlifecycle.SparePartReadinessReason;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.sparepartlifecycle.SparePartDueEvent;
import com.toir.entity.sparepartlifecycle.SparePartInstallation;
import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import com.toir.enums.sparepartlifecycle.SparePartDueEventState;
import com.toir.enums.sparepartlifecycle.SparePartInstallationStatus;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleEvaluationState;
import com.toir.enums.sparepartlifecycle.SparePartOperationalReadiness;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.sparepartlifecycle.SparePartDueEventRepository;
import com.toir.repository.sparepartlifecycle.SparePartInstallationRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SparePartOperationalReadinessService {

    private final EquipmentRepository equipmentRepository;
    private final SparePartInstallationRepository installationRepository;
    private final SparePartDueEventRepository dueEventRepository;

    @Transactional(readOnly = true)
    public SparePartOperationalReadinessDto get(UUID equipmentId) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        List<SparePartInstallation> installations = installationRepository
                .findAllByEquipmentIdAndStatusAndIsDeletedFalseOrderByInstalledAtDesc(
                        equipmentId,
                        SparePartInstallationStatus.ACTIVE
                );
        List<UUID> ids = installations.stream().map(SparePartInstallation::getId).toList();
        List<SparePartDueEvent> events = ids.isEmpty()
                ? List.of()
                : dueEventRepository.findAllByInstallationIdInAndIsDeletedFalse(ids);
        return evaluate(equipment, installations, events);
    }

    public SparePartOperationalReadinessDto evaluate(Equipment equipment,
                                                     List<SparePartInstallation> installations,
                                                     List<SparePartDueEvent> events) {
        Map<UUID, SparePartInstallation> byId = installations.stream()
                .collect(Collectors.toMap(SparePartInstallation::getId, Function.identity(), (left, right) -> left));
        List<SparePartReadinessReason> reasons = new ArrayList<>();
        boolean evaluationError = false;
        boolean blocked = false;
        boolean maintenanceRequired = false;
        boolean warning = false;
        for (SparePartInstallation installation : installations) {
            if (installation.getLifecycleEvaluationState() == SparePartLifecycleEvaluationState.ERROR) {
                evaluationError = true;
                reasons.add(new SparePartReadinessReason(
                        "SPARE_PART_INSTALLATION",
                        installation.getId(),
                        "SPARE_PART_LIFECYCLE_EVALUATION_ERROR",
                        null,
                        installation.getSparePartId(),
                        installation.getPositionKey(),
                        null,
                        null,
                        null
                ));
            }
        }
        for (SparePartDueEvent event : events) {
            if (event.getState() == SparePartDueEventState.RESOLVED
                    || event.getState() == SparePartDueEventState.UPCOMING) {
                continue;
            }
            SparePartInstallation installation = byId.get(event.getInstallationId());
            if (event.getDueAction() == SparePartDueAction.BLOCK_OPERATION
                    && (event.getState() == SparePartDueEventState.DUE
                    || event.getState() == SparePartDueEventState.OVERDUE)) {
                blocked = true;
            } else if (event.getDueAction() == SparePartDueAction.MAINTENANCE_REQUIRED
                    && (event.getState() == SparePartDueEventState.DUE
                    || event.getState() == SparePartDueEventState.OVERDUE)) {
                maintenanceRequired = true;
            } else {
                warning = true;
            }
            reasons.add(new SparePartReadinessReason(
                    "SPARE_PART_INSTALLATION",
                    event.getInstallationId(),
                    event.getState() == SparePartDueEventState.OVERDUE
                            ? "SPARE_PART_SERVICE_LIFE_OVERDUE"
                            : event.getState() == SparePartDueEventState.DUE
                            ? "SPARE_PART_SERVICE_LIFE_EXPIRED"
                            : "SPARE_PART_SERVICE_LIFE_WARNING",
                    event.getDueAction(),
                    installation == null ? null : installation.getSparePartId(),
                    installation == null ? null : installation.getPositionKey(),
                    event.getDueAt(),
                    event.getDueMeterValue(),
                    null
            ));
        }
        SparePartOperationalReadiness readiness = evaluationError
                ? SparePartOperationalReadiness.EVALUATION_ERROR
                : blocked
                ? SparePartOperationalReadiness.BLOCKED
                : maintenanceRequired
                ? SparePartOperationalReadiness.MAINTENANCE_REQUIRED
                : warning
                ? SparePartOperationalReadiness.WARNING
                : SparePartOperationalReadiness.READY;
        return new SparePartOperationalReadinessDto(
                equipment.getId(),
                equipment.getStatus(),
                readiness,
                List.copyOf(reasons),
                Instant.now()
        );
    }
}
