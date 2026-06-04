package com.toir.service.maintanance;

import com.toir.dto.sparepartforecast.SparePartForecastEvaluateResponse;
import com.toir.dto.sparepartforecast.SparePartForecastItemDto;
import com.toir.dto.sparepartforecast.SparePartForecastRequest;
import com.toir.dto.sparepartforecast.SparePartForecastSourceDto;
import com.toir.dto.sparepartforecast.SparePartForecastSummaryDto;
import com.toir.entity.SparePart;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceDueEvent;
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
import com.toir.repository.maintenance.MaintenanceTemplateSparePartRequirementRepository;
import com.toir.service.OperationalIssueService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
    private final WarehouseStockRepository stockRepository;
    private final WarehouseRepository warehouseRepository;
    private final SparePartRepository sparePartRepository;
    private final EquipmentRepository equipmentRepository;
    private final OperationalIssueRepository operationalIssueRepository;
    private final OperationalIssueService operationalIssueService;

    @Transactional(readOnly = true)
    public SparePartForecastSummaryDto forecast(SparePartForecastRequest request) {
        ForecastPeriod period = period(request);
        UUID departmentId = request == null ? null : request.departmentId();
        UUID equipmentId = request == null ? null : request.equipmentId();
        UUID templateId = request == null ? null : request.templateId();
        UUID warehouseId = request == null ? null : request.warehouseId();
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
                requirementRepository.findAllActiveByTemplateIdIn(templateIds).stream()
                        .collect(Collectors.groupingBy(MaintenanceTemplateSparePartRequirement::getTemplateId));
        if (requirementsByTemplate.isEmpty()) {
            return new SparePartForecastSummaryDto(period.start(), period.end(), List.of());
        }

        List<DemandRow> demandRows = buildDemandRows(events, requirementsByTemplate, warehouseId);
        if (demandRows.isEmpty()) {
            return new SparePartForecastSummaryDto(period.start(), period.end(), List.of());
        }

        List<UUID> sparePartIds = demandRows.stream().map(DemandRow::sparePartId).distinct().toList();
        Map<UUID, SparePart> spareParts = sparePartRepository.findAllByIdInAndIsDeletedFalse(sparePartIds).stream()
                .collect(Collectors.toMap(SparePart::getId, sparePart -> sparePart));
        List<WarehouseStock> stocks = loadStocks(sparePartIds, warehouseId);
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
        SparePartForecastRequest deficitRequest = new SparePartForecastRequest(
                request == null ? null : request.days(),
                request == null ? null : request.from(),
                request == null ? null : request.to(),
                request == null ? null : request.warehouseId(),
                request == null ? null : request.departmentId(),
                request == null ? null : request.equipmentId(),
                request == null ? null : request.templateId(),
                true
        );
        SparePartForecastSummaryDto summary = forecast(deficitRequest);
        int created = 0;
        int updated = 0;
        for (SparePartForecastItemDto item : summary.items()) {
            if (item.shortageQty() <= 0) {
                continue;
            }
            UUID sourceId = sourceId(summary.periodStart(), summary.periodEnd(), item.warehouseId(), item.sparePartId());
            boolean exists = operationalIssueRepository
                    .findBySourceTypeAndSourceIdAndStatusAndIsDeletedFalse(SOURCE_TYPE, sourceId, OperationalIssueStatus.OPEN)
                    .isPresent();
            operationalIssueService.openOrUpdate(
                    OperationalIssueType.SPARE_PART_SHORTAGE_FORECAST,
                    item.severity(),
                    null,
                    null,
                    SOURCE_TYPE,
                    sourceId,
                    "Spare part shortage forecast: " + firstNonBlank(item.sparePartName(), item.sparePartId().toString()),
                    issueMessage(summary, item)
            );
            if (exists) {
                updated++;
            } else {
                created++;
            }
        }
        return new SparePartForecastEvaluateResponse(created, updated, 0, summary);
    }

    private List<DemandRow> buildDemandRows(List<MaintenanceDueEvent> events,
                                            Map<UUID, List<MaintenanceTemplateSparePartRequirement>> requirementsByTemplate,
                                            UUID warehouseId) {
        Set<String> seenKeys = new java.util.HashSet<>();
        List<DemandRow> rows = new ArrayList<>();
        for (MaintenanceDueEvent event : events) {
            List<MaintenanceTemplateSparePartRequirement> requirements =
                    requirementsByTemplate.getOrDefault(event.getTemplateId(), List.of());
            for (MaintenanceTemplateSparePartRequirement requirement : requirements) {
                String key = event.getId() + ":" + requirement.getId();
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

    private List<WarehouseStock> loadStocks(List<UUID> sparePartIds, UUID warehouseId) {
        if (sparePartIds.isEmpty()) {
            return List.of();
        }
        if (warehouseId != null) {
            return stockRepository.findAllBySparePartIdInAndWarehouseIdAndIsDeletedFalseOrderByUpdatedAtDesc(sparePartIds, warehouseId);
        }
        return stockRepository.findAllBySparePartIdInAndIsDeletedFalseOrderByUpdatedAtDesc(sparePartIds);
    }

    private Map<StockKey, StockTotals> stockTotals(List<WarehouseStock> stocks, UUID warehouseId) {
        Map<StockKey, StockTotals> totals = new HashMap<>();
        for (WarehouseStock stock : stocks) {
            UUID keyWarehouseId = warehouseId == null ? null : stock.getWarehouseId();
            StockKey key = new StockKey(stock.getSparePartId(), keyWarehouseId);
            StockTotals current = totals.getOrDefault(key, StockTotals.ZERO);
            totals.put(key, new StockTotals(
                    current.availableQty() + stock.getAvailable(),
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
        Instant start = request != null && request.from() != null ? request.from() : Instant.now();
        Instant end = request != null && request.to() != null ? request.to() : start.plus(normalizedDays(request), ChronoUnit.DAYS);
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

    private UUID sourceId(Instant start, Instant end, UUID warehouseId, UUID sparePartId) {
        String scope = warehouseId == null ? "enterprise" : warehouseId.toString();
        String raw = "spare-part-forecast:" + start + ":" + end + ":" + scope + ":" + sparePartId;
        return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String issueMessage(SparePartForecastSummaryDto summary, SparePartForecastItemDto item) {
        return "periodStart=%s; periodEnd=%s; requiredQty=%s; availableQty=%s; shortageQty=%s; sourceCount=%d"
                .formatted(summary.periodStart(), summary.periodEnd(), item.requiredQty(), item.availableQty(),
                        item.shortageQty(), item.sourceCount());
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private record ForecastPeriod(Instant start, Instant end) {
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
