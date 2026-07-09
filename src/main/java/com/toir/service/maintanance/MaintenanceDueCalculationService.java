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
import com.toir.enums.MaintenanceDueBaseSource;
import com.toir.enums.MaintenanceDueReasonCode;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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
        return calculate(equipmentId, regulation, null);
    }

    public MaintenanceDueCalculationDto calculate(UUID equipmentId, MaintenanceRegulation regulation, String lang) {
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
                regulation.getLeadMeterPercent(),
                MaintenanceDueReasonI18n.normalizeLang(lang)
        );
    }

    public MaintenanceDueCalculationDto calculate(UUID equipmentId, EquipmentMaintenanceRule rule) {
        return calculate(equipmentId, rule, null);
    }

    public MaintenanceDueCalculationDto calculate(UUID equipmentId, EquipmentMaintenanceRule rule, String lang) {
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
                null,
                MaintenanceDueReasonI18n.normalizeLang(lang)
        );
    }

    public MaintenanceDueCalculationDto calculate(EquipmentMaintenanceEffectiveRule rule) {
        return calculate(rule, null);
    }

    public MaintenanceDueCalculationDto calculate(EquipmentMaintenanceEffectiveRule rule, String lang) {
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
                rule.leadMeterPercent(),
                MaintenanceDueReasonI18n.normalizeLang(lang)
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
                                                  Double leadMeterPercent,
                                                  String lang) {
        MaintenanceTriggerPolicy effectiveTriggerPolicy = triggerPolicy == null ? MaintenanceTriggerPolicy.ANY : triggerPolicy;
        MaintenanceRecalculationPolicy effectiveRecalculationPolicy = recalculationPolicy == null
                ? MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION
                : recalculationPolicy;
        if (effectiveTriggerPolicy == MaintenanceTriggerPolicy.MANUAL) {
            return dto(equipmentId, regulationId, ruleId, MaintenanceDueStatus.NOT_DUE, false, false,
                    null, null, meterType, null, null, meterInterval, null,
                    Reason.of(MaintenanceDueReasonCode.MANUAL_TRIGGER_POLICY), lang);
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
                    Reason.of(MaintenanceDueReasonCode.NO_TRIGGER_CONFIGURED), lang);
        }

        CombinedSignal combined = combine(configured, effectiveTriggerPolicy);
        return dto(equipmentId, regulationId, ruleId, combined.status(), isActive(calendarSignal), isActive(meterSignal),
                lastPerformedAt, calendarSignal.nextDueAt(), meterType, meterSignal.currentValue(),
                meterSignal.anchorValue(), meterInterval, meterSignal.remaining(), combined.reason(),
                periodicityUnit, periodicityValue, toleranceDays, effectiveTriggerPolicy, calendarSignal.baseDate(),
                calendarSignal.baseSourceCode(), lang);
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
                    Reason.of(calendarBase.blockedReason()));
        }
        Instant base = calendarBase.base();
        if (base == null) {
            return TriggerSignal.configured(MaintenanceDueStatus.NOT_DUE, null, null, null,
                    Reason.of(MaintenanceDueReasonCode.NO_CALENDAR_ANCHOR), null, base, null);
        }
        Instant nextDue = addPeriod(base, unit, value);
        if (isDateBased(unit)) {
            return dateBasedCalendarSignal(calendarBase, nextDue, leadTimeDays);
        }

        Instant now = clock.instant();
        if (!now.isBefore(nextDue.plus(Math.max(0, toleranceDays == null ? 0 : toleranceDays), ChronoUnit.DAYS))) {
            return TriggerSignal.configured(MaintenanceDueStatus.OVERDUE, nextDue, null, null,
                    calendarReason(MaintenanceDueReasonCode.CALENDAR_OVERDUE, calendarBase), null, base, calendarBase.sourceCode());
        }
        if (!now.isBefore(nextDue)) {
            return TriggerSignal.configured(MaintenanceDueStatus.DUE, nextDue, null, null,
                    calendarReason(MaintenanceDueReasonCode.CALENDAR_DUE, calendarBase), null, base, calendarBase.sourceCode());
        }
        long upcomingDays = Math.max(1, leadTimeDays == null ? toleranceDays == null ? 0 : toleranceDays : leadTimeDays);
        if (!now.isBefore(nextDue.minus(upcomingDays, ChronoUnit.DAYS))) {
            return TriggerSignal.configured(MaintenanceDueStatus.UPCOMING, nextDue, null, null,
                    calendarReason(MaintenanceDueReasonCode.CALENDAR_UPCOMING, calendarBase), null, base, calendarBase.sourceCode());
        }
        return TriggerSignal.configured(MaintenanceDueStatus.NOT_DUE, nextDue, null, null,
                calendarReason(MaintenanceDueReasonCode.CALENDAR_NOT_DUE, calendarBase), null, base, calendarBase.sourceCode());
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
            return CalendarBase.available(base, MaintenanceDueBaseSource.COMPLETION_ANCHOR);
        }

        MaintenanceInitialSchedulePolicy policy = initialSchedulePolicy == null
                ? MaintenanceInitialSchedulePolicy.FROM_OPERATION_START
                : initialSchedulePolicy;
        if (policy == MaintenanceInitialSchedulePolicy.REQUIRE_INITIAL_ANCHOR) {
            return CalendarBase.blocked(MaintenanceDueReasonCode.REQUIRE_INITIAL_ANCHOR);
        }
        if (policy == MaintenanceInitialSchedulePolicy.BLOCKED) {
            return CalendarBase.blocked(MaintenanceDueReasonCode.NO_COMPLETION_ANCHOR);
        }
        if (policy == MaintenanceInitialSchedulePolicy.FROM_REGULATION_CREATED) {
            return initialScheduleBaseAt == null
                    ? CalendarBase.blocked(MaintenanceDueReasonCode.NO_COMPLETION_ANCHOR)
                    : CalendarBase.available(initialScheduleBaseAt, MaintenanceDueBaseSource.REGULATION_CREATED);
        }

        Optional<Equipment> foundEquipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId);
        Equipment equipment = foundEquipment == null ? null : foundEquipment.orElse(null);
        LocalDate operationStartDate = equipment == null ? null : equipment.getOperationStartDate();
        if (operationStartDate != null) {
            return CalendarBase.available(operationStartDate.atStartOfDay(clockZone()).toInstant(),
                    MaintenanceDueBaseSource.OPERATION_START);
        }
        return initialScheduleBaseAt == null
                ? CalendarBase.blocked(MaintenanceDueReasonCode.NO_COMPLETION_ANCHOR)
                : CalendarBase.available(initialScheduleBaseAt, MaintenanceDueBaseSource.REGULATION_CREATED);
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
                    Reason.of(MaintenanceDueReasonCode.MISSING_ACTIVE_METER, Map.of("meterType", meterType.name())));
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
                    Reason.of(MaintenanceDueReasonCode.METER_OVERDUE), remaining);
        }
        if (remainingComparison == 0) {
            return TriggerSignal.configured(MaintenanceDueStatus.DUE, null, meter.getCurrentValue(), anchorValue,
                    Reason.of(MaintenanceDueReasonCode.METER_DUE), remaining);
        }
        if (BigDecimal.valueOf(remaining).compareTo(BigDecimal.valueOf(interval * meterLeadRatio(leadMeterPercent))) <= 0) {
            return TriggerSignal.configured(MaintenanceDueStatus.UPCOMING, null, meter.getCurrentValue(), anchorValue,
                    Reason.of(MaintenanceDueReasonCode.METER_UPCOMING), remaining);
        }
        return TriggerSignal.configured(MaintenanceDueStatus.NOT_DUE, null, meter.getCurrentValue(), anchorValue,
                Reason.of(MaintenanceDueReasonCode.METER_NOT_DUE), remaining);
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
                    calendarReason(MaintenanceDueReasonCode.CALENDAR_OVERDUE, calendarBase), null, calendarBase.base(), calendarBase.sourceCode());
        }
        if (dueDate.isEqual(today)) {
            return TriggerSignal.configured(MaintenanceDueStatus.DUE, nextDue, null, null,
                    calendarReason(MaintenanceDueReasonCode.CALENDAR_DUE, calendarBase), null, calendarBase.base(), calendarBase.sourceCode());
        }
        long daysUntil = ChronoUnit.DAYS.between(today, dueDate);
        long upcomingDays = Math.max(0, leadTimeDays == null ? 0 : leadTimeDays);
        if (daysUntil <= upcomingDays) {
            return TriggerSignal.configured(MaintenanceDueStatus.UPCOMING, nextDue, null, null,
                    calendarReason(MaintenanceDueReasonCode.CALENDAR_UPCOMING, calendarBase), null, calendarBase.base(), calendarBase.sourceCode());
        }
        return TriggerSignal.configured(MaintenanceDueStatus.NOT_DUE, nextDue, null, null,
                calendarReason(MaintenanceDueReasonCode.CALENDAR_NOT_DUE, calendarBase), null, calendarBase.base(), calendarBase.sourceCode());
    }

    private boolean isDateBased(PeriodicityUnit unit) {
        return unit != PeriodicityUnit.HOUR;
    }

    private ZoneId clockZone() {
        return clock == null || clock.getZone() == null ? ZoneOffset.UTC : clock.getZone();
    }

    private Reason calendarReason(MaintenanceDueReasonCode code, CalendarBase calendarBase) {
        return new Reason(code, baseSourceParams(calendarBase.sourceCode()), List.of());
    }

    private Map<String, Object> baseSourceParams(MaintenanceDueBaseSource sourceCode) {
        return sourceCode == null ? Map.of() : Map.of("baseSource", sourceCode.name());
    }

    private CombinedSignal combine(List<TriggerSignal> signals, MaintenanceTriggerPolicy policy) {
        if (policy == MaintenanceTriggerPolicy.ALL) {
            Optional<TriggerSignal> blocked = signals.stream()
                    .filter(signal -> signal.status() == MaintenanceDueStatus.BLOCKED)
                    .findFirst();
            if (blocked.isPresent()) {
                return new CombinedSignal(MaintenanceDueStatus.BLOCKED, mergeReasons(signals));
            }
            boolean allDue = signals.stream().allMatch(this::isDueOrOverdue);
            if (allDue) {
                TriggerSignal dominant = dominantActive(signals);
                return new CombinedSignal(dominant.status(), mergeReasons(dominant, signals));
            }
            boolean allAtLeastUpcoming = signals.stream().allMatch(this::isActive);
            if (allAtLeastUpcoming) {
                return new CombinedSignal(MaintenanceDueStatus.UPCOMING,
                        new Reason(MaintenanceDueReasonCode.WAITING_ALL_UPCOMING, Map.of(), reasonsOf(signals)));
            }
            return new CombinedSignal(MaintenanceDueStatus.NOT_DUE,
                    new Reason(MaintenanceDueReasonCode.WAITING_ALL_NONE_DUE, Map.of(), reasonsOf(signals)));
        }

        List<TriggerSignal> active = signals.stream().filter(this::isActive).toList();
        if (!active.isEmpty()) {
            TriggerSignal dominant = dominantActive(active);
            return new CombinedSignal(dominant.status(), mergeReasons(dominant, signals));
        }
        Optional<TriggerSignal> blocked = signals.stream()
                .filter(signal -> signal.status() == MaintenanceDueStatus.BLOCKED)
                .findFirst();
        if (blocked.isPresent()) {
            return new CombinedSignal(MaintenanceDueStatus.BLOCKED, mergeReasons(signals));
        }
        return new CombinedSignal(MaintenanceDueStatus.NOT_DUE, mergeReasons(signals));
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

    private List<Reason> reasonsOf(List<TriggerSignal> signals) {
        return signals.stream()
                .map(TriggerSignal::reason)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private Reason mergeReasons(List<TriggerSignal> signals) {
        List<Reason> reasons = reasonsOf(signals);
        if (reasons.isEmpty()) {
            return null;
        }
        Reason primary = reasons.getFirst();
        List<Reason> supporting = reasons.size() > 1 ? reasons.subList(1, reasons.size()) : List.of();
        return new Reason(primary.code(), primary.params(), supporting);
    }

    private Reason mergeReasons(TriggerSignal dominant, List<TriggerSignal> signals) {
        List<Reason> rest = signals.stream()
                .filter(signal -> signal != dominant)
                .map(TriggerSignal::reason)
                .filter(Objects::nonNull)
                .filter(reason -> !reason.equals(dominant.reason()))
                .distinct()
                .toList();
        if (dominant.reason() == null) {
            return rest.isEmpty() ? null : new Reason(rest.getFirst().code(), rest.getFirst().params(), rest.subList(1, rest.size()));
        }
        return new Reason(dominant.reason().code(), dominant.reason().params(), rest);
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
                                             Reason reason,
                                             String lang) {
        return dto(equipmentId, regulationId, ruleId, status, dueByCalendar, dueByMeter, lastPerformedAt, nextDueAt,
                meterType, currentValue, anchorValue, interval, remaining, reason, null, 0, 0,
                MaintenanceTriggerPolicy.ANY, null, null, lang);
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
                                             Reason reason,
                                             PeriodicityUnit periodicityUnit,
                                             int periodicityValue,
                                             Integer toleranceDays,
                                             MaintenanceTriggerPolicy triggerPolicy,
                                             Instant calendarBaseDate,
                                             MaintenanceDueBaseSource calendarBaseSourceCode,
                                             String lang) {
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
                renderLegacyText(reason, lang),
                structuredExplanation(equipmentId, status, lastPerformedAt, nextDueAt, meterType, currentValue,
                        interval, remaining, reason, periodicityUnit, periodicityValue, toleranceDays,
                        triggerPolicy, calendarBaseDate, calendarBaseSourceCode, lang)
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
            Reason reason,
            PeriodicityUnit periodicityUnit,
            int periodicityValue,
            Integer toleranceDays,
            MaintenanceTriggerPolicy triggerPolicy,
            Instant calendarBaseDate,
            MaintenanceDueBaseSource calendarBaseSourceCode,
            String lang
    ) {
        String blockingCode = null;
        String blockingField = null;
        String fixLink = null;
        MaintenanceDueReasonCode code = reason == null ? null : reason.code();
        // A combined (policy=ALL) reason can carry the blocking cause in its supporting list rather
        // than as the primary code (e.g. calendar=NOT_DUE, meter=MISSING_ACTIVE_METER still blocks
        // the regulation) - scan the whole reason set, not just the primary one.
        if (status == MaintenanceDueStatus.BLOCKED && containsReasonCode(reason, MaintenanceDueReasonCode.MISSING_ACTIVE_METER)) {
            blockingCode = "MISSING_ACTIVE_METER";
            blockingField = meterType == null ? null : meterType.name();
            fixLink = "/equipment/%s/meters".formatted(equipmentId);
        } else if (status == MaintenanceDueStatus.BLOCKED
                && (containsReasonCode(reason, MaintenanceDueReasonCode.REQUIRE_INITIAL_ANCHOR)
                    || containsReasonCode(reason, MaintenanceDueReasonCode.NO_COMPLETION_ANCHOR))) {
            blockingCode = "MISSING_COMPLETION_ANCHOR";
            blockingField = "completionAnchor";
            fixLink = "/equipment/%s/maintenance".formatted(equipmentId);
        }
        String reasonKey = code == null ? null : "maintenanceDue.reasons." + code.name();
        List<String> supportingReasonKeys = reason == null ? List.of()
                : reason.supporting().stream().map(r -> "maintenanceDue.reasons." + r.code().name()).toList();
        List<Map<String, Object>> supportingReasonParams = reason == null ? List.of()
                : reason.supporting().stream().map(Reason::params).toList();
        return new MaintenanceDueStructuredExplanationDto(
                baseSource(lastPerformedAt, calendarBaseDate, calendarBaseSourceCode),
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
                renderLegacyText(reason, lang),
                blockingCode,
                blockingField,
                fixLink,
                code == null ? null : code.name(),
                reasonKey,
                reason == null ? Map.of() : reason.params(),
                supportingReasonKeys,
                supportingReasonParams
        );
    }

    private boolean containsReasonCode(Reason reason, MaintenanceDueReasonCode target) {
        if (reason == null) {
            return false;
        }
        if (reason.code() == target) {
            return true;
        }
        return reason.supporting().stream().anyMatch(supporting -> supporting.code() == target);
    }

    private String baseSource(Instant lastPerformedAt, Instant calendarBaseDate, MaintenanceDueBaseSource calendarBaseSourceCode) {
        if (lastPerformedAt != null) {
            return MaintenanceDueBaseSource.COMPLETION_ANCHOR.name();
        }
        if (calendarBaseDate != null && calendarBaseSourceCode != null) {
            return calendarBaseSourceCode.name();
        }
        return calendarBaseDate == null ? null : MaintenanceDueBaseSource.CALENDAR_BASE.name();
    }

    private String renderLegacyText(Reason reason, String lang) {
        if (reason == null) {
            return null;
        }
        if (lang != null) {
            List<MaintenanceDueReasonCode> supportingCodes = reason.supporting().stream().map(Reason::code).toList();
            List<Map<String, Object>> supportingParams = reason.supporting().stream().map(Reason::params).toList();
            return MaintenanceDueReasonI18n.renderFull(reason.code(), reason.params(), supportingCodes, supportingParams, lang);
        }
        String primary = renderReasonText(reason.code(), reason.params());
        if (reason.supporting().isEmpty()) {
            return primary;
        }
        String rest = reason.supporting().stream()
                .map(r -> renderReasonText(r.code(), r.params()))
                .filter(text -> text != null && !text.isBlank())
                .distinct()
                .collect(Collectors.joining("; "));
        return rest.isBlank() ? primary : primary + "; " + rest;
    }

    private String renderReasonText(MaintenanceDueReasonCode code, Map<String, Object> params) {
        return switch (code) {
            case MANUAL_TRIGGER_POLICY -> "Manual trigger policy";
            case NO_TRIGGER_CONFIGURED -> "No maintenance trigger configured";
            case NO_CALENDAR_ANCHOR -> "No calendar anchor date";
            case CALENDAR_OVERDUE -> "Calendar trigger overdue" + baseSourceSuffixText(params);
            case CALENDAR_DUE -> "Calendar trigger due" + baseSourceSuffixText(params);
            case CALENDAR_UPCOMING -> "Calendar trigger upcoming" + baseSourceSuffixText(params);
            case CALENDAR_NOT_DUE -> "Calendar trigger not due" + baseSourceSuffixText(params);
            case REQUIRE_INITIAL_ANCHOR -> "Initial completion anchor is required for this calendar regulation.";
            case NO_COMPLETION_ANCHOR -> "No completion anchor for calendar trigger.";
            case MISSING_ACTIVE_METER -> "Required active meter is missing: " + params.getOrDefault("meterType", "");
            case METER_OVERDUE -> "Meter trigger overdue";
            case METER_DUE -> "Meter trigger due";
            case METER_UPCOMING -> "Meter trigger upcoming";
            case METER_NOT_DUE -> "Meter trigger not due";
            case WAITING_ALL_UPCOMING -> "Waiting for all maintenance triggers to become due";
            case WAITING_ALL_NONE_DUE -> "Waiting for all maintenance triggers";
        };
    }

    private String baseSourceSuffixText(Map<String, Object> params) {
        Object baseSource = params == null ? null : params.get("baseSource");
        if (baseSource == null) {
            return "";
        }
        return switch (MaintenanceDueBaseSource.valueOf(baseSource.toString())) {
            case REGULATION_CREATED -> " from regulation created";
            case OPERATION_START -> " from operation start";
            default -> "";
        };
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

    /** Machine-readable reason for a trigger signal or combined status; renders to legacy English text via {@link #renderReasonText}. */
    private record Reason(MaintenanceDueReasonCode code, Map<String, Object> params, List<Reason> supporting) {
        static Reason of(MaintenanceDueReasonCode code) {
            return new Reason(code, Map.of(), List.of());
        }

        static Reason of(MaintenanceDueReasonCode code, Map<String, Object> params) {
            return new Reason(code, params, List.of());
        }
    }

    private record TriggerSignal(
            boolean configured,
            MaintenanceDueStatus status,
            Instant nextDueAt,
            Double currentValue,
            Double anchorValue,
            Double remaining,
            Reason reason,
            Instant baseDate,
            MaintenanceDueBaseSource baseSourceCode
    ) {
        static TriggerSignal notConfigured() {
            return new TriggerSignal(false, MaintenanceDueStatus.NOT_DUE, null, null, null, null, null, null, null);
        }

        static TriggerSignal configured(MaintenanceDueStatus status, Instant nextDueAt, Double currentValue,
                                        Double anchorValue, Reason reason) {
            return configured(status, nextDueAt, currentValue, anchorValue, reason, null, null, null);
        }

        static TriggerSignal configured(MaintenanceDueStatus status, Instant nextDueAt, Double currentValue,
                                        Double anchorValue, Reason reason, Double remaining) {
            return configured(status, nextDueAt, currentValue, anchorValue, reason, remaining, null, null);
        }

        static TriggerSignal configured(MaintenanceDueStatus status, Instant nextDueAt, Double currentValue,
                                        Double anchorValue, Reason reason, Double remaining, Instant baseDate,
                                        MaintenanceDueBaseSource baseSourceCode) {
            return new TriggerSignal(true, status, nextDueAt, currentValue, anchorValue, remaining, reason, baseDate, baseSourceCode);
        }
    }

    private record CombinedSignal(MaintenanceDueStatus status, Reason reason) {}

    private record CalendarBase(Instant base, boolean blocked, MaintenanceDueReasonCode blockedReason, MaintenanceDueBaseSource sourceCode) {
        static CalendarBase available(Instant base, MaintenanceDueBaseSource sourceCode) {
            return new CalendarBase(base, false, null, sourceCode);
        }

        static CalendarBase blocked(MaintenanceDueReasonCode reason) {
            return new CalendarBase(null, true, reason, null);
        }
    }
}
