package com.toir.service.pprcalendar;

import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarEquipment;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarEquipmentRow;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarExcludedDiagnostics;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarFilter;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarNamedRef;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarOccurrence;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarPageMetadata;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarPlacementBasis;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarPlanSummary;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarResponse;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarSpanMode;
import com.toir.entity.PprPlan;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.pprcalendar.PprEquipmentCalendarQueryRepository;
import com.toir.repository.pprcalendar.PprEquipmentCalendarQueryRepository.CalendarDiagnostics;
import com.toir.repository.pprcalendar.PprEquipmentCalendarQueryRepository.CalendarEquipment;
import com.toir.repository.pprcalendar.PprEquipmentCalendarQueryRepository.CalendarOccurrence;
import com.toir.repository.pprcalendar.PprEquipmentCalendarQueryRepository.CalendarQuery;
import com.toir.repository.pprcalendar.PprEquipmentCalendarQueryRepository.EquipmentIdPage;
import com.toir.security.ScopeAccessService;
import com.toir.service.PprPlanVisibilityPolicy;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PprEquipmentCalendarService {

    private final PprPlanRepository planRepository;
    private final PprPlanVisibilityPolicy visibilityPolicy;
    private final ScopeAccessService scopeAccessService;
    private final PprEquipmentCalendarAuthoritativeSourceResolver sourceResolver;
    private final PprEquipmentCalendarQueryRepository queryRepository;
    private final MaintenanceKindDisplayResolver displayResolver;

    public PprEquipmentCalendarService(
            PprPlanRepository planRepository,
            PprPlanVisibilityPolicy visibilityPolicy,
            ScopeAccessService scopeAccessService,
            PprEquipmentCalendarAuthoritativeSourceResolver sourceResolver,
            PprEquipmentCalendarQueryRepository queryRepository,
            MaintenanceKindDisplayResolver displayResolver) {
        this.planRepository = planRepository;
        this.visibilityPolicy = visibilityPolicy;
        this.scopeAccessService = scopeAccessService;
        this.sourceResolver = sourceResolver;
        this.queryRepository = queryRepository;
        this.displayResolver = displayResolver;
    }

    @Transactional(readOnly = true)
    public PprEquipmentCalendarResponse getCalendar(
            UUID planId,
            PprEquipmentCalendarFilter filter) {
        Objects.requireNonNull(planId, "planId is required");
        Objects.requireNonNull(filter, "filter is required");
        PprPlan plan = planRepository.findByIdAndIsDeletedFalse(planId)
                .orElseThrow(this::planUnavailable);
        if (!visibilityPolicy.isVisible(plan.getStatus())) {
            throw planUnavailable();
        }

        UUID scopeDepartmentId = authorizePlanScope(plan);
        validateYearOverlap(plan, filter.year());
        PprEquipmentCalendarSourceSelection source = sourceResolver.resolve(plan);
        CalendarQuery query = new CalendarQuery(
                plan.getId(),
                plan.getDepartmentId(),
                scopeDepartmentId,
                plan.getStartDate(),
                plan.getEndDate(),
                source.authoritativeSource(),
                source.calculationRevision(),
                filter);

        EquipmentIdPage equipmentPage = queryRepository.findEquipmentPage(query);
        List<UUID> equipmentIds = equipmentPage.equipmentIds();
        List<CalendarEquipment> metadata = equipmentIds.isEmpty()
                ? List.of()
                : queryRepository.findEquipmentMetadata(equipmentIds);
        List<CalendarOccurrence> occurrences = equipmentIds.isEmpty()
                ? List.of()
                : queryRepository.findOccurrences(query, equipmentIds);
        CalendarDiagnostics diagnostics = queryRepository.findDiagnostics(query);

        Map<UUID, CalendarEquipment> metadataById = new HashMap<>();
        metadata.forEach(item -> metadataById.put(item.id(), item));
        Map<UUID, List<CalendarOccurrence>> occurrencesByEquipment = new HashMap<>();
        occurrences.forEach(item -> occurrencesByEquipment
                .computeIfAbsent(item.equipmentId(), ignored -> new ArrayList<>())
                .add(item));

        List<PprEquipmentCalendarEquipmentRow> content = new ArrayList<>();
        for (UUID equipmentId : equipmentIds) {
            CalendarEquipment equipment = metadataById.get(equipmentId);
            if (equipment == null) {
                continue;
            }
            content.add(toRow(equipment, occurrencesByEquipment.getOrDefault(equipmentId, List.of())));
        }

        int totalPages = equipmentPage.totalElements() == 0
                ? 0
                : (int) ((equipmentPage.totalElements() + filter.size() - 1) / filter.size());
        return new PprEquipmentCalendarResponse(
                new PprEquipmentCalendarPlanSummary(
                        plan.getId(),
                        plan.getCode(),
                        plan.getName(),
                        plan.getStatus(),
                        queryRepository.findLatestApprovalStatus(plan.getId()),
                        plan.getStartDate(),
                        plan.getEndDate()),
                filter.year(),
                source.authoritativeSource(),
                source.calculationRevision(),
                PprEquipmentCalendarPlacementBasis.PLANNED_DATE,
                PprEquipmentCalendarSpanMode.START_MONTH,
                new PprEquipmentCalendarPageMetadata(
                        filter.page(),
                        filter.size(),
                        equipmentPage.totalElements(),
                        totalPages),
                content,
                new PprEquipmentCalendarExcludedDiagnostics(
                        diagnostics.missingEquipment(),
                        diagnostics.unresolvedEquipment(),
                        diagnostics.outsidePlanYear(),
                        diagnostics.outsidePlanRange()));
    }

    private UUID authorizePlanScope(PprPlan plan) {
        boolean scopeAdmin = scopeAccessService.isScopeAdmin();
        if (plan.getDepartmentId() == null) {
            if (!scopeAdmin) {
                throw new AccessDeniedException("Access denied by data scope");
            }
        } else {
            scopeAccessService.assertCanAccessDepartment(plan.getDepartmentId());
        }
        if (scopeAdmin) {
            return null;
        }
        UUID currentDepartmentId = scopeAccessService.currentDepartmentIdOrNull();
        if (currentDepartmentId == null) {
            throw new AccessDeniedException("Access denied by data scope");
        }
        return currentDepartmentId;
    }

    private void validateYearOverlap(PprPlan plan, int year) {
        final LocalDate yearStart;
        final LocalDate yearEnd;
        try {
            yearStart = LocalDate.of(year, 1, 1);
            yearEnd = LocalDate.of(year, 12, 31);
        } catch (DateTimeException exception) {
            throw RestException.badRequest("year must be between 1 and 9999");
        }
        if (yearEnd.isBefore(plan.getStartDate()) || yearStart.isAfter(plan.getEndDate())) {
            throw RestException.badRequest("year does not overlap the PPR plan date range");
        }
    }

    private PprEquipmentCalendarEquipmentRow toRow(
            CalendarEquipment equipment,
            List<CalendarOccurrence> sourceOccurrences) {
        List<CalendarOccurrence> sorted = sourceOccurrences.stream()
                .sorted(occurrenceComparator())
                .toList();
        Map<Integer, List<PprEquipmentCalendarOccurrence>> months = new LinkedHashMap<>();
        for (CalendarOccurrence occurrence : sorted) {
            LocalDate effectivePlannedDate = effectivePlannedDate(occurrence);
            if (effectivePlannedDate == null) {
                continue;
            }
            months.computeIfAbsent(effectivePlannedDate.getMonthValue(), ignored -> new ArrayList<>())
                    .add(toOccurrence(occurrence, effectivePlannedDate));
        }
        long count = months.values().stream().mapToLong(List::size).sum();
        return new PprEquipmentCalendarEquipmentRow(toEquipment(equipment), count, months);
    }

    private Comparator<CalendarOccurrence> occurrenceComparator() {
        Comparator<String> nullableText = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
        Comparator<UUID> nullableId = Comparator.nullsLast(Comparator.naturalOrder());
        return Comparator
                .comparing(this::effectivePlannedDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CalendarOccurrence::maintenanceKind,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CalendarOccurrence::sourceCode, nullableText)
                .thenComparing(CalendarOccurrence::sourceName, nullableText)
                .thenComparing(item -> item.taskId() != null ? item.taskId() : item.calculationItemId(),
                        nullableId);
    }

    private LocalDate effectivePlannedDate(CalendarOccurrence occurrence) {
        if (occurrence.plannedDate() != null) {
            return occurrence.plannedDate();
        }
        return occurrence.scheduledStart() == null
                ? null
                : occurrence.scheduledStart().toLocalDate();
    }

    private PprEquipmentCalendarOccurrence toOccurrence(
            CalendarOccurrence occurrence,
            LocalDate effectivePlannedDate) {
        MaintenanceKindDisplay display = displayResolver.resolve(occurrence.maintenanceKind());
        return new PprEquipmentCalendarOccurrence(
                occurrence.sourceType(),
                occurrence.taskId(),
                occurrence.calculationItemId(),
                occurrence.regulationId(),
                occurrence.maintenanceRuleId(),
                occurrence.sourceCode(),
                occurrence.sourceName(),
                occurrence.maintenanceKind(),
                display.displayCode(),
                display.displayName(),
                effectivePlannedDate,
                occurrence.scheduledStart(),
                occurrence.scheduledEnd(),
                occurrence.dueDate(),
                occurrence.status(),
                occurrence.priority(),
                occurrence.plannedLaborHours(),
                occurrence.title());
    }

    private PprEquipmentCalendarEquipment toEquipment(CalendarEquipment equipment) {
        return new PprEquipmentCalendarEquipment(
                equipment.id(),
                equipment.code(),
                equipment.name(),
                equipment.inventoryNumber(),
                equipment.technicalNumber(),
                equipment.status(),
                equipment.deleted(),
                equipment.criticalityCode(),
                namedRef(
                        equipment.physicalDepartmentId(),
                        equipment.physicalDepartmentCode(),
                        equipment.physicalDepartmentName()),
                namedRef(
                        equipment.responsibleDepartmentId(),
                        equipment.responsibleDepartmentCode(),
                        equipment.responsibleDepartmentName()),
                namedRef(equipment.parentId(), equipment.parentCode(), equipment.parentName()),
                namedRef(equipment.locationId(), equipment.locationCode(), equipment.locationName()),
                namedRef(
                        equipment.equipmentTypeId(),
                        equipment.equipmentTypeCode(),
                        equipment.equipmentTypeName()));
    }

    private PprEquipmentCalendarNamedRef namedRef(UUID id, String code, String name) {
        return id == null ? null : new PprEquipmentCalendarNamedRef(id, code, name);
    }

    private RestException planUnavailable() {
        return RestException.notFound("PPR plan is unavailable");
    }
}
