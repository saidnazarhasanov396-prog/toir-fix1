package com.toir.service.maintanance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.maintenanceplanning.MaintenanceDueCalculationDto;
import com.toir.dto.maintenanceplanning.MaintenanceDueStructuredExplanationDto;
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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
        TriggerSignal meterSignal = meterSignal(equipmentId, anchor.orElse(null), meterType, meterInterval, leadMeterPercent);

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
                meterSignal.anchorValue(), meterInterval, meterSignal.remaining(), combined.explanation(),
                periodicityUnit, periodicityValue, toleranceDays, effectiveTriggerPolicy, calendarSignal.baseDate());
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
                    "No calendar anchor date", null, base);
        }
        Instant nextDue = addPeriod(base, unit, value);
        if (isDateBased(unit)) {
            return dateBasedCalendarSignal(calendarBase, nextDue, leadTimeDays);
        }

        Instant now = clock.instant();
        if (!now.isBefore(nextDue.plus(Math.max(0, toleranceDays == null ? 0 : toleranceDays), ChronoUnit.DAYS))) {
            return TriggerSignal.configured(MaintenanceDueStatus.OVERDUE, nextDue, null, null,
                    "Calendar trigger overdue" + calendarBase.explanationSuffix(), null, base);
        }
        if (!now.isBefore(nextDue)) {
            return TriggerSignal.configured(MaintenanceDueStatus.DUE, nextDue, null, null,
                    "Calendar trigger due" + calendarBase.explanationSuffix(), null, base);
        }
        long upcomingDays = Math.max(1, leadTimeDays == null ? toleranceDays == null ? 0 : toleranceDays : leadTimeDays);
        if (!now.isBefore(nextDue.minus(upcomingDays, ChronoUnit.DAYS))) {
            return TriggerSignal.configured(MaintenanceDueStatus.UPCOMING, nextDue, null, null,
                    "Calendar trigger upcoming" + calendarBase.explanationSuffix(), null, base);
        }
        return TriggerSignal.configured(MaintenanceDueStatus.NOT_DUE, nextDue, null, null,
                "Calendar trigger not due" + calendarBase.explanationSuffix(), null, base);
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
            return CalendarBase.available(operationStartDate.atStartOfDay(clockZone()).toInstant(),
                    " from operation start");
        }
        return initialScheduleBaseAt == null
                ? CalendarBase.blocked("No completion anchor for calendar trigger.")
                : CalendarBase.available(initialScheduleBaseAt, " from regulation created");
    }

    private TriggerSignal meterSignal(UUID equipmentId,
                                      MaintenanceCompletionAnchor anchor,
                                      MeterType meterType,
                                      Double interval,
                                      Double leadMeterPercent) {
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
        if (anchor == null) {
            anchorValue = 0.0;
        } else {
            anchorValue = anchorMeterValue(anchor, meterType).orElse(0.0);
        }
        double dueValue = meterDueValue(anchorValue, interval, meter.getCurrentValue());
        double remaining = dueValue - meter.getCurrentValue();
        int remainingComparison = BigDecimal.valueOf(remaining).compareTo(BigDecimal.ZERO);
        if (remainingComparison < 0) {
            return TriggerSignal.configured(MaintenanceDueStatus.OVERDUE, null, meter.getCurrentValue(), anchorValue,
                    "Meter trigger overdue", remaining);
        }
        if (remainingComparison == 0) {
            return TriggerSignal.configured(MaintenanceDueStatus.DUE, null, meter.getCurrentValue(), anchorValue,
                    "Meter trigger due", remaining);
        }
        if (BigDecimal.valueOf(remaining).compareTo(BigDecimal.valueOf(interval * meterLeadRatio(leadMeterPercent))) <= 0) {
            return TriggerSignal.configured(MaintenanceDueStatus.UPCOMING, null, meter.getCurrentValue(), anchorValue,
                    "Meter trigger upcoming", remaining);
        }
        return TriggerSignal.configured(MaintenanceDueStatus.NOT_DUE, null, meter.getCurrentValue(), anchorValue,
                "Meter trigger not due", remaining);
    }

    private double meterDueValue(double anchorValue, double interval, double currentValue) {
        double elapsed = Math.max(0.0, currentValue - anchorValue);
        if (elapsed < interval) {
            return anchorValue + interval;
        }
        double cycleCount = Math.floor(elapsed / interval);
        return anchorValue + cycleCount * interval;
    }

    private double meterLeadRatio(Double leadMeterPercent) {
        if (leadMeterPercent == null) {
            return UPCOMING_RATIO;
        }
        double value = Math.max(0.0, leadMeterPercent);
        return value > 1.0 ? value / 100.0 : value;
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
        ZoneId zone = clockZone();
        return switch (unit) {
            case DAY -> base.atZone(zone).plusDays(value).toInstant();
            case WEEK -> base.atZone(zone).plusWeeks(value).toInstant();
            case MONTH -> base.atZone(zone).plusMonths(value).toInstant();
            case QUARTER -> base.atZone(zone).plusMonths(value * 3L).toInstant();
            case YEAR -> base.atZone(zone).plusYears(value).toInstant();
            case HOUR -> base.plus(value, ChronoUnit.HOURS);
        };
    }

    private TriggerSignal dateBasedCalendarSignal(CalendarBase calendarBase,
                                                  Instant nextDue,
                                                  Integer leadTimeDays) {
        LocalDate today = LocalDate.now(clock);
        LocalDate dueDate = nextDue.atZone(clockZone()).toLocalDate();
        if (dueDate.isBefore(today)) {
            return TriggerSignal.configured(MaintenanceDueStatus.OVERDUE, nextDue, null, null,
                    "Calendar trigger overdue" + calendarBase.explanationSuffix(), null, calendarBase.base());
        }
        if (dueDate.isEqual(today)) {
            return TriggerSignal.configured(MaintenanceDueStatus.DUE, nextDue, null, null,
                    "Calendar trigger due" + calendarBase.explanationSuffix(), null, calendarBase.base());
        }
        long daysUntil = ChronoUnit.DAYS.between(today, dueDate);
        long upcomingDays = Math.max(0, leadTimeDays == null ? 0 : leadTimeDays);
        if (daysUntil <= upcomingDays) {
            return TriggerSignal.configured(MaintenanceDueStatus.UPCOMING, nextDue, null, null,
                    "Calendar trigger upcoming" + calendarBase.explanationSuffix(), null, calendarBase.base());
        }
        return TriggerSignal.configured(MaintenanceDueStatus.NOT_DUE, nextDue, null, null,
                "Calendar trigger not due" + calendarBase.explanationSuffix(), null, calendarBase.base());
    }

    private boolean isDateBased(PeriodicityUnit unit) {
        return unit != PeriodicityUnit.HOUR;
    }

    private ZoneId clockZone() {
        return clock == null || clock.getZone() == null ? ZoneOffset.UTC : clock.getZone();
    }

    private CombinedSignal combine(List<TriggerSignal> signals, MaintenanceTriggerPolicy policy) {
        if (policy == MaintenanceTriggerPolicy.ALL) {
            Optional<TriggerSignal> blocked = signals.stream()
                    .filter(signal -> signal.status() == MaintenanceDueStatus.BLOCKED)
                    .findFirst();
            if (blocked.isPresent()) {
                return new CombinedSignal(MaintenanceDueStatus.BLOCKED, joinExplanations(signals));
            }
            boolean allDue = signals.stream().allMatch(this::isDueOrOverdue);
            if (allDue) {
                TriggerSignal dominant = dominantActive(signals);
                return new CombinedSignal(dominant.status(), joinExplanations(dominant, signals));
            }
            boolean allAtLeastUpcoming = signals.stream().allMatch(this::isActive);
            if (allAtLeastUpcoming) {
                return new CombinedSignal(MaintenanceDueStatus.UPCOMING,
                        "Waiting for all maintenance triggers to become due; " + joinExplanations(signals));
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

    private boolean isDueOrOverdue(TriggerSignal signal) {
        return signal.status() == MaintenanceDueStatus.DUE
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
        return dto(equipmentId, regulationId, ruleId, status, dueByCalendar, dueByMeter, lastPerformedAt, nextDueAt,
                meterType, currentValue, anchorValue, interval, remaining, explanation, null, 0, 0,
                MaintenanceTriggerPolicy.ANY, null);
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
                                             String explanation,
                                             PeriodicityUnit periodicityUnit,
                                             int periodicityValue,
                                             Integer toleranceDays,
                                             MaintenanceTriggerPolicy triggerPolicy,
                                             Instant calendarBaseDate) {
        Double nextMeterDueValue = null;
        if (currentValue != null && remaining != null) {
            nextMeterDueValue = currentValue + remaining;
        } else if (anchorValue != null && interval != null) {
            nextMeterDueValue = anchorValue + interval;
        }
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
                explanation,
                structuredExplanation(equipmentId, status, lastPerformedAt, nextDueAt, meterType, currentValue,
                        interval, remaining, explanation, periodicityUnit, periodicityValue, toleranceDays,
                        triggerPolicy, calendarBaseDate)
        );
    }

    private MaintenanceDueStructuredExplanationDto structuredExplanation(
            UUID equipmentId,
            MaintenanceDueStatus status,
            Instant lastPerformedAt,
            Instant nextDueAt,
            MeterType meterType,
            Double currentValue,
            Double interval,
            Double remaining,
            String explanation,
            PeriodicityUnit periodicityUnit,
            int periodicityValue,
            Integer toleranceDays,
            MaintenanceTriggerPolicy triggerPolicy,
            Instant calendarBaseDate
    ) {
        String blockingCode = null;
        String blockingField = null;
        String fixLink = null;
        if (status == MaintenanceDueStatus.BLOCKED && meterType != null
                && explanation != null && explanation.toLowerCase(java.util.Locale.ROOT).contains("meter")) {
            blockingCode = "MISSING_ACTIVE_METER";
            blockingField = meterType.name();
            fixLink = "/equipment/%s/meters".formatted(equipmentId);
        } else if (status == MaintenanceDueStatus.BLOCKED
                && explanation != null && explanation.toLowerCase(java.util.Locale.ROOT).contains("anchor")) {
            blockingCode = "MISSING_COMPLETION_ANCHOR";
            blockingField = "completionAnchor";
            fixLink = "/equipment/%s/maintenance".formatted(equipmentId);
        }
        return new MaintenanceDueStructuredExplanationDto(
                baseSource(lastPerformedAt, calendarBaseDate, explanation),
                calendarBaseDate == null ? lastPerformedAt : calendarBaseDate,
                lastPerformedAt,
                meterType,
                currentValue,
                interval,
                remaining,
                intervalDays(periodicityUnit, periodicityValue),
                intervalMonths(periodicityUnit, periodicityValue),
                Math.max(0, toleranceDays == null ? 0 : toleranceDays),
                triggerPolicy,
                explanation,
                blockingCode,
                blockingField,
                fixLink
        );
    }

    private String baseSource(Instant lastPerformedAt, Instant calendarBaseDate, String explanation) {
        if (lastPerformedAt != null) {
            return "COMPLETION_ANCHOR";
        }
        if (calendarBaseDate != null && explanation != null
                && explanation.toLowerCase(java.util.Locale.ROOT).contains("operation start")) {
            return "OPERATION_START";
        }
        if (calendarBaseDate != null && explanation != null
                && explanation.toLowerCase(java.util.Locale.ROOT).contains("regulation created")) {
            return "REGULATION_CREATED";
        }
        return calendarBaseDate == null ? null : "CALENDAR_BASE";
    }

    private Integer intervalDays(PeriodicityUnit unit, int value) {
        if (unit == null || value <= 0) {
            return null;
        }
        return switch (unit) {
            case DAY -> value;
            case WEEK -> value * 7;
            default -> null;
        };
    }

    private Integer intervalMonths(PeriodicityUnit unit, int value) {
        if (unit == null || value <= 0) {
            return null;
        }
        return switch (unit) {
            case MONTH -> value;
            case QUARTER -> value * 3;
            case YEAR -> value * 12;
            default -> null;
        };
    }

    private record TriggerSignal(
            boolean configured,
            MaintenanceDueStatus status,
            Instant nextDueAt,
            Double currentValue,
            Double anchorValue,
            Double remaining,
            String explanation,
            Instant baseDate
    ) {
        static TriggerSignal notConfigured() {
            return new TriggerSignal(false, MaintenanceDueStatus.NOT_DUE, null, null, null, null, null, null);
        }

        static TriggerSignal configured(MaintenanceDueStatus status, Instant nextDueAt, Double currentValue,
                                        Double anchorValue, String explanation) {
            return configured(status, nextDueAt, currentValue, anchorValue, explanation, null);
        }

        static TriggerSignal configured(MaintenanceDueStatus status, Instant nextDueAt, Double currentValue,
                                        Double anchorValue, String explanation, Double remaining) {
            return configured(status, nextDueAt, currentValue, anchorValue, explanation, remaining, null);
        }

        static TriggerSignal configured(MaintenanceDueStatus status, Instant nextDueAt, Double currentValue,
                                        Double anchorValue, String explanation, Double remaining, Instant baseDate) {
            return new TriggerSignal(true, status, nextDueAt, currentValue, anchorValue, remaining, explanation, baseDate);
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
