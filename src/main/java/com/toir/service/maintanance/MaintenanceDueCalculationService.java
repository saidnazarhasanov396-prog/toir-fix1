package com.toir.service.maintanance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.maintenanceplanning.MaintenanceDueCalculationDto;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceCompletionAnchor;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceInitialSchedulePolicy;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.maintenance.MaintenanceCompletionAnchorRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    private final EquipmentRepository equipmentRepository;
    private final MaintenanceCompletionAnchorRepository anchorRepository;
    private final ObjectMapper objectMapper;
    private Clock clock = Clock.systemUTC();

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
                regulation.getRecalculationPolicy(),
                regulation.getInitialSchedulePolicy(),
                regulation.getCreatedAt(),
                regulation.getLeadTimeDays(),
                regulation.getLeadMeterPercent()
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
                rule.getRecalculationPolicy(),
                MaintenanceInitialSchedulePolicy.FROM_OPERATION_START,
                rule.getCreatedAt(),
                null,
                null
        );
    }

    public MaintenanceDueCalculationDto calculate(EquipmentMaintenanceEffectiveRule rule) {
        return calculate(
                rule.equipmentId(),
                rule.regulationId(),
                rule.equipmentMaintenanceRuleId(),
                rule.periodicityUnit(),
                rule.periodicityValue(),
                rule.toleranceDays(),
                rule.triggerMeterType(),
                rule.triggerMeterInterval(),
                rule.triggerPolicy(),
                rule.recalculationPolicy(),
                rule.initialSchedulePolicy(),
                rule.initialScheduleBaseAt(),
                rule.leadTimeDays(),
                rule.leadMeterPercent()
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
                                                  MaintenanceRecalculationPolicy recalculationPolicy,
                                                  MaintenanceInitialSchedulePolicy initialSchedulePolicy,
                                                  Instant initialScheduleBaseAt,
                                                  Integer leadTimeDays,
                                                  Double leadMeterPercent) {
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

        TriggerSignal calendarSignal = calendarSignal(equipmentId, anchor.orElse(null), periodicityUnit, periodicityValue,
                toleranceDays, leadTimeDays, effectiveRecalculationPolicy, initialSchedulePolicy, initialScheduleBaseAt);
        TriggerSignal meterSignal = meterSignal(equipmentId, anchor.orElse(null), meterType, meterInterval);

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

        CombinedSignal combined = combine(configured, effectiveTriggerPolicy);
        return dto(equipmentId, regulationId, ruleId, combined.status(), isActive(calendarSignal), isActive(meterSignal),
                lastPerformedAt, calendarSignal.nextDueAt(), meterType, meterSignal.currentValue(),
                meterSignal.anchorValue(), meterInterval, meterSignal.remaining(), combined.explanation());
    }

    private TriggerSignal calendarSignal(UUID equipmentId,
                                         MaintenanceCompletionAnchor anchor,
                                         PeriodicityUnit unit,
                                         int value,
                                         Integer toleranceDays,
                                         Integer leadTimeDays,
                                         MaintenanceRecalculationPolicy recalculationPolicy,
                                         MaintenanceInitialSchedulePolicy initialSchedulePolicy,
                                         Instant initialScheduleBaseAt) {
        if (unit == null || value <= 0) {
            return TriggerSignal.notConfigured();
        }
        CalendarBase calendarBase = calendarBase(equipmentId, anchor, recalculationPolicy,
                initialSchedulePolicy, initialScheduleBaseAt);
        if (calendarBase.blocked()) {
            return TriggerSignal.configured(MaintenanceDueStatus.BLOCKED, null, null, null,
                    calendarBase.explanation());
        }
        Instant base = calendarBase.base();
        if (base == null) {
            return TriggerSignal.configured(MaintenanceDueStatus.NOT_DUE, null, null, null,
                    "No calendar anchor date");
        }
        Instant nextDue = addPeriod(base, unit, value);
        Instant now = clock.instant();
        if (!now.isBefore(nextDue.plus(Math.max(0, toleranceDays == null ? 0 : toleranceDays), ChronoUnit.DAYS))) {
            return TriggerSignal.configured(MaintenanceDueStatus.OVERDUE, nextDue, null, null,
                    "Calendar trigger overdue" + calendarBase.explanationSuffix());
        }
        if (!now.isBefore(nextDue)) {
            return TriggerSignal.configured(MaintenanceDueStatus.DUE, nextDue, null, null,
                    "Calendar trigger due" + calendarBase.explanationSuffix());
        }
        long upcomingDays = Math.max(1, leadTimeDays == null ? toleranceDays == null ? 0 : toleranceDays : leadTimeDays);
        if (!now.isBefore(nextDue.minus(upcomingDays, ChronoUnit.DAYS))) {
            return TriggerSignal.configured(MaintenanceDueStatus.UPCOMING, nextDue, null, null,
                    "Calendar trigger upcoming" + calendarBase.explanationSuffix());
        }
        return TriggerSignal.configured(MaintenanceDueStatus.NOT_DUE, nextDue, null, null,
                "Calendar trigger not due" + calendarBase.explanationSuffix());
    }

    private CalendarBase calendarBase(UUID equipmentId,
                                      MaintenanceCompletionAnchor anchor,
                                      MaintenanceRecalculationPolicy recalculationPolicy,
                                      MaintenanceInitialSchedulePolicy initialSchedulePolicy,
                                      Instant initialScheduleBaseAt) {
        if (anchor != null) {
            Instant base = recalculationPolicy == MaintenanceRecalculationPolicy.FROM_PLANNED_DUE
                    && anchor.getPlannedDueAt() != null
                    ? anchor.getPlannedDueAt()
                    : anchor.getPerformedAt();
            return CalendarBase.available(base, "");
        }

        MaintenanceInitialSchedulePolicy policy = initialSchedulePolicy == null
                ? MaintenanceInitialSchedulePolicy.FROM_OPERATION_START
                : initialSchedulePolicy;
        if (policy == MaintenanceInitialSchedulePolicy.REQUIRE_INITIAL_ANCHOR) {
            return CalendarBase.blocked("Initial completion anchor is required for this calendar regulation.");
        }
        if (policy == MaintenanceInitialSchedulePolicy.BLOCKED) {
            return CalendarBase.blocked("No completion anchor for calendar trigger.");
        }
        if (policy == MaintenanceInitialSchedulePolicy.FROM_REGULATION_CREATED) {
            return initialScheduleBaseAt == null
                    ? CalendarBase.blocked("No completion anchor for calendar trigger.")
                    : CalendarBase.available(initialScheduleBaseAt, " from regulation created");
        }

        Optional<Equipment> foundEquipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId);
        Equipment equipment = foundEquipment == null ? null : foundEquipment.orElse(null);
        LocalDate operationStartDate = equipment == null ? null : equipment.getOperationStartDate();
        if (operationStartDate != null) {
            return CalendarBase.available(operationStartDate.atStartOfDay().toInstant(ZoneOffset.UTC),
                    " from operation start");
        }
        return initialScheduleBaseAt == null
                ? CalendarBase.blocked("No completion anchor for calendar trigger.")
                : CalendarBase.available(initialScheduleBaseAt, " from regulation created");
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
            case MONTH -> base.atZone(ZoneOffset.UTC).plusMonths(value).toInstant();
            case QUARTER -> base.atZone(ZoneOffset.UTC).plusMonths(value * 3L).toInstant();
            case YEAR -> base.atZone(ZoneOffset.UTC).plusYears(value).toInstant();
            case HOUR -> base.plus(value, ChronoUnit.HOURS);
        };
    }

    private CombinedSignal combine(List<TriggerSignal> signals, MaintenanceTriggerPolicy policy) {
        if (policy == MaintenanceTriggerPolicy.ALL) {
            Optional<TriggerSignal> blocked = signals.stream()
                    .filter(signal -> signal.status() == MaintenanceDueStatus.BLOCKED)
                    .findFirst();
            if (blocked.isPresent()) {
                return new CombinedSignal(MaintenanceDueStatus.BLOCKED, joinExplanations(signals));
            }
            boolean allActive = signals.stream().allMatch(this::isActive);
            if (allActive) {
                TriggerSignal dominant = dominantActive(signals);
                return new CombinedSignal(dominant.status(), joinExplanations(dominant, signals));
            }
            return new CombinedSignal(MaintenanceDueStatus.NOT_DUE,
                    "Waiting for all maintenance triggers; " + joinExplanations(signals));
        }

        List<TriggerSignal> active = signals.stream().filter(this::isActive).toList();
        if (!active.isEmpty()) {
            TriggerSignal dominant = dominantActive(active);
            return new CombinedSignal(dominant.status(), joinExplanations(dominant, signals));
        }
        Optional<TriggerSignal> blocked = signals.stream()
                .filter(signal -> signal.status() == MaintenanceDueStatus.BLOCKED)
                .findFirst();
        if (blocked.isPresent()) {
            return new CombinedSignal(MaintenanceDueStatus.BLOCKED, joinExplanations(signals));
        }
        return new CombinedSignal(MaintenanceDueStatus.NOT_DUE, joinExplanations(signals));
    }

    private boolean isActive(TriggerSignal signal) {
        return signal.status() == MaintenanceDueStatus.UPCOMING
                || signal.status() == MaintenanceDueStatus.DUE
                || signal.status() == MaintenanceDueStatus.OVERDUE;
    }

    private TriggerSignal dominantActive(List<TriggerSignal> signals) {
        return signals.stream()
                .max(java.util.Comparator.comparingInt(signal -> urgency(signal.status())))
                .orElse(signals.getFirst());
    }

    private int urgency(MaintenanceDueStatus status) {
        return switch (status) {
            case OVERDUE -> 4;
            case DUE -> 3;
            case UPCOMING -> 2;
            case NOT_DUE -> 1;
            case BLOCKED -> 0;
        };
    }

    private String joinExplanations(List<TriggerSignal> signals) {
        return signals.stream()
                .map(TriggerSignal::explanation)
                .filter(text -> text != null && !text.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private String joinExplanations(TriggerSignal dominant, List<TriggerSignal> signals) {
        String rest = signals.stream()
                .filter(signal -> signal != dominant)
                .map(TriggerSignal::explanation)
                .filter(text -> text != null && !text.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining("; "));
        if (dominant.explanation() == null || dominant.explanation().isBlank()) {
            return rest;
        }
        if (rest.isBlank()) {
            return dominant.explanation();
        }
        return dominant.explanation() + "; " + rest;
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

    private record CombinedSignal(MaintenanceDueStatus status, String explanation) {}

    private record CalendarBase(Instant base, boolean blocked, String explanation) {
        static CalendarBase available(Instant base, String explanationSuffix) {
            return new CalendarBase(base, false, explanationSuffix == null ? "" : explanationSuffix);
        }

        static CalendarBase blocked(String explanation) {
            return new CalendarBase(null, true, explanation);
        }

        String explanationSuffix() {
            return blocked || explanation == null || explanation.isBlank() ? "" : explanation;
        }
    }
}
