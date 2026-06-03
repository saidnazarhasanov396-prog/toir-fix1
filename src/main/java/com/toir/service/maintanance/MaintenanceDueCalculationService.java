package com.toir.service.maintanance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.maintenanceplanning.MaintenanceDueCalculationDto;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceCompletionAnchor;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.maintenance.MaintenanceCompletionAnchorRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MaintenanceDueCalculationService {

    private static final double UPCOMING_RATIO = 0.05;

    private final EquipmentMeterRepository meterRepository;
    private final MaintenanceCompletionAnchorRepository anchorRepository;
    private final ObjectMapper objectMapper;

    public MaintenanceDueCalculationDto calculate(UUID equipmentId, MaintenanceRegulation regulation) {
        return calculate(
                equipmentId,
                regulation.getId(),
                null,
                regulation.getPeriodicityUnit(),
                regulation.getPeriodicityValue(),
                regulation.getToleranceDays(),
                regulation.getTriggerMeterType(),
                regulation.getTriggerMeterInterval(),
                regulation.getTriggerPolicy(),
                regulation.getRecalculationPolicy()
        );
    }

    public MaintenanceDueCalculationDto calculate(UUID equipmentId, EquipmentMaintenanceRule rule) {
        return calculate(
                equipmentId,
                rule.getBaseRegulationId(),
                rule.getId(),
                rule.getPeriodicityUnit(),
                rule.getPeriodicityValue(),
                rule.getToleranceDays(),
                rule.getTriggerMeterType(),
                rule.getTriggerMeterInterval(),
                rule.getTriggerPolicy(),
                rule.getRecalculationPolicy()
        );
    }

    private MaintenanceDueCalculationDto calculate(UUID equipmentId,
                                                  UUID regulationId,
                                                  UUID ruleId,
                                                  PeriodicityUnit periodicityUnit,
                                                  int periodicityValue,
                                                  Integer toleranceDays,
                                                  MeterType meterType,
                                                  Double meterInterval,
                                                  MaintenanceTriggerPolicy triggerPolicy,
                                                  MaintenanceRecalculationPolicy recalculationPolicy) {
        MaintenanceTriggerPolicy effectiveTriggerPolicy = triggerPolicy == null ? MaintenanceTriggerPolicy.ANY : triggerPolicy;
        MaintenanceRecalculationPolicy effectiveRecalculationPolicy = recalculationPolicy == null
                ? MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION
                : recalculationPolicy;
        if (effectiveTriggerPolicy == MaintenanceTriggerPolicy.MANUAL) {
            return dto(equipmentId, regulationId, ruleId, MaintenanceDueStatus.NOT_DUE, false, false,
                    null, null, meterType, null, null, meterInterval, null, "Manual trigger policy");
        }

        Optional<MaintenanceCompletionAnchor> anchor =
                anchorRepository.findLatestAnchor(equipmentId, regulationId, ruleId);
        Instant lastPerformedAt = anchor.map(MaintenanceCompletionAnchor::getPerformedAt).orElse(null);

        boolean meterTriggerConfigured = meterType != null && meterInterval != null && meterInterval > 0;
        TriggerSignal calendarSignal = meterTriggerConfigured
                ? TriggerSignal.notConfigured()
                : calendarSignal(anchor.orElse(null), periodicityUnit, periodicityValue,
                        toleranceDays, effectiveRecalculationPolicy);
        TriggerSignal meterSignal = meterSignal(equipmentId, anchor.orElse(null), meterType, meterInterval);
        if (meterSignal.status() == MaintenanceDueStatus.BLOCKED) {
            return dto(equipmentId, regulationId, ruleId, MaintenanceDueStatus.BLOCKED,
                    isDue(calendarSignal), false, lastPerformedAt,
                    calendarSignal.nextDueAt(), meterType, meterSignal.currentValue(), meterSignal.anchorValue(),
                    meterInterval, meterSignal.remaining(), meterSignal.explanation());
        }

        List<TriggerSignal> configured = new ArrayList<>();
        if (calendarSignal.configured()) {
            configured.add(calendarSignal);
        }
        if (meterSignal.configured()) {
            configured.add(meterSignal);
        }
        if (configured.isEmpty()) {
            return dto(equipmentId, regulationId, ruleId, MaintenanceDueStatus.NOT_DUE, false, false,
                    lastPerformedAt, null, meterType, null, null, meterInterval, null,
                    "No maintenance trigger configured");
        }

        boolean hasBlocked = configured.stream()
                .anyMatch(signal -> signal.status() == MaintenanceDueStatus.BLOCKED);
        boolean hasDue = configured.stream().anyMatch(this::isDue);
        if (hasBlocked && (effectiveTriggerPolicy == MaintenanceTriggerPolicy.ALL || !hasDue)) {
            String blockedExplanation = configured.stream()
                    .filter(signal -> signal.status() == MaintenanceDueStatus.BLOCKED)
                    .map(TriggerSignal::explanation)
                    .filter(text -> text != null && !text.isBlank())
                    .findFirst()
                    .orElse("Required maintenance planning data is missing");
            return dto(equipmentId, regulationId, ruleId, MaintenanceDueStatus.BLOCKED,
                    isDue(calendarSignal), isDue(meterSignal), lastPerformedAt, calendarSignal.nextDueAt(),
                    meterType, meterSignal.currentValue(), meterSignal.anchorValue(), meterInterval,
                    meterSignal.remaining(), blockedExplanation);
        }

        MaintenanceDueStatus status = combine(configured, effectiveTriggerPolicy);
        String explanation = configured.stream()
                .map(TriggerSignal::explanation)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElse(status.name());
        return dto(equipmentId, regulationId, ruleId, status, isDue(calendarSignal), isDue(meterSignal),
                lastPerformedAt, calendarSignal.nextDueAt(), meterType, meterSignal.currentValue(),
                meterSignal.anchorValue(), meterInterval, meterSignal.remaining(), explanation);
    }

    private TriggerSignal calendarSignal(MaintenanceCompletionAnchor anchor,
                                         PeriodicityUnit unit,
                                         int value,
                                         Integer toleranceDays,
                                         MaintenanceRecalculationPolicy recalculationPolicy) {
        if (unit == null || value <= 0) {
            return TriggerSignal.notConfigured();
        }
        if (anchor == null) {
            return TriggerSignal.configured(MaintenanceDueStatus.BLOCKED, null, null, null,
                    "No completion anchor for calendar trigger");
        }
        Instant base = recalculationPolicy == MaintenanceRecalculationPolicy.FROM_PLANNED_DUE
                && anchor.getPlannedDueAt() != null
                ? anchor.getPlannedDueAt()
                : anchor.getPerformedAt();
        if (base == null) {
            return TriggerSignal.configured(MaintenanceDueStatus.NOT_DUE, null, null, null,
                    "No calendar anchor date");
        }
        Instant nextDue = addPeriod(base, unit, value);
        Instant now = Instant.now();
        if (!now.isBefore(nextDue.plus(Math.max(0, toleranceDays == null ? 0 : toleranceDays), ChronoUnit.DAYS))) {
            return TriggerSignal.configured(MaintenanceDueStatus.OVERDUE, nextDue, null, null,
                    "Calendar trigger overdue");
        }
        if (!now.isBefore(nextDue)) {
            return TriggerSignal.configured(MaintenanceDueStatus.DUE, nextDue, null, null,
                    "Calendar trigger due");
        }
        long upcomingDays = Math.max(1, toleranceDays == null ? 0 : toleranceDays);
        if (!now.isBefore(nextDue.minus(upcomingDays, ChronoUnit.DAYS))) {
            return TriggerSignal.configured(MaintenanceDueStatus.UPCOMING, nextDue, null, null,
                    "Calendar trigger upcoming");
        }
        return TriggerSignal.configured(MaintenanceDueStatus.NOT_DUE, nextDue, null, null,
                "Calendar trigger not due");
    }

    private TriggerSignal meterSignal(UUID equipmentId,
                                      MaintenanceCompletionAnchor anchor,
                                      MeterType meterType,
                                      Double interval) {
        if (meterType == null || interval == null || interval <= 0) {
            return TriggerSignal.notConfigured();
        }
        EquipmentMeter meter = meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId).stream()
                .filter(candidate -> candidate.getMeterType() == meterType)
                .findFirst()
                .orElse(null);
        if (meter == null) {
            return TriggerSignal.configured(MaintenanceDueStatus.BLOCKED, null, null, null,
                    "Required active meter is missing: " + meterType);
        }
        double anchorValue;
        double elapsed;
        if (anchor == null) {
            anchorValue = 0.0;
            elapsed = Math.max(0.0, meter.getCurrentValue() - anchorValue);
        } else {
            anchorValue = anchorMeterValue(anchor, meterType).orElse(0.0);
            elapsed = Math.max(0.0, meter.getCurrentValue() - anchorValue);
        }
        double remaining = Math.max(0.0, interval - elapsed);
        if (elapsed > interval * (1.0 + UPCOMING_RATIO)) {
            return TriggerSignal.configured(MaintenanceDueStatus.OVERDUE, null, meter.getCurrentValue(), anchorValue,
                    "Meter trigger overdue", remaining);
        }
        if (elapsed >= interval) {
            return TriggerSignal.configured(MaintenanceDueStatus.DUE, null, meter.getCurrentValue(), anchorValue,
                    "Meter trigger due", remaining);
        }
        if (remaining <= interval * UPCOMING_RATIO) {
            return TriggerSignal.configured(MaintenanceDueStatus.UPCOMING, null, meter.getCurrentValue(), anchorValue,
                    "Meter trigger upcoming", remaining);
        }
        return TriggerSignal.configured(MaintenanceDueStatus.NOT_DUE, null, meter.getCurrentValue(), anchorValue,
                "Meter trigger not due", remaining);
    }

    private Optional<Double> anchorMeterValue(MaintenanceCompletionAnchor anchor, MeterType meterType) {
        if (anchor.getMeterSnapshots() == null || anchor.getMeterSnapshots().isBlank()) {
            return Optional.empty();
        }
        try {
            List<JsonNode> snapshots = objectMapper.readValue(anchor.getMeterSnapshots(), new TypeReference<>() {});
            return snapshots.stream()
                    .filter(snapshot -> meterType.name().equals(snapshot.path("meterType").asText(null)))
                    .map(snapshot -> snapshot.hasNonNull("value") ? snapshot.get("value").asDouble() : null)
                    .filter(value -> value != null)
                    .findFirst();
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Instant addPeriod(Instant base, PeriodicityUnit unit, int value) {
        return switch (unit) {
            case DAY -> base.plus(value, ChronoUnit.DAYS);
            case WEEK -> base.plus(value * 7L, ChronoUnit.DAYS);
            case MONTH -> base.plus(value * 30L, ChronoUnit.DAYS);
            case QUARTER -> base.plus(value * 90L, ChronoUnit.DAYS);
            case YEAR -> base.plus(value * 365L, ChronoUnit.DAYS);
            case HOUR -> base.plus(value, ChronoUnit.HOURS);
        };
    }

    private MaintenanceDueStatus combine(List<TriggerSignal> signals, MaintenanceTriggerPolicy policy) {
        if (policy == MaintenanceTriggerPolicy.ALL) {
            boolean allDue = signals.stream()
                    .allMatch(signal -> signal.status() == MaintenanceDueStatus.DUE
                            || signal.status() == MaintenanceDueStatus.OVERDUE);
            if (allDue) {
                return signals.stream().anyMatch(signal -> signal.status() == MaintenanceDueStatus.OVERDUE)
                        ? MaintenanceDueStatus.OVERDUE
                        : MaintenanceDueStatus.DUE;
            }
            return signals.stream().anyMatch(signal -> signal.status() == MaintenanceDueStatus.UPCOMING)
                    ? MaintenanceDueStatus.UPCOMING
                    : MaintenanceDueStatus.NOT_DUE;
        }
        if (signals.stream().anyMatch(signal -> signal.status() == MaintenanceDueStatus.OVERDUE)) {
            return MaintenanceDueStatus.OVERDUE;
        }
        if (signals.stream().anyMatch(signal -> signal.status() == MaintenanceDueStatus.DUE)) {
            return MaintenanceDueStatus.DUE;
        }
        if (signals.stream().anyMatch(signal -> signal.status() == MaintenanceDueStatus.UPCOMING)) {
            return MaintenanceDueStatus.UPCOMING;
        }
        return MaintenanceDueStatus.NOT_DUE;
    }

    private boolean isDue(TriggerSignal signal) {
        return signal.status() == MaintenanceDueStatus.DUE || signal.status() == MaintenanceDueStatus.OVERDUE;
    }

    private MaintenanceDueCalculationDto dto(UUID equipmentId,
                                             UUID regulationId,
                                             UUID ruleId,
                                             MaintenanceDueStatus status,
                                             boolean dueByCalendar,
                                             boolean dueByMeter,
                                             Instant lastPerformedAt,
                                             Instant nextDueAt,
                                             MeterType meterType,
                                             Double currentValue,
                                             Double anchorValue,
                                             Double interval,
                                             Double remaining,
                                             String explanation) {
        Double nextMeterDueValue = anchorValue == null || interval == null ? null : anchorValue + interval;
        return new MaintenanceDueCalculationDto(
                equipmentId,
                regulationId,
                ruleId,
                status,
                dueByCalendar,
                dueByMeter,
                lastPerformedAt,
                nextDueAt,
                nextDueAt,
                meterType,
                currentValue,
                currentValue,
                anchorValue,
                interval,
                nextMeterDueValue,
                remaining,
                remaining,
                explanation
        );
    }

    private record TriggerSignal(
            boolean configured,
            MaintenanceDueStatus status,
            Instant nextDueAt,
            Double currentValue,
            Double anchorValue,
            Double remaining,
            String explanation
    ) {
        static TriggerSignal notConfigured() {
            return new TriggerSignal(false, MaintenanceDueStatus.NOT_DUE, null, null, null, null, null);
        }

        static TriggerSignal configured(MaintenanceDueStatus status, Instant nextDueAt, Double currentValue,
                                        Double anchorValue, String explanation) {
            return configured(status, nextDueAt, currentValue, anchorValue, explanation, null);
        }

        static TriggerSignal configured(MaintenanceDueStatus status, Instant nextDueAt, Double currentValue,
                                        Double anchorValue, String explanation, Double remaining) {
            return new TriggerSignal(true, status, nextDueAt, currentValue, anchorValue, remaining, explanation);
        }
    }
}
