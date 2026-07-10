package com.toir.service.sparepartlifecycle;

import com.toir.dto.sparepartlifecycle.SparePartNextRequiredActionCandidate;
import com.toir.dto.sparepartlifecycle.SparePartNextRequiredActionsDto;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.sparepartlifecycle.SparePartDueEvent;
import com.toir.entity.sparepartlifecycle.SparePartInstallation;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.sparepartlifecycle.SparePartDueEventState;
import com.toir.enums.sparepartlifecycle.SparePartInstallationStatus;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.sparepartlifecycle.SparePartDueEventRepository;
import com.toir.repository.sparepartlifecycle.SparePartInstallationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SparePartNextRequiredActionService {

    private static final Collection<MaintenanceDueEventStatus> OPEN_MAINTENANCE_STATUSES = List.of(
            MaintenanceDueEventStatus.DETECTED,
            MaintenanceDueEventStatus.AWAITING_APPROVAL,
            MaintenanceDueEventStatus.TASK_CREATED,
            MaintenanceDueEventStatus.WORK_ORDER_CREATED
    );

    private final EquipmentRepository equipmentRepository;
    private final EquipmentMeterRepository meterRepository;
    private final MaintenanceDueEventRepository maintenanceDueEventRepository;
    private final SparePartInstallationRepository installationRepository;
    private final SparePartDueEventRepository sparePartDueEventRepository;

    @Transactional(readOnly = true)
    public SparePartNextRequiredActionsDto get(UUID equipmentId) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        Instant evaluatedAt = Instant.now();
        List<SparePartNextRequiredActionCandidate> parent = parentCandidates(equipment, evaluatedAt);
        List<SparePartNextRequiredActionCandidate> maintenance = maintenanceDueEventRepository
                .findAllByEquipmentIdAndStatusInAndIsDeletedFalseOrderByUpdatedAtDesc(
                        equipmentId, OPEN_MAINTENANCE_STATUSES)
                .stream()
                .map(this::maintenanceCandidate)
                .toList();
        List<SparePartInstallation> installations = installationRepository
                .findAllByEquipmentIdAndStatusAndIsDeletedFalseOrderByInstalledAtDesc(
                        equipmentId, SparePartInstallationStatus.ACTIVE);
        Map<UUID, SparePartInstallation> byId = installations.stream()
                .collect(Collectors.toMap(SparePartInstallation::getId, Function.identity()));
        List<SparePartNextRequiredActionCandidate> parts = installations.isEmpty()
                ? List.of()
                : sparePartDueEventRepository.findAllByInstallationIdInAndIsDeletedFalse(byId.keySet()).stream()
                        .filter(event -> event.getState() != SparePartDueEventState.RESOLVED)
                        .map(event -> partCandidate(event, byId.get(event.getInstallationId())))
                        .toList();
        List<SparePartNextRequiredActionCandidate> all = new ArrayList<>(parent);
        all.addAll(maintenance);
        all.addAll(parts);
        return new SparePartNextRequiredActionsDto(
                equipmentId,
                parent,
                maintenance,
                parts,
                choosePrimary(all).orElse(null),
                evaluatedAt
        );
    }

    public static Optional<SparePartNextRequiredActionCandidate> choosePrimary(
            Collection<SparePartNextRequiredActionCandidate> candidates) {
        Comparator<SparePartNextRequiredActionCandidate> comparator = Comparator
                .comparingInt(SparePartNextRequiredActionService::severity)
                .thenComparing(
                        SparePartNextRequiredActionCandidate::dueAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(
                        SparePartNextRequiredActionCandidate::remainingRatio,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(candidate -> candidate.sourceType() == null ? "" : candidate.sourceType())
                .thenComparing(candidate -> candidate.sourceId() == null ? "" : candidate.sourceId().toString());
        return candidates.stream().min(comparator);
    }

    private List<SparePartNextRequiredActionCandidate> parentCandidates(Equipment equipment, Instant now) {
        List<SparePartNextRequiredActionCandidate> result = new ArrayList<>();
        LocalDate start = equipment.getOperationStartDate() != null
                ? equipment.getOperationStartDate()
                : equipment.getCommissionedAt();
        Integer months = equipment.getExpectedLifetimeMonths();
        if ((months == null || months <= 0) && equipment.getExpectedLifetimeYears() != null
                && equipment.getExpectedLifetimeYears() > 0) {
            months = equipment.getExpectedLifetimeYears() * 12;
        }
        if (start != null && months != null && months > 0) {
            Instant dueAt = start.plusMonths(months).atStartOfDay().toInstant(ZoneOffset.UTC);
            result.add(new SparePartNextRequiredActionCandidate(
                    "PARENT_DESIGN_LIFE",
                    equipment.getId(),
                    dueAt.isBefore(now) ? "OVERDUE" : "UPCOMING",
                    dueAt,
                    null, null, null, null, null,
                    "DESIGN_LIFE_REVIEW",
                    "Parent equipment calendar design life"
            ));
        }
        if (equipment.getLifetimeMeterId() != null && equipment.getLifetimeLimitValue() != null
                && equipment.getLifetimeLimitValue() > 0) {
            meterRepository.findByIdAndIsDeletedFalse(equipment.getLifetimeMeterId())
                    .ifPresent(meter -> result.add(parentMeterCandidate(equipment, meter)));
        }
        return List.copyOf(result);
    }

    private SparePartNextRequiredActionCandidate parentMeterCandidate(Equipment equipment, EquipmentMeter meter) {
        BigDecimal limit = BigDecimal.valueOf(equipment.getLifetimeLimitValue());
        BigDecimal baseline = BigDecimal.valueOf(
                equipment.getLifetimeBaselineValue() == null ? 0 : equipment.getLifetimeBaselineValue());
        BigDecimal target = baseline.add(limit);
        BigDecimal current = BigDecimal.valueOf(meter.getCurrentValue());
        BigDecimal remaining = target.subtract(current);
        return new SparePartNextRequiredActionCandidate(
                "PARENT_DESIGN_LIFE",
                equipment.getId(),
                remaining.signum() <= 0 ? "OVERDUE" : "UPCOMING",
                null,
                meter.getMeterType(),
                target,
                current,
                remaining,
                ratio(remaining, limit),
                "DESIGN_LIFE_REVIEW",
                "Parent equipment meter design life"
        );
    }

    private SparePartNextRequiredActionCandidate maintenanceCandidate(MaintenanceDueEvent event) {
        BigDecimal current = decimal(event.getMeterCurrentValue());
        BigDecimal remaining = decimal(event.getMeterRemaining());
        BigDecimal interval = decimal(event.getMeterInterval());
        BigDecimal due = current == null || remaining == null ? null : current.add(remaining);
        return new SparePartNextRequiredActionCandidate(
                "MAINTENANCE",
                event.getId(),
                event.getDueStatus().name(),
                event.getDueAt(),
                event.getMeterType(),
                due,
                current,
                remaining,
                ratio(remaining, interval),
                "MAINTENANCE_REQUIRED",
                event.getReasonCode() == null ? event.getExplanation() : event.getReasonCode()
        );
    }

    private SparePartNextRequiredActionCandidate partCandidate(SparePartDueEvent event,
                                                                SparePartInstallation installation) {
        BigDecimal current = event.getCurrentMeterValue();
        BigDecimal remaining = event.getDueMeterValue() == null || current == null
                ? null
                : event.getDueMeterValue().subtract(current);
        return new SparePartNextRequiredActionCandidate(
                "SPARE_PART_INSTALLATION",
                event.getInstallationId(),
                event.getState().name(),
                event.getDueAt(),
                event.getMeterType(),
                event.getDueMeterValue(),
                current,
                remaining,
                ratio(remaining, event.getDueMeterValue()),
                event.getDueAction().name(),
                installation == null
                        ? "Installed-part service-life action"
                        : "Installed part at " + installation.getPositionKey()
        );
    }

    private static int severity(SparePartNextRequiredActionCandidate candidate) {
        String status = candidate.status() == null ? "" : candidate.status();
        if ("BLOCK_OPERATION".equals(candidate.action())
                && ("DUE".equals(status) || "OVERDUE".equals(status) || "BLOCKED".equals(status))) {
            return 0;
        }
        if ("BLOCKED".equals(status) || "OVERDUE".equals(status)) return 0;
        if ("DUE".equals(status)) return 1;
        if ("WARNING".equals(status)) return 2;
        return 3;
    }

    private static BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private static BigDecimal ratio(BigDecimal remaining, BigDecimal total) {
        return remaining == null || total == null || total.signum() == 0
                ? null
                : remaining.divide(total, 6, RoundingMode.HALF_UP);
    }
}
