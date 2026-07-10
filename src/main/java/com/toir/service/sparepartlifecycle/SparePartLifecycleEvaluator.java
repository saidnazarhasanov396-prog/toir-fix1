package com.toir.service.sparepartlifecycle;

import com.toir.dto.sparepartlifecycle.AppliedLifeLimitSnapshot;
import com.toir.dto.sparepartlifecycle.CurrentMeterValue;
import com.toir.dto.sparepartlifecycle.SparePartLifeLimitEvaluation;
import com.toir.dto.sparepartlifecycle.SparePartLifecycleEvaluation;
import com.toir.dto.sparepartlifecycle.SparePartLifecycleEvaluationInput;
import com.toir.enums.sparepartlifecycle.SparePartCalendarUnit;
import com.toir.enums.sparepartlifecycle.SparePartLifeCombinationMode;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleEvaluationState;
import com.toir.exception.RestException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SparePartLifecycleEvaluator {

    public SparePartLifecycleEvaluation evaluate(SparePartLifecycleEvaluationInput input) {
        if (input == null || input.rule() == null || input.installedAt() == null || input.evaluatedAt() == null) {
            throw RestException.badRequest("LIFECYCLE_EVALUATION_INVALID: installation, rule and timestamps are required");
        }
        List<AppliedLifeLimitSnapshot> snapshots = input.rule().limits() == null
                ? List.of()
                : input.rule().limits().stream()
                        .sorted(Comparator.comparingInt(AppliedLifeLimitSnapshot::sequence))
                        .toList();
        Map<UUID, BigDecimal> baselines = input.meterBaselines() == null ? Map.of() : input.meterBaselines();
        Map<UUID, CurrentMeterValue> currentMeters = input.currentMeters() == null ? Map.of() : input.currentMeters();
        List<SparePartLifeLimitEvaluation> limits = new ArrayList<>(snapshots.size());
        Set<String> errors = new LinkedHashSet<>();
        for (AppliedLifeLimitSnapshot snapshot : snapshots) {
            SparePartLifeLimitEvaluation evaluation = switch (snapshot.limitKind()) {
                case CALENDAR -> evaluateCalendar(snapshot, input.installedAt(), input.evaluatedAt());
                case METER -> evaluateMeter(snapshot, baselines, currentMeters);
            };
            limits.add(evaluation);
            if (evaluation.errorCode() != null) {
                errors.add(evaluation.errorCode());
            }
        }
        Instant nextCalendarDueAt = limits.stream()
                .map(SparePartLifeLimitEvaluation::dueAt)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        SparePartLifecycleEvaluationState aggregate = aggregate(
                input.rule().combinationMode(),
                limits,
                !errors.isEmpty(),
                input.manualDue()
        );
        return new SparePartLifecycleEvaluation(
                input.installationId(),
                aggregate,
                input.rule().dueAction(),
                List.copyOf(limits),
                nextCalendarDueAt,
                List.copyOf(errors),
                input.evaluatedAt()
        );
    }

    private SparePartLifeLimitEvaluation evaluateCalendar(AppliedLifeLimitSnapshot limit,
                                                           Instant installedAt,
                                                           Instant evaluatedAt) {
        try {
            long limitUnits = limit.limitValue().longValueExact();
            long warningUnits = limit.warningBeforeValue() == null
                    ? 0
                    : limit.warningBeforeValue().longValueExact();
            Instant dueAt = addCalendar(installedAt, limit.calendarUnit(), limitUnits);
            Instant warningAt = addCalendar(installedAt, limit.calendarUnit(), limitUnits - warningUnits);
            SparePartLifecycleEvaluationState state = evaluatedAt.isAfter(dueAt)
                    ? SparePartLifecycleEvaluationState.OVERDUE
                    : evaluatedAt.equals(dueAt)
                    ? SparePartLifecycleEvaluationState.DUE
                    : !evaluatedAt.isBefore(warningAt)
                    ? SparePartLifecycleEvaluationState.WARNING
                    : SparePartLifecycleEvaluationState.OK;
            BigDecimal consumed = BigDecimal.valueOf(elapsedCalendarUnits(installedAt, evaluatedAt, limit.calendarUnit()));
            return new SparePartLifeLimitEvaluation(
                    limit.limitId(),
                    limit.limitKind(),
                    state,
                    consumed,
                    limit.limitValue().subtract(consumed),
                    limit.limitValue().subtract(BigDecimal.valueOf(warningUnits)),
                    dueAt,
                    null,
                    null,
                    null,
                    null
            );
        } catch (ArithmeticException | NullPointerException exception) {
            return error(limit, "CALENDAR_LIMIT_INVALID", null);
        }
    }

    private SparePartLifeLimitEvaluation evaluateMeter(AppliedLifeLimitSnapshot limit,
                                                        Map<UUID, BigDecimal> baselines,
                                                        Map<UUID, CurrentMeterValue> currentMeters) {
        UUID meterId = limit.equipmentMeterId();
        BigDecimal baseline = meterId == null ? null : baselines.get(meterId);
        if (baseline == null) {
            return error(limit, "METER_BASELINE_REQUIRED", null);
        }
        CurrentMeterValue current = currentMeters.get(meterId);
        if (current == null || current.value() == null) {
            return error(limit, "METER_REQUIRED", null);
        }
        if (!current.active()) {
            return error(limit, "METER_INACTIVE", current.value());
        }
        BigDecimal consumed;
        if (current.value().compareTo(baseline) >= 0) {
            consumed = current.value().subtract(baseline);
        } else if (current.rolloverValue() != null
                && current.rolloverValue().compareTo(baseline) >= 0
                && current.value().compareTo(current.rolloverValue()) < 0) {
            consumed = current.rolloverValue().subtract(baseline).add(current.value());
        } else {
            return error(limit, "METER_NEGATIVE_DELTA", current.value());
        }
        BigDecimal warningBefore = limit.warningBeforeValue() == null
                ? BigDecimal.ZERO
                : limit.warningBeforeValue();
        BigDecimal warningThreshold = limit.limitValue().subtract(warningBefore);
        int dueComparison = consumed.compareTo(limit.limitValue());
        SparePartLifecycleEvaluationState state = dueComparison > 0
                ? SparePartLifecycleEvaluationState.OVERDUE
                : dueComparison == 0
                ? SparePartLifecycleEvaluationState.DUE
                : consumed.compareTo(warningThreshold) >= 0
                ? SparePartLifecycleEvaluationState.WARNING
                : SparePartLifecycleEvaluationState.OK;
        return new SparePartLifeLimitEvaluation(
                limit.limitId(),
                limit.limitKind(),
                state,
                consumed,
                limit.limitValue().subtract(consumed),
                warningThreshold,
                null,
                meterId,
                limit.meterType(),
                current.value(),
                null
        );
    }

    private SparePartLifeLimitEvaluation error(AppliedLifeLimitSnapshot limit,
                                               String errorCode,
                                               BigDecimal currentValue) {
        return new SparePartLifeLimitEvaluation(
                limit.limitId(),
                limit.limitKind(),
                SparePartLifecycleEvaluationState.ERROR,
                null,
                null,
                null,
                null,
                limit.equipmentMeterId(),
                limit.meterType(),
                currentValue,
                errorCode
        );
    }

    private static SparePartLifecycleEvaluationState aggregate(SparePartLifeCombinationMode mode,
                                                               List<SparePartLifeLimitEvaluation> limits,
                                                               boolean hasErrors,
                                                               boolean manualDue) {
        if (hasErrors) {
            return SparePartLifecycleEvaluationState.ERROR;
        }
        if (mode == SparePartLifeCombinationMode.MANUAL) {
            if (manualDue) {
                return SparePartLifecycleEvaluationState.DUE;
            }
            return limits.stream().anyMatch(limit -> limit.state() != SparePartLifecycleEvaluationState.OK)
                    ? SparePartLifecycleEvaluationState.WARNING
                    : SparePartLifecycleEvaluationState.OK;
        }
        boolean anyWarning = limits.stream().anyMatch(limit -> limit.state() == SparePartLifecycleEvaluationState.WARNING);
        boolean anyDue = limits.stream().anyMatch(SparePartLifecycleEvaluator::isDueState);
        boolean anyOverdue = limits.stream().anyMatch(limit -> limit.state() == SparePartLifecycleEvaluationState.OVERDUE);
        if (mode == SparePartLifeCombinationMode.ANY) {
            if (anyOverdue) return SparePartLifecycleEvaluationState.OVERDUE;
            if (anyDue) return SparePartLifecycleEvaluationState.DUE;
            if (anyWarning) return SparePartLifecycleEvaluationState.WARNING;
            return SparePartLifecycleEvaluationState.OK;
        }
        boolean allDue = !limits.isEmpty() && limits.stream().allMatch(SparePartLifecycleEvaluator::isDueState);
        if (allDue) {
            return anyOverdue ? SparePartLifecycleEvaluationState.OVERDUE : SparePartLifecycleEvaluationState.DUE;
        }
        if (anyDue || anyWarning) {
            return SparePartLifecycleEvaluationState.WARNING;
        }
        return SparePartLifecycleEvaluationState.OK;
    }

    private static boolean isDueState(SparePartLifeLimitEvaluation limit) {
        return limit.state() == SparePartLifecycleEvaluationState.DUE
                || limit.state() == SparePartLifecycleEvaluationState.OVERDUE;
    }

    private static Instant addCalendar(Instant start, SparePartCalendarUnit unit, long amount) {
        ZonedDateTime zoned = start.atZone(ZoneOffset.UTC);
        return (switch (unit) {
            case DAY -> zoned.plusDays(amount);
            case MONTH -> zoned.plusMonths(amount);
            case YEAR -> zoned.plusYears(amount);
        }).toInstant();
    }

    private static long elapsedCalendarUnits(Instant start, Instant end, SparePartCalendarUnit unit) {
        ZonedDateTime from = start.atZone(ZoneOffset.UTC);
        ZonedDateTime to = end.atZone(ZoneOffset.UTC);
        return switch (unit) {
            case DAY -> ChronoUnit.DAYS.between(from, to);
            case MONTH -> ChronoUnit.MONTHS.between(from, to);
            case YEAR -> ChronoUnit.YEARS.between(from, to);
        };
    }
}
