package com.toir.service.sparepartlifecycle;

import com.toir.dto.sparepartlifecycle.SparePartOperationalReadinessDto;
import com.toir.dto.sparepartlifecycle.SparePartReadinessReason;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.sparepartlifecycle.SparePartDueEvent;
import com.toir.entity.sparepartlifecycle.SparePartInstallation;
import com.toir.enums.sparepartlifecycle.SparePartDueEventState;
import com.toir.enums.sparepartlifecycle.SparePartInstallationStatus;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleEvaluationState;
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
    private final SparePartLifecyclePolicy lifecyclePolicy;

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
        for (SparePartInstallation installation : installations) {
            if (installation.getLifecycleEvaluationState() == SparePartLifecycleEvaluationState.ERROR) {
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
            if (event.getState() == SparePartDueEventState.RESOLVED) {
                continue;
            }
            SparePartInstallation installation = byId.get(event.getInstallationId());
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
        SparePartLifecyclePolicy.Assessment assessment = lifecyclePolicy.assess(installations, events);
        return new SparePartOperationalReadinessDto(
                equipment.getId(),
                equipment.getStatus(),
                assessment.readinessStatus(),
                assessment.hasEvaluationError(),
                assessment.evaluationErrorCount(),
                List.copyOf(reasons),
                Instant.now()
        );
    }
}
