package com.toir.service.maintanance;

import com.toir.dto.maintenanceplanning.MaintenanceDueCalculationDto;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewItem;
import com.toir.dto.maintenanceschedule.MaintenanceScheduleOption;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewRequest;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewResponse;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewSummary;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.MaintenanceScheduleAnchorMode;
import com.toir.enums.MaintenanceScheduleAnchorSource;
import com.toir.enums.MaintenanceScheduleScopeType;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.PeriodicityUnit;
import com.toir.exception.RestException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceScheduleService {

    private static final String MISSING_ACTIVE_METER = "MISSING_ACTIVE_METER";
    private static final long MAX_HORIZON_DAYS = 366;
    private static final int MAX_SCOPE_IDS = 1_000;
    private static final int MAX_OCCURRENCES = 100_000;

    private final EquipmentMaintenanceEffectiveRuleResolver ruleResolver;
    private final MaintenanceDueCalculationService dueCalculationService;
    private final MaintenanceScheduleEligibilitySelector eligibilitySelector;
    private final ZoneId zoneId;

    @Autowired
    public MaintenanceScheduleService(
            EquipmentMaintenanceEffectiveRuleResolver ruleResolver,
            MaintenanceDueCalculationService dueCalculationService,
            MaintenanceScheduleEligibilitySelector eligibilitySelector
    ) {
        this(ruleResolver, dueCalculationService, eligibilitySelector, ZoneId.systemDefault());
    }

    MaintenanceScheduleService(
            EquipmentMaintenanceEffectiveRuleResolver ruleResolver,
            MaintenanceDueCalculationService dueCalculationService,
            MaintenanceScheduleEligibilitySelector eligibilitySelector,
            ZoneId zoneId
    ) {
        this.ruleResolver = ruleResolver;
        this.dueCalculationService = dueCalculationService;
        this.eligibilitySelector = eligibilitySelector;
        this.zoneId = zoneId;
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceScheduleOption> options(
            MaintenanceScheduleScopeType scopeType,
            UUID departmentId,
            String search,
            int page,
            int size
    ) {
        if (scopeType == null) {
            throw RestException.badRequest("scopeType is required");
        }
        if (page < 0) {
            throw RestException.badRequest("page must be zero or greater");
        }
        if (size < 1 || size > 100) {
            throw RestException.badRequest("size must be between 1 and 100");
        }
        return eligibilitySelector.findOptions(
                scopeType,
                departmentId,
                search,
                PageRequest.of(page, size)
        );
    }

    @Transactional(readOnly = true)
    public MaintenanceSchedulePreviewResponse preview(MaintenanceSchedulePreviewRequest request) {
        validate(request);
        List<Equipment> equipment = eligibilitySelector.selectForPreview(request);
        List<MaintenanceSchedulePreviewItem> items = new ArrayList<>();
        Set<RuleSignature> missingMeters = new HashSet<>();
        Set<UUID> matchedEquipment = new HashSet<>();

        for (Equipment item : equipment) {
            for (EquipmentMaintenanceEffectiveRule rule : ruleResolver.resolveApplicable(item.getId())) {
                MaintenanceDueCalculationDto due = dueCalculationService.calculate(rule);
                if (hasMissingMeter(due)) {
                    missingMeters.add(new RuleSignature(
                            item.getId(), rule.regulationId(), rule.equipmentMaintenanceRuleId()));
                }
                List<LocalDate> dates = occurrenceDates(request, rule, due);
                if (!dates.isEmpty()) {
                    matchedEquipment.add(item.getId());
                }
                for (LocalDate plannedDate : dates) {
                    if (items.size() >= MAX_OCCURRENCES) {
                        throw RestException.badRequest(
                                "Maintenance schedule exceeds maximum occurrence count: " + MAX_OCCURRENCES);
                    }
                    items.add(toItem(item, rule, plannedDate, request.anchorMode()));
                }
            }
        }
        items.sort(Comparator
                .comparing(MaintenanceSchedulePreviewItem::equipmentCode,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(MaintenanceSchedulePreviewItem::equipmentId)
                .thenComparing(MaintenanceSchedulePreviewItem::plannedDate)
                .thenComparing(MaintenanceSchedulePreviewItem::regulationName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(item -> Objects.toString(item.regulationId(), ""))
                .thenComparing(item -> Objects.toString(item.equipmentMaintenanceRuleId(), "")));

        return new MaintenanceSchedulePreviewResponse(
                List.copyOf(items),
                new MaintenanceSchedulePreviewSummary(
                        matchedEquipment.size(),
                        items.size(),
                        missingMeters.size(),
                        equipment.size() - matchedEquipment.size()
                )
        );
    }

    private List<LocalDate> occurrenceDates(
            MaintenanceSchedulePreviewRequest request,
            EquipmentMaintenanceEffectiveRule rule,
            MaintenanceDueCalculationDto due
    ) {
        if (rule.triggerPolicy() == MaintenanceTriggerPolicy.MANUAL
                || rule.periodicityUnit() == PeriodicityUnit.HOUR
                || rule.periodicityUnit() == null
                || rule.periodicityValue() <= 0) {
            return List.of();
        }
        LocalDate cursor;
        if (request.anchorMode() == MaintenanceScheduleAnchorMode.RESET_TO_PLAN_START) {
            cursor = advance(request.fromDate(), rule.periodicityUnit(), rule.periodicityValue());
        } else {
            if (due == null || due.nextCalendarDueAt() == null) {
                return List.of();
            }
            cursor = due.nextCalendarDueAt().atZone(zoneId).toLocalDate();
            while (cursor.isBefore(request.fromDate())) {
                cursor = advance(cursor, rule.periodicityUnit(), rule.periodicityValue());
            }
        }

        List<LocalDate> dates = new ArrayList<>();
        while (!cursor.isAfter(request.toDate())) {
            dates.add(cursor);
            cursor = advance(cursor, rule.periodicityUnit(), rule.periodicityValue());
        }
        return dates;
    }

    private LocalDate advance(LocalDate date, PeriodicityUnit unit, int value) {
        return switch (unit) {
            case DAY -> date.plusDays(value);
            case WEEK -> date.plusWeeks(value);
            case MONTH -> date.plusMonths(value);
            case QUARTER -> date.plusMonths(3L * value);
            case YEAR -> date.plusYears(value);
            case HOUR -> throw new IllegalArgumentException("HOUR is not a calendar interval");
        };
    }

    private MaintenanceSchedulePreviewItem toItem(
            Equipment equipment,
            EquipmentMaintenanceEffectiveRule rule,
            LocalDate plannedDate,
            MaintenanceScheduleAnchorMode anchorMode
    ) {
        return new MaintenanceSchedulePreviewItem(
                equipment.getId(),
                equipment.getCode(),
                equipment.getName(),
                rule.regulationId(),
                rule.equipmentMaintenanceRuleId(),
                rule.name(),
                rule.maintenanceKind(),
                rule.periodicityUnit(),
                rule.periodicityValue(),
                plannedDate,
                anchorMode == MaintenanceScheduleAnchorMode.CURRENT
                        ? MaintenanceScheduleAnchorSource.EXISTING_DUE_DATE
                        : MaintenanceScheduleAnchorSource.PLAN_START,
                rule.normativeLaborHours(),
                rule.requiresShutdown()
        );
    }

    private boolean hasMissingMeter(MaintenanceDueCalculationDto due) {
        if (due == null || due.structuredExplanation() == null) {
            return false;
        }
        var explanation = due.structuredExplanation();
        return MISSING_ACTIVE_METER.equals(explanation.blockingCode())
                || MISSING_ACTIVE_METER.equals(explanation.reasonCode())
                || (explanation.supportingReasonKeys() != null
                && explanation.supportingReasonKeys().stream()
                .anyMatch(key -> key != null && key.endsWith("." + MISSING_ACTIVE_METER)));
    }

    private void validate(MaintenanceSchedulePreviewRequest request) {
        if (request == null) {
            throw RestException.badRequest("Maintenance schedule request is required");
        }
        if (request.fromDate() == null || request.toDate() == null) {
            throw RestException.badRequest("fromDate and toDate are required");
        }
        if (request.fromDate().isAfter(request.toDate())) {
            throw RestException.badRequest("fromDate must be on or before toDate");
        }
        if (ChronoUnit.DAYS.between(request.fromDate(), request.toDate()) > MAX_HORIZON_DAYS) {
            throw RestException.badRequest(
                    "Maintenance schedule preview is limited to an annual horizon");
        }
        if (request.scopeType() == null) {
            throw RestException.badRequest("scopeType is required");
        }
        if (request.anchorMode() == null) {
            throw RestException.badRequest("anchorMode is required");
        }
        if (request.scopeType() == MaintenanceScheduleScopeType.EQUIPMENT) {
            requireIds("equipmentIds", request.equipmentIds());
            rejectIds("equipmentTypeIds", request.equipmentTypeIds());
        } else {
            requireIds("equipmentTypeIds", request.equipmentTypeIds());
            rejectIds("equipmentIds", request.equipmentIds());
        }
    }

    private void requireIds(String field, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            throw RestException.badRequest(field + " is required for selected scopeType");
        }
        if (ids.stream().anyMatch(Objects::isNull)) {
            throw RestException.badRequest(field + " must not contain null values");
        }
        if (ids.size() > MAX_SCOPE_IDS) {
            throw RestException.badRequest(field + " must contain at most " + MAX_SCOPE_IDS + " values");
        }
        if (new LinkedHashSet<>(ids).size() != ids.size()) {
            throw RestException.badRequest(field + " must not contain duplicate values");
        }
    }

    private void rejectIds(String field, List<UUID> ids) {
        if (ids != null && !ids.isEmpty()) {
            throw RestException.badRequest(field + " is not allowed for selected scopeType");
        }
    }

    private record RuleSignature(
            UUID equipmentId,
            UUID regulationId,
            UUID equipmentMaintenanceRuleId
    ) {}
}
