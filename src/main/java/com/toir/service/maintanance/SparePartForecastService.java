package com.toir.service.maintanance;

import com.toir.dto.sparepartforecast.SparePartForecastEvaluateResponse;
import com.toir.dto.sparepartforecast.SparePartForecastItemDto;
import com.toir.dto.sparepartforecast.SparePartForecastRequest;
import com.toir.dto.sparepartforecast.SparePartForecastSourceDto;
import com.toir.dto.sparepartforecast.SparePartForecastSummaryDto;
import com.toir.entity.SparePart;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.maintenance.MaintenanceRegulationSparePartRequirement;
import com.toir.entity.maintenance.MaintenanceTemplateSparePartRequirement;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.OperationalIssueStatus;
import com.toir.enums.OperationalIssueType;
import com.toir.exception.RestException;
import com.toir.repository.OperationalIssueRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.maintenance.MaintenanceRegulationSparePartRequirementRepository;
import com.toir.repository.maintenance.MaintenanceTemplateSparePartRequirementRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.OperationalIssueService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SparePartForecastService {

    private static final String SOURCE_TYPE = "SPARE_PART_FORECAST";
    private static final List<MaintenanceDueEventStatus> OPEN_EVENT_STATUSES = List.of(
            MaintenanceDueEventStatus.DETECTED,
            MaintenanceDueEventStatus.AWAITING_APPROVAL,
            MaintenanceDueEventStatus.TASK_CREATED,
            MaintenanceDueEventStatus.WORK_ORDER_CREATED
    );
    private static final List<MaintenanceDueStatus> FORECAST_DUE_STATUSES = List.of(
            MaintenanceDueStatus.UPCOMING,
            MaintenanceDueStatus.DUE,
            MaintenanceDueStatus.OVERDUE
    );

    private final MaintenanceDueEventRepository eventRepository;
    private final MaintenanceTemplateSparePartRequirementRepository requirementRepository;
    private final MaintenanceRegulationSparePartRequirementRepository regulationRequirementRepository;
    private final WarehouseStockRepository stockRepository;
    private final WarehouseRepository warehouseRepository;
    private final SparePartRepository sparePartRepository;
    private final EquipmentRepository equipmentRepository;
    private final OperationalIssueRepository operationalIssueRepository;
    private final OperationalIssueService operationalIssueService;
    private final ScopeAccessService scopeAccessService;

    @Transactional(readOnly = true)
    public SparePartForecastSummaryDto forecast(SparePartForecastRequest request) {
        ForecastPeriod period = period(request);
        EffectiveScope scope = effectiveScope(request);
        UUID departmentId = scope.departmentId();
        UUID equipmentId = request == null ? null : request.equipmentId();
        UUID templateId = request == null ? null : request.templateId();
        UUID warehouseId = scope.warehouseId();
        boolean onlyDeficit = request != null && Boolean.TRUE.equals(request.onlyDeficit());

        List<MaintenanceDueEvent> events = eventRepository.findForecastCandidates(
                period.start(),
                period.end(),
                departmentId,
                equipmentId,
                templateId,
                OPEN_EVENT_STATUSES,
                FORECAST_DUE_STATUSES
        );
        if (events.isEmpty()) {
            return new SparePartForecastSummaryDto(period.start(), period.end(), List.of());
        }

        List<UUID> templateIds = events.stream()
                .map(MaintenanceDueEvent::getTemplateId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, List<MaintenanceTemplateSparePartRequirement>> requirementsByTemplate =
                templateIds.isEmpty()
                        ? Map.of()
                        : requirementRepository.findAllActiveByTemplateIdIn(templateIds).stream()
                        .collect(Collectors.groupingBy(MaintenanceTemplateSparePartRequirement::getTemplateId));
        List<UUID> regulationIds = events.stream()
                .map(MaintenanceDueEvent::getRegulationId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, List<MaintenanceRegulationSparePartRequirement>> requirementsByRegulation =
                regulationIds.isEmpty()
                        ? Map.of()
                        : regulationRequirementRepository.findAllActiveByRegulationIdIn(regulationIds).stream()
                        .collect(Collectors.groupingBy(MaintenanceRegulationSparePartRequirement::getRegulationId));
        if (requirementsByTemplate.isEmpty() && requirementsByRegulation.isEmpty()) {
            return new SparePartForecastSummaryDto(period.start(), period.end(), List.of());
        }

        List<DemandRow> demandRows = buildDemandRows(events, requirementsByTemplate, requirementsByRegulation, warehouseId);
        if (demandRows.isEmpty()) {
            return new SparePartForecastSummaryDto(period.start(), period.end(), List.of());
        }

        List<UUID> sparePartIds = demandRows.stream().map(DemandRow::sparePartId).distinct().toList();
        Map<UUID, SparePart> spareParts = sparePartRepository.findAllByIdInAndIsDeletedFalse(sparePartIds).stream()
                .collect(Collectors.toMap(SparePart::getId, sparePart -> sparePart));
        List<WarehouseStock> stocks = loadStocks(sparePartIds, warehouseId, scope.warehouseScopeIds());
        Map<StockKey, StockTotals> stockTotals = stockTotals(stocks, warehouseId);
        Map<UUID, Warehouse> warehouses = loadWarehouses(stocks, warehouseId);
        Map<UUID, Equipment> equipment = loadEquipment(events);

        Map<StockKey, List<DemandRow>> rowsByStock = demandRows.stream()
                .collect(Collectors.groupingBy(row -> new StockKey(row.sparePartId(), row.warehouseId()),
                        LinkedHashMap::new, Collectors.toList()));

        List<SparePartForecastItemDto> items = new ArrayList<>();
        for (Map.Entry<StockKey, List<DemandRow>> entry : rowsByStock.entrySet()) {
            StockKey key = entry.getKey();
            List<DemandRow> rows = entry.getValue();
            double requiredQty = rows.stream().mapToDouble(DemandRow::requiredQty).sum();
            StockTotals totals = stockTotals.getOrDefault(key, StockTotals.ZERO);
            double shortageQty = Math.max(requiredQty - totals.availableQty(), 0);
            if (onlyDeficit && shortageQty <= 0) {
                continue;
            }
            SparePart sparePart = spareParts.get(key.sparePartId());
            Warehouse warehouse = key.warehouseId() == null ? null : warehouses.get(key.warehouseId());
            List<SparePartForecastSourceDto> sources = rows.stream()
                    .sorted(Comparator.comparing(DemandRow::dueAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(row -> toSource(row, equipment.get(row.equipmentId())))
                    .toList();
            Instant firstDueAt = sources.stream()
                    .map(SparePartForecastSourceDto::dueAt)
                    .filter(Objects::nonNull)
                    .min(Instant::compareTo)
                    .orElse(null);
            items.add(new SparePartForecastItemDto(
                    key.sparePartId(),
                    sparePart == null ? null : sparePart.getCode(),
                    sparePart == null ? null : sparePart.getName(),
                    key.warehouseId(),
                    warehouse == null ? null : warehouse.getName(),
                    requiredQty,
                    totals.availableQty(),
                    totals.reservedQty(),
                    shortageQty,
                    firstNonBlank(rows.get(0).unit(), sparePart == null ? null : sparePart.getUnit()),
                    severity(requiredQty, totals.availableQty(), shortageQty),
                    firstDueAt,
                    sources.size(),
                    sources
            ));
        }

        items.sort(Comparator
                .comparing((SparePartForecastItemDto item) -> item.severity().ordinal()).reversed()
                .thenComparing(SparePartForecastItemDto::shortageQty, Comparator.reverseOrder())
                .thenComparing(item -> item.sparePartName() == null ? "" : item.sparePartName()));
        return new SparePartForecastSummaryDto(period.start(), period.end(), items);
    }

    @Transactional
    public SparePartForecastEvaluateResponse evaluateAndCreateIssues(SparePartForecastRequest request) {
        EffectiveScope scope = effectiveScope(request);
        SparePartForecastRequest fullRequest = new SparePartForecastRequest(
                request == null ? null : request.days(),
                request == null ? null : request.from(),
                request == null ? null : request.to(),
                request == null ? null : request.warehouseId(),
                request == null ? null : request.departmentId(),
                request == null ? null : request.equipmentId(),
                request == null ? null : request.templateId(),
                false
        );
        SparePartForecastSummaryDto fullSummary = forecast(fullRequest);
        Map<UUID, SparePartForecastItemDto> candidatesBySourceId = new LinkedHashMap<>();
        Map<UUID, SparePartForecastItemDto> recoveredBySourceId = new LinkedHashMap<>();
        for (SparePartForecastItemDto item : fullSummary.items()) {
            UUID sourceId = sourceId(fullSummary.periodStart(), fullSummary.periodEnd(),
                    item.warehouseId(), scope.departmentId(), item.sparePartId());
            if (item.shortageQty() > 0) {
                candidatesBySourceId.merge(sourceId, item, this::mergeForecastItems);
            } else {
                recoveredBySourceId.put(sourceId, item);
            }
        }
        int created = 0;
        int updated = 0;
        for (Map.Entry<UUID, SparePartForecastItemDto> entry : candidatesBySourceId.entrySet()) {
            UUID sourceId = entry.getKey();
            SparePartForecastItemDto item = entry.getValue();
            boolean exists = operationalIssueRepository
                    .findBySourceTypeAndSourceIdAndStatusAndIsDeletedFalse(SOURCE_TYPE, sourceId, OperationalIssueStatus.OPEN)
                    .isPresent();
            operationalIssueService.openOrUpdate(
                    OperationalIssueType.SPARE_PART_SHORTAGE_FORECAST,
                    item.severity(),
                    issueEquipmentId(item),
                    scope.departmentId(),
                    SOURCE_TYPE,
                    sourceId,
                    "Spare part shortage forecast: " + firstNonBlank(item.sparePartName(), item.sparePartId().toString()),
                    issueMessage(fullSummary, item),
                    issueMetadata(fullSummary, item, scope)
            );
            if (exists) {
                updated++;
            } else {
                created++;
            }
        }
        int resolved = 0;
        for (Map.Entry<UUID, SparePartForecastItemDto> entry : recoveredBySourceId.entrySet()) {
            UUID sourceId = entry.getKey();
            if (operationalIssueRepository
                    .findBySourceTypeAndSourceIdAndStatusAndIsDeletedFalse(SOURCE_TYPE, sourceId, OperationalIssueStatus.OPEN)
                    .isEmpty()) {
                continue;
            }
            operationalIssueService.resolveOpen(SOURCE_TYPE, sourceId, recoveredMessage(fullSummary, entry.getValue()));
            resolved++;
        }
        List<SparePartForecastItemDto> deficitItems = fullSummary.items().stream()
                .filter(item -> item.shortageQty() > 0)
                .toList();
        SparePartForecastSummaryDto deficitSummary =
                new SparePartForecastSummaryDto(fullSummary.periodStart(), fullSummary.periodEnd(), deficitItems);
        return new SparePartForecastEvaluateResponse(created, updated, resolved, deficitSummary);
    }

    private SparePartForecastItemDto mergeForecastItems(SparePartForecastItemDto first, SparePartForecastItemDto duplicate) {
        double requiredQty = first.requiredQty() + duplicate.requiredQty();
        double availableQty = first.availableQty();
        double shortageQty = Math.max(requiredQty - availableQty, 0);
        List<SparePartForecastSourceDto> sources = new ArrayList<>();
        sources.addAll(first.sources());
        sources.addAll(duplicate.sources());
        Instant firstDueAt = sources.stream()
                .map(SparePartForecastSourceDto::dueAt)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);
        return new SparePartForecastItemDto(
                first.sparePartId(),
                firstNonBlank(first.sparePartCode(), duplicate.sparePartCode()),
                firstNonBlank(first.sparePartName(), duplicate.sparePartName()),
                first.warehouseId(),
                firstNonBlank(first.warehouseName(), duplicate.warehouseName()),
                requiredQty,
                availableQty,
                first.reservedQty(),
                shortageQty,
                firstNonBlank(first.unit(), duplicate.unit()),
                severity(requiredQty, availableQty, shortageQty),
                firstDueAt,
                sources.size(),
                sources
        );
    }

    private List<DemandRow> buildDemandRows(List<MaintenanceDueEvent> events,
                                            Map<UUID, List<MaintenanceTemplateSparePartRequirement>> requirementsByTemplate,
                                            Map<UUID, List<MaintenanceRegulationSparePartRequirement>> requirementsByRegulation,
                                            UUID warehouseId) {
        Set<String> seenKeys = new java.util.HashSet<>();
        List<DemandRow> rows = new ArrayList<>();
        for (MaintenanceDueEvent event : events) {
            List<MaintenanceTemplateSparePartRequirement> requirements =
                    event.getTemplateId() == null
                            ? List.of()
                            : requirementsByTemplate.getOrDefault(event.getTemplateId(), List.of());
            for (MaintenanceTemplateSparePartRequirement requirement : requirements) {
                String key = "T:" + event.getId() + ":" + requirement.getId();
                if (!seenKeys.add(key)) {
                    continue;
                }
                rows.add(new DemandRow(
                        event.getId(),
                        requirement.getId(),
                        event.getTemplateId(),
                        event.getEquipmentId(),
                        event.getCreatedTaskId(),
                        event.getCreatedWorkOrderId(),
                        event.getDueAt(),
                        requirement.getSparePartId(),
                        warehouseId,
                        requirement.getQuantity(),
                        requirement.getUnit()
                ));
            }
            List<MaintenanceRegulationSparePartRequirement> regulationRequirements =
                    event.getRegulationId() == null
                            ? List.of()
                            : requirementsByRegulation.getOrDefault(event.getRegulationId(), List.of());
            for (MaintenanceRegulationSparePartRequirement requirement : regulationRequirements) {
                String key = "R:" + event.getId() + ":" + requirement.getId();
                if (!seenKeys.add(key)) {
                    continue;
                }
                rows.add(new DemandRow(
                        event.getId(),
                        requirement.getId(),
                        event.getTemplateId(),
                        event.getEquipmentId(),
                        event.getCreatedTaskId(),
                        event.getCreatedWorkOrderId(),
                        event.getDueAt(),
                        requirement.getSparePartId(),
                        warehouseId,
                        requirement.getQuantity(),
                        requirement.getUnit()
                ));
            }
        }
        return rows;
    }

    private List<WarehouseStock> loadStocks(List<UUID> sparePartIds, UUID warehouseId, List<UUID> warehouseScopeIds) {
        if (sparePartIds.isEmpty()) {
            return List.of();
        }
        if (warehouseId != null) {
            return stockRepository.findAllBySparePartIdInAndWarehouseIdAndIsDeletedFalseOrderByUpdatedAtDesc(sparePartIds, warehouseId);
        }
        if (warehouseScopeIds != null) {
            if (warehouseScopeIds.isEmpty()) {
                return List.of();
            }
            return stockRepository.findAllBySparePartIdInAndWarehouseIdInAndIsDeletedFalseOrderByUpdatedAtDesc(
                    sparePartIds,
                    warehouseScopeIds
            );
        }
        return stockRepository.findAllBySparePartIdInAndIsDeletedFalseOrderByUpdatedAtDesc(sparePartIds);
    }

    private Map<StockKey, StockTotals> stockTotals(List<WarehouseStock> stocks, UUID warehouseId) {
        Map<StockKey, StockTotals> totals = new HashMap<>();
        for (WarehouseStock stock : stocks) {
            UUID keyWarehouseId = warehouseId == null ? null : stock.getWarehouseId();
            StockKey key = new StockKey(stock.getSparePartId(), keyWarehouseId);
            StockTotals current = totals.getOrDefault(key, StockTotals.ZERO);
            double available = Math.max(stock.getAvailable(), 0);
            totals.put(key, new StockTotals(
                    current.availableQty() + available,
                    current.reservedQty() + stock.getReservedQty()
            ));
        }
        return totals;
    }

    private Map<UUID, Warehouse> loadWarehouses(List<WarehouseStock> stocks, UUID warehouseId) {
        List<UUID> warehouseIds = warehouseId != null
                ? List.of(warehouseId)
                : stocks.stream().map(WarehouseStock::getWarehouseId).filter(Objects::nonNull).distinct().toList();
        if (warehouseIds.isEmpty()) {
            return Map.of();
        }
        return warehouseRepository.findAllByIdInAndIsDeletedFalse(warehouseIds).stream()
                .collect(Collectors.toMap(Warehouse::getId, warehouse -> warehouse));
    }

    private Map<UUID, Equipment> loadEquipment(List<MaintenanceDueEvent> events) {
        List<UUID> equipmentIds = events.stream().map(MaintenanceDueEvent::getEquipmentId).filter(Objects::nonNull).distinct().toList();
        if (equipmentIds.isEmpty()) {
            return Map.of();
        }
        return equipmentRepository.findAllByIdInAndIsDeletedFalse(equipmentIds).stream()
                .collect(Collectors.toMap(Equipment::getId, equipment -> equipment));
    }

    private SparePartForecastSourceDto toSource(DemandRow row, Equipment equipment) {
        return new SparePartForecastSourceDto(
                row.maintenanceDueEventId(),
                row.templateId(),
                row.equipmentId(),
                equipment == null ? null : equipment.getName(),
                row.createdTaskId(),
                row.createdWorkOrderId(),
                row.dueAt(),
                row.requiredQty()
        );
    }

    private NotificationSeverity severity(double requiredQty, double availableQty, double shortageQty) {
        if (shortageQty <= 0) {
            return NotificationSeverity.INFO;
        }
        if (availableQty <= 0 || shortageQty >= requiredQty) {
            return NotificationSeverity.CRITICAL;
        }
        return NotificationSeverity.WARNING;
    }

    private ForecastPeriod period(SparePartForecastRequest request) {
        ZoneId zone = ZoneId.systemDefault();
        Instant start = request != null && request.from() != null
                ? request.from()
                : LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Instant end = request != null && request.to() != null
                ? request.to()
                : start.plus(normalizedDays(request), ChronoUnit.DAYS);
        if (end.isBefore(start)) {
            throw RestException.badRequest("forecast period end must be after start");
        }
        return new ForecastPeriod(start, end);
    }

    private int normalizedDays(SparePartForecastRequest request) {
        int days = request == null || request.days() == null ? 30 : request.days();
        if (days < 1 || days > 365) {
            throw RestException.badRequest("days must be between 1 and 365");
        }
        return days;
    }

    private UUID sourceId(Instant start, Instant end, UUID warehouseId, UUID departmentId, UUID sparePartId) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate periodStartDate = start.atZone(zone).toLocalDate();
        LocalDate periodEndDate = end.atZone(zone).toLocalDate();
        String warehouseScope = warehouseId == null ? "ENTERPRISE" : warehouseId.toString();
        String departmentScope = departmentId == null ? "GLOBAL" : departmentId.toString();
        String raw = "spare-part-forecast:" + periodStartDate + ":" + periodEndDate + ":"
                + warehouseScope + ":" + departmentScope + ":" + sparePartId;
        return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String issueMessage(SparePartForecastSummaryDto summary, SparePartForecastItemDto item) {
        return "periodStart=%s; periodEnd=%s; requiredQty=%s; availableQty=%s; shortageQty=%s; sourceCount=%d"
                .formatted(summary.periodStart(), summary.periodEnd(), item.requiredQty(), item.availableQty(),
                        item.shortageQty(), item.sourceCount());
    }

    private String recoveredMessage(SparePartForecastSummaryDto summary, SparePartForecastItemDto item) {
        return "Spare part shortage recovered; periodStart=%s; periodEnd=%s; requiredQty=%s; availableQty=%s; shortageQty=%s; sourceCount=%d"
                .formatted(summary.periodStart(), summary.periodEnd(), item.requiredQty(), item.availableQty(),
                        item.shortageQty(), item.sourceCount());
    }

    private Map<String, Object> issueMetadata(SparePartForecastSummaryDto summary,
                                              SparePartForecastItemDto item,
                                              EffectiveScope scope) {
        ZoneId zone = ZoneId.systemDefault();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("periodStartDate", summary.periodStart().atZone(zone).toLocalDate().toString());
        metadata.put("periodEndDate", summary.periodEnd().atZone(zone).toLocalDate().toString());
        metadata.put("warehouseId", item.warehouseId() == null ? null : item.warehouseId().toString());
        metadata.put("warehouseScope", item.warehouseId() == null ? "ENTERPRISE" : item.warehouseId().toString());
        metadata.put("departmentId", scope.departmentId() == null ? null : scope.departmentId().toString());
        metadata.put("sparePartId", item.sparePartId().toString());
        metadata.put("sparePartCode", item.sparePartCode());
        metadata.put("requiredQty", item.requiredQty());
        metadata.put("availableQty", item.availableQty());
        metadata.put("shortageQty", item.shortageQty());
        metadata.put("sourceCount", item.sourceCount());
        return metadata;
    }

    private UUID issueEquipmentId(SparePartForecastItemDto item) {
        if (item.sources().size() != 1) {
            return null;
        }
        return item.sources().get(0).equipmentId();
    }

    private EffectiveScope effectiveScope(SparePartForecastRequest request) {
        UUID requestedDepartmentId = request == null ? null : request.departmentId();
        UUID requestedWarehouseId = request == null ? null : request.warehouseId();
        if (scopeAccessService.isScopeAdmin()) {
            return new EffectiveScope(requestedDepartmentId, requestedWarehouseId, null);
        }

        UUID currentDepartmentId = scopeAccessService.currentDepartmentIdOrNull();
        if (currentDepartmentId == null) {
            throw new AccessDeniedException("Access denied by department scope");
        }
        if (requestedDepartmentId != null && !currentDepartmentId.equals(requestedDepartmentId)) {
            throw new AccessDeniedException("Access denied by department scope");
        }

        if (requestedWarehouseId != null) {
            Warehouse warehouse = warehouseRepository.findByIdAndIsDeletedFalse(requestedWarehouseId)
                    .orElseThrow(() -> RestException.notFound("Warehouse not found: " + requestedWarehouseId));
            if (!canAccessWarehouse(warehouse)) {
                throw new AccessDeniedException("Access denied by warehouse scope");
            }
            return new EffectiveScope(currentDepartmentId, requestedWarehouseId, List.of(requestedWarehouseId));
        }

        List<UUID> warehouseScopeIds = warehouseRepository.search(null, currentDepartmentId, null, null, true).stream()
                .map(Warehouse::getId)
                .filter(Objects::nonNull)
                .toList();
        return new EffectiveScope(currentDepartmentId, null, warehouseScopeIds);
    }

    private boolean canAccessWarehouse(Warehouse warehouse) {
        if (warehouse == null) {
            return false;
        }
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        return (warehouse.getDepartmentId() != null && scopeAccessService.canAccessDepartment(warehouse.getDepartmentId()))
                || (warehouse.getResponsibleId() != null && scopeAccessService.canAccessEmployee(warehouse.getResponsibleId()));
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private record ForecastPeriod(Instant start, Instant end) {
    }

    private record EffectiveScope(UUID departmentId, UUID warehouseId, List<UUID> warehouseScopeIds) {
    }

    private record StockKey(UUID sparePartId, UUID warehouseId) {
    }

    private record StockTotals(double availableQty, double reservedQty) {
        private static final StockTotals ZERO = new StockTotals(0, 0);
    }

    private record DemandRow(
            UUID maintenanceDueEventId,
            UUID requirementId,
            UUID templateId,
            UUID equipmentId,
            UUID createdTaskId,
            UUID createdWorkOrderId,
            Instant dueAt,
            UUID sparePartId,
            UUID warehouseId,
            double requiredQty,
            String unit
    ) {
    }
}
