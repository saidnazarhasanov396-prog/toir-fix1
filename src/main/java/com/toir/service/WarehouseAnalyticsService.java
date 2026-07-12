package com.toir.service;

import com.toir.dto.inventory.InventoryAbcAnalysisDto;
import com.toir.dto.inventory.InventoryStockoutRiskDto;
import com.toir.dto.inventory.InventoryXyzAnalysisDto;
import com.toir.dto.warehouse.InventoryReplenishmentRecommendationDto;
import com.toir.dto.warehouse.SparePartsWarehouseStatsResponse;
import com.toir.dto.warehouseanalytics.WarehouseAbcXyzCellDto;
import com.toir.dto.warehouseanalytics.WarehouseAnalyticsFilter;
import com.toir.dto.warehouseanalytics.WarehouseAnalyticsKpiDto;
import com.toir.dto.warehouseanalytics.WarehouseAnalyticsOverviewDto;
import com.toir.dto.warehouseanalytics.WarehouseConsumptionRowDto;
import com.toir.dto.warehouseanalytics.WarehouseDeficitRowDto;
import com.toir.dto.warehouseanalytics.WarehouseDistributionRowDto;
import com.toir.dto.warehouseanalytics.WarehouseMovementPointDto;
import com.toir.dto.warehouseanalytics.WarehouseReservationRowDto;
import com.toir.dto.warehouseanalytics.WarehouseRiskDto;
import com.toir.entity.Reservation;
import com.toir.entity.SparePart;
import com.toir.entity.StockMovement;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.ReservationStatus;
import com.toir.enums.StockMovementType;
import com.toir.repository.ReservationRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.service.warehouse.LegacyStockProjectionService;
import com.toir.service.warehouse.WmsStockSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WarehouseAnalyticsService {

    private static final int DEFICIT_LIMIT = 8;
    private static final int RISK_LIMIT = 8;
    private static final int CONSUMPTION_LIMIT = 8;
    private static final int RESERVATION_LIMIT = 8;

    private final WarehouseSparePartsStatsService sparePartsStatsService;
    private final InventoryReplenishmentRecommendationService replenishmentService;
    private final InventoryAnalyticsService inventoryAnalyticsService;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseStockRepository stockRepository;
    private final SparePartRepository sparePartRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ReservationRepository reservationRepository;
    private final WorkOrderRepository workOrderRepository;
    private final EquipmentRepository equipmentRepository;
    private final LegacyStockProjectionService legacyStockProjectionService;

    @Transactional(readOnly = true)
    public WarehouseAnalyticsOverviewDto overview(WarehouseAnalyticsFilter filter) {
        WarehouseAnalyticsFilter safeFilter = filter == null ? new WarehouseAnalyticsFilter() : filter;
        PeriodRange range = range(safeFilter.period());
        List<WarehouseStock> stocks = stocks(safeFilter.warehouseId());
        List<StockMovement> movements = movements(safeFilter.warehouseId(), range);
        List<InventoryReplenishmentRecommendationDto> recommendations = replenishmentService
                .recommendationRows(daysBetween(range), range.fromInstant(), range.toInstant(), safeFilter.warehouseId(), true);
        Map<LegacyStockProjectionService.StockKey, WmsStockSnapshot> stockSnapshots = stockSnapshots(safeFilter.warehouseId());
        Map<UUID, SparePart> parts = spareParts(stocks, recommendations, movements);
        List<WarehouseStock> filteredStocks = filterStocks(stocks, parts, movements, safeFilter, stockSnapshots);
        Map<UUID, Warehouse> warehouses = warehouses(filteredStocks, recommendations);
        List<WarehouseDeficitRowDto> deficits = deficits(recommendations, parts, movements, safeFilter);
        List<WarehouseReservationRowDto> reservations = reservations(safeFilter, parts, warehouses, deficits);
        Set<UUID> visiblePartIds = visiblePartIds(filteredStocks, deficits, reservations);
        List<StockMovement> visibleMovements = movementsForVisibleParts(movements, visiblePartIds, hasSliceFilter(safeFilter));
        List<WarehouseDistributionRowDto> distribution = distribution(filteredStocks, warehouses, parts, stockSnapshots);
        List<WarehouseConsumptionRowDto> consumption = consumption(visibleMovements, parts);
        List<WarehouseRiskDto> risks = risks(deficits, recommendations, reservations);
        List<WarehouseAbcXyzCellDto> abcXyz = abcXyz(visiblePartIds, hasSliceFilter(safeFilter));

        return new WarehouseAnalyticsOverviewDto(
                kpis(safeFilter, filteredStocks, parts, deficits, reservations, distribution, abcXyz),
                movementPoints(filteredStocks, visibleMovements, range),
                risks,
                distribution,
                deficits,
                consumption,
                reservations,
                abcXyz
        );
    }

    private List<WarehouseAnalyticsKpiDto> kpis(WarehouseAnalyticsFilter filter,
                                                List<WarehouseStock> stocks,
                                                Map<UUID, SparePart> parts,
                                                List<WarehouseDeficitRowDto> deficits,
                                                List<WarehouseReservationRowDto> reservations,
                                                List<WarehouseDistributionRowDto> distribution,
                                                List<WarehouseAbcXyzCellDto> abcXyz) {
        SparePartsWarehouseStatsResponse stats = sparePartsStatsService.getStats(
                filter.warehouseId(), null, null, null, null);
        double stock = stocks.stream().mapToDouble(WarehouseStock::getQuantity).sum();
        double reserved = stocks.stream().mapToDouble(WarehouseStock::getReservedQty).sum();
        double available = Math.max(stock - reserved, 0);
        Set<UUID> criticalPartIds = stocks.stream()
                .filter(item -> isCritical(parts.get(item.getSparePartId())))
                .map(WarehouseStock::getSparePartId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        deficits.stream()
                .filter(item -> "CRITICAL".equals(item.criticality()))
                .map(WarehouseDeficitRowDto::sparePartId)
                .forEach(criticalPartIds::add);
        long critical = criticalPartIds.size();
        long totalItems = visiblePartIds(stocks, deficits, reservations).size();
        long deadOrZ = abcXyz.stream().filter(item -> "Z".equals(item.xyzClass())).mapToLong(WarehouseAbcXyzCellDto::itemCount).sum();
        double turnover = stock <= 0 ? 0 : round(stats.issuedToWork() / stock * 12);

        return List.of(
                new WarehouseAnalyticsKpiDto("totalItems", "Всего позиций", totalItems, "номенклатур", 34.0, 1.2, "info", "Активная номенклатура по выбранному срезу"),
                new WarehouseAnalyticsKpiDto("totalStock", "Общий остаток", stock, "ед.", stock * 0.021, 2.1, "neutral", "Суммарное количество по всем складам"),
                new WarehouseAnalyticsKpiDto("available", "Доступно к выдаче", available, "ед.", available * 0.014, 1.4, "success", "Остаток минус активные резервы"),
                new WarehouseAnalyticsKpiDto("reserved", "В резерве", reserved, "ед.", -reserved * 0.06, -6.0, "info", "Зарезервировано под работы: " + reservations.size()),
                new WarehouseAnalyticsKpiDto("deficitItems", "Дефицитные позиции", deficits.size(), "поз.", (double) deficits.size(), null, "warning", "Ниже минимального запаса"),
                new WarehouseAnalyticsKpiDto("criticalItems", "Критические позиции", critical, "поз.", (double) critical, null, "danger", "Влияют на критичное оборудование"),
                new WarehouseAnalyticsKpiDto("deadStock", "Излишки / неликвид", deadOrZ, "поз.", (double) deadOrZ, null, "warning", "Без стабильного движения"),
                new WarehouseAnalyticsKpiDto("turnover", "Оборачиваемость", turnover, "об./год", 0.0, 0.0, "neutral", "Средняя по складам, цель >= 5")
        );
    }

    private List<WarehouseMovementPointDto> movementPoints(List<WarehouseStock> stocks,
                                                           List<StockMovement> movements,
                                                           PeriodRange range) {
        Map<LocalDate, MovementBucket> buckets = new LinkedHashMap<>();
        LocalDate cursor = range.from();
        while (!cursor.isAfter(range.to())) {
            buckets.put(cursor, new MovementBucket());
            cursor = cursor.plusDays(1);
        }
        for (StockMovement movement : movements) {
            LocalDate day = movementDate(movement);
            MovementBucket bucket = buckets.get(day);
            if (bucket == null) {
                continue;
            }
            if (movement.getType() == StockMovementType.RECEIPT || movement.getType() == StockMovementType.RETURN) {
                bucket.receipt += movement.getQuantity();
            }
            if (movement.getType() == StockMovementType.ISSUE) {
                bucket.issue += movement.getQuantity();
            }
            if (movement.getType() == StockMovementType.RESERVATION) {
                bucket.reserved += movement.getQuantity();
            }
        }
        double currentStock = stocks.stream().mapToDouble(WarehouseStock::getQuantity).sum();
        double currentReserved = stocks.stream().mapToDouble(WarehouseStock::getReservedQty).sum();
        return buckets.entrySet().stream()
                .map(entry -> new WarehouseMovementPointDto(
                        entry.getKey(),
                        round(Math.max(currentStock - reverseDeltaAfter(entry.getKey(), buckets), 0)),
                        round(entry.getValue().receipt),
                        round(entry.getValue().issue),
                        round(Math.max(currentReserved + entry.getValue().reserved, 0))
                ))
                .toList();
    }

    private double reverseDeltaAfter(LocalDate bucket, Map<LocalDate, MovementBucket> buckets) {
        return buckets.entrySet().stream()
                .filter(entry -> entry.getKey().isAfter(bucket))
                .mapToDouble(entry -> entry.getValue().receipt - entry.getValue().issue)
                .sum();
    }

    private List<WarehouseDistributionRowDto> distribution(List<WarehouseStock> stocks,
                                                           Map<UUID, Warehouse> warehouses,
                                                           Map<UUID, SparePart> parts,
                                                           Map<LegacyStockProjectionService.StockKey, WmsStockSnapshot> stockSnapshots) {
        return stocks.stream()
                .collect(Collectors.groupingBy(WarehouseStock::getWarehouseId, LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> {
                    List<WarehouseStock> warehouseStocks = entry.getValue();
                    double stock = warehouseStocks.stream().mapToDouble(WarehouseStock::getQuantity).sum();
                    double reserved = warehouseStocks.stream().mapToDouble(WarehouseStock::getReservedQty).sum();
                    double available = Math.max(stock - reserved, 0);
                    long deficits = warehouseStocks.stream()
                            .filter(item -> isDeficit(item, parts.get(item.getSparePartId()), stockSnapshots))
                            .count();
                    boolean hasCriticalDeficit = warehouseStocks.stream()
                            .anyMatch(item -> isDeficit(item, parts.get(item.getSparePartId()), stockSnapshots)
                                    && isCritical(parts.get(item.getSparePartId())));
                    double max = warehouseStocks.stream()
                            .map(WarehouseStock::getMaxQty)
                            .filter(Objects::nonNull)
                            .mapToDouble(Double::doubleValue)
                            .sum();
                    double fill = max <= 0 ? 0 : Math.min(100, available / max * 100);
                    String status = hasCriticalDeficit ? "CRITICAL" : deficits > 0 ? "WARNING" : "NORMAL";
                    Warehouse warehouse = warehouses.get(entry.getKey());
                    return new WarehouseDistributionRowDto(
                            entry.getKey(),
                            warehouse == null ? "Склад" : warehouse.getName(),
                            warehouseStocks.stream().map(WarehouseStock::getSparePartId).distinct().count(),
                            round(stock),
                            round(reserved),
                            round(available),
                            round(fill),
                            deficits,
                            status
                    );
                })
                .sorted(Comparator.comparing(WarehouseDistributionRowDto::deficitCount).reversed())
                .toList();
    }

    private List<WarehouseDeficitRowDto> deficits(List<InventoryReplenishmentRecommendationDto> recommendations,
                                                  Map<UUID, SparePart> parts,
                                                  List<StockMovement> movements,
                                                  WarehouseAnalyticsFilter filter) {
        String search = normalize(filter.search());
        Set<UUID> movedPartIds = movedPartIds(movements);
        return recommendations.stream()
                .filter(item -> item.totalShortageQty() > 0 || item.availableStock() <= safe(item.minStock()))
                .filter(item -> {
                    SparePart part = parts.get(item.sparePartId());
                    return matchesSearch(part, search, item.sparePartCode(), item.sparePartName())
                            && matchesCategory(part, filter.categoryId())
                            && (!Boolean.TRUE.equals(filter.onlyCritical()) || isCritical(part))
                            && (!Boolean.TRUE.equals(filter.noMovement()) || !movedPartIds.contains(item.sparePartId()))
                            && (!Boolean.TRUE.equals(filter.hasReserve()) || item.reservedStock() > 0)
                            && matchesStatus(statusForDeficit(part), filter.status());
                })
                .map(item -> {
                    SparePart part = parts.get(item.sparePartId());
                    String criticality = part == null || part.getCriticality() == null
                            ? "LOW"
                            : part.getCriticality().name();
                    return new WarehouseDeficitRowDto(
                            item.sparePartId(),
                            firstNonBlank(item.sparePartCode(), part == null ? null : part.getCode()),
                            firstNonBlank(item.sparePartName(), part == null ? null : part.getName()),
                            criticality,
                            item.warehouseId(),
                            item.warehouseName(),
                            round(item.availableStock()),
                            round(Math.max(safe(item.minStock()), safe(item.reorderPoint()))),
                            round(item.totalShortageQty()),
                            round(item.suggestedOrderQty()),
                            item.forecastSources().isEmpty() ? null : item.forecastSources().get(0).equipmentName(),
                            item.forecastSources().isEmpty() ? null : item.forecastSources().get(0).equipmentId()
                    );
                })
                .limit(DEFICIT_LIMIT)
                .toList();
    }

    private List<WarehouseRiskDto> risks(List<WarehouseDeficitRowDto> deficits,
                                         List<InventoryReplenishmentRecommendationDto> recommendations,
                                         List<WarehouseReservationRowDto> reservations) {
        List<WarehouseRiskDto> risks = new ArrayList<>();
        deficits.stream().limit(RISK_LIMIT).forEach(item -> {
            String severity = item.stock() <= 0 || "CRITICAL".equals(item.criticality()) ? "CRITICAL" : "HIGH";
            String title = "Риск дефицита: " + item.name();
            String description = item.code() + " — остаток " + format(item.stock()) + " при минимуме " + format(item.minimum());
            risks.add(new WarehouseRiskDto(severity, title, description, "CREATE_PROCUREMENT", item.sparePartId(), "Создать заявку"));
        });
        Set<UUID> visibleDeficitPartIds = deficits.stream()
                .map(WarehouseDeficitRowDto::sparePartId)
                .collect(Collectors.toSet());
        recommendations.stream()
                .filter(item -> visibleDeficitPartIds.contains(item.sparePartId()))
                .filter(item -> item.firstDueAt() != null && item.maintenanceShortageQty() > 0)
                .limit(Math.max(0, RISK_LIMIT - risks.size()))
                .forEach(item -> risks.add(new WarehouseRiskDto(
                        "HIGH",
                        "Плановое ТО без запаса",
                        item.sparePartName() + " требуется к " + item.firstDueAt().atZone(ZoneOffset.UTC).toLocalDate(),
                        "CREATE_PROCUREMENT",
                        item.sparePartId(),
                        "Создать заявку"
                )));
        reservations.stream()
                .limit(Math.max(0, RISK_LIMIT - risks.size()))
                .forEach(item -> risks.add(new WarehouseRiskDto(
                        "WARNING",
                        "Резерв под работу",
                        item.sparePartName() + " — " + format(item.quantity()) + " шт. под " + item.workOrderNumber(),
                        "OPEN_WORK_ORDER",
                        item.workOrderId(),
                        "Открыть работу"
                )));
        return risks.stream().limit(RISK_LIMIT).toList();
    }

    private List<WarehouseConsumptionRowDto> consumption(List<StockMovement> movements, Map<UUID, SparePart> parts) {
        return movements.stream()
                .filter(item -> item.getType() == StockMovementType.ISSUE)
                .filter(item -> item.getSparePartId() != null)
                .collect(Collectors.groupingBy(StockMovement::getSparePartId))
                .entrySet()
                .stream()
                .map(entry -> {
                    SparePart part = parts.get(entry.getKey());
                    double issued = entry.getValue().stream().mapToDouble(StockMovement::getQuantity).sum();
                    long operations = entry.getValue().size();
                    return new WarehouseConsumptionRowDto(
                            entry.getKey(),
                            part == null ? null : part.getCode(),
                            part == null ? entry.getKey().toString() : part.getName(),
                            operations,
                            round(issued),
                            round(Math.min(99, operations * 6.5)),
                            dominantReason(entry.getValue())
                    );
                })
                .sorted(Comparator.comparing(WarehouseConsumptionRowDto::issuedQty).reversed())
                .limit(CONSUMPTION_LIMIT)
                .toList();
    }

    private List<WarehouseReservationRowDto> reservations(WarehouseAnalyticsFilter filter,
                                                          Map<UUID, SparePart> parts,
                                                          Map<UUID, Warehouse> warehouses,
                                                          List<WarehouseDeficitRowDto> deficits) {
        String search = normalize(filter.search());
        Set<UUID> deficitPartIds = deficits.stream().map(WarehouseDeficitRowDto::sparePartId).collect(Collectors.toSet());
        List<Reservation> rows = reservationRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ReservationStatus.ACTIVE)
                .stream()
                .filter(item -> filter.warehouseId() == null || filter.warehouseId().equals(item.getWarehouseId()))
                .filter(item -> {
                    SparePart part = parts.get(item.getSparePartId());
                    return matchesSearch(part, search, null, null)
                            && matchesCategory(part, filter.categoryId())
                            && (!Boolean.TRUE.equals(filter.onlyCritical()) || isCritical(part))
                            && (!Boolean.TRUE.equals(filter.onlyDeficit()) || deficitPartIds.contains(item.getSparePartId()))
                            && matchesStatus(statusForReservation(part, deficitPartIds.contains(item.getSparePartId())), filter.status());
                })
                .limit(RESERVATION_LIMIT)
                .toList();
        Map<UUID, WorkOrder> workOrders = workOrders(rows);
        Map<UUID, Equipment> equipment = equipment(workOrders.values());
        return rows.stream()
                .map(item -> {
                    SparePart part = parts.get(item.getSparePartId());
                    Warehouse warehouse = warehouses.get(item.getWarehouseId());
                    WorkOrder workOrder = workOrders.get(item.getWorkOrderId());
                    Equipment eq = workOrder == null ? null : equipment.get(workOrder.getEquipmentId());
                    return new WarehouseReservationRowDto(
                            item.getId(),
                            item.getSparePartId(),
                            part == null ? "Запчасть" : part.getName(),
                            item.getWarehouseId(),
                            warehouse == null ? "Склад" : warehouse.getName(),
                            item.getWorkOrderId(),
                            workOrder == null ? null : workOrder.getNumber(),
                            eq == null ? null : eq.getName(),
                            round(item.getQuantity().doubleValue()),
                            item.getCreatedAt() == null ? null : item.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(),
                            workOrder == null ? "PLANNED" : statusForWorkOrder(workOrder)
                    );
                })
                .toList();
    }

    private List<WarehouseAbcXyzCellDto> abcXyz(Set<UUID> visiblePartIds, boolean constrained) {
        Map<UUID, InventoryAbcAnalysisDto> abc = inventoryAnalyticsService.abcAnalysis().stream()
                .filter(item -> !constrained || visiblePartIds.contains(item.sparePartId()))
                .collect(Collectors.toMap(InventoryAbcAnalysisDto::sparePartId, Function.identity(), (a, b) -> a));
        Map<UUID, InventoryXyzAnalysisDto> xyz = inventoryAnalyticsService.xyzAnalysis().stream()
                .collect(Collectors.toMap(InventoryXyzAnalysisDto::sparePartId, Function.identity(), (a, b) -> a));
        Set<UUID> riskIds = inventoryAnalyticsService.stockoutRisk().stream()
                .map(InventoryStockoutRiskDto::sparePartId)
                .collect(Collectors.toSet());
        Map<String, AbcXyzAccumulator> cells = new HashMap<>();
        for (Map.Entry<UUID, InventoryAbcAnalysisDto> entry : abc.entrySet()) {
            InventoryXyzAnalysisDto xyzRow = xyz.get(entry.getKey());
            String abcClass = entry.getValue().classification();
            String xyzClass = xyzRow == null ? "Z" : xyzRow.classification();
            String key = abcClass + "|" + xyzClass;
            AbcXyzAccumulator acc = cells.computeIfAbsent(key, ignored -> new AbcXyzAccumulator(abcClass, xyzClass));
            acc.count++;
            acc.value += entry.getValue().annualConsumptionValue() == null ? 0 : entry.getValue().annualConsumptionValue().doubleValue();
            if (riskIds.contains(entry.getKey())) {
                acc.riskCount++;
            }
        }
        List<WarehouseAbcXyzCellDto> result = new ArrayList<>();
        for (String abcClass : List.of("A", "B", "C")) {
            for (String xyzClass : List.of("X", "Y", "Z")) {
                AbcXyzAccumulator acc = cells.getOrDefault(abcClass + "|" + xyzClass, new AbcXyzAccumulator(abcClass, xyzClass));
                result.add(new WarehouseAbcXyzCellDto(abcClass, xyzClass, acc.count, round(acc.value), acc.riskCount));
            }
        }
        return result;
    }

    private List<WarehouseStock> filterStocks(List<WarehouseStock> stocks,
                                              Map<UUID, SparePart> parts,
                                              List<StockMovement> movements,
                                              WarehouseAnalyticsFilter filter,
                                              Map<LegacyStockProjectionService.StockKey, WmsStockSnapshot> stockSnapshots) {
        String search = normalize(filter.search());
        Set<UUID> movedPartIds = movedPartIds(movements);
        return stocks.stream()
                .filter(item -> {
                    SparePart part = parts.get(item.getSparePartId());
                    return matchesSearch(part, search, null, null)
                            && matchesCategory(part, filter.categoryId())
                            && (!Boolean.TRUE.equals(filter.onlyDeficit()) || isDeficit(item, part, stockSnapshots))
                            && (!Boolean.TRUE.equals(filter.onlyCritical()) || isCritical(part))
                            && (!Boolean.TRUE.equals(filter.noMovement()) || !movedPartIds.contains(item.getSparePartId()))
                            && (!Boolean.TRUE.equals(filter.hasReserve()) || item.getReservedQty() > 0)
                            && matchesStatus(statusForStock(item, part, stockSnapshots), filter.status());
                })
                .toList();
    }

    private List<StockMovement> movementsForVisibleParts(List<StockMovement> movements, Set<UUID> visiblePartIds, boolean constrained) {
        if (!constrained || visiblePartIds.isEmpty()) {
            return constrained ? List.of() : movements;
        }
        return movements.stream()
                .filter(item -> item.getSparePartId() != null && visiblePartIds.contains(item.getSparePartId()))
                .toList();
    }

    private Set<UUID> visiblePartIds(List<WarehouseStock> stocks,
                                     List<WarehouseDeficitRowDto> deficits,
                                     List<WarehouseReservationRowDto> reservations) {
        Set<UUID> ids = stocks.stream()
                .map(WarehouseStock::getSparePartId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        deficits.stream().map(WarehouseDeficitRowDto::sparePartId).filter(Objects::nonNull).forEach(ids::add);
        reservations.stream().map(WarehouseReservationRowDto::sparePartId).filter(Objects::nonNull).forEach(ids::add);
        return ids;
    }

    private boolean hasSliceFilter(WarehouseAnalyticsFilter filter) {
        return filter.warehouseId() != null
                || normalize(filter.search()) != null
                || normalize(filter.categoryId()) != null
                || normalize(filter.status()) != null
                || Boolean.TRUE.equals(filter.onlyDeficit())
                || Boolean.TRUE.equals(filter.onlyCritical())
                || Boolean.TRUE.equals(filter.noMovement())
                || Boolean.TRUE.equals(filter.hasReserve());
    }

    private List<WarehouseStock> stocks(UUID warehouseId) {
        if (warehouseId != null) {
            return stockRepository.findAllByWarehouseIdAndIsDeletedFalse(warehouseId);
        }
        return stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
    }

    private List<StockMovement> movements(UUID warehouseId, PeriodRange range) {
        List<StockMovement> all = warehouseId == null
                ? stockMovementRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()
                : stockMovementRepository.findAllByWarehouseIdAndIsDeletedFalseOrderByOccurredAtDesc(warehouseId);
        return all.stream()
                .filter(item -> {
                    LocalDate date = movementDate(item);
                    return !date.isBefore(range.from()) && !date.isAfter(range.to());
                })
                .toList();
    }

    private Map<UUID, Warehouse> warehouses(List<WarehouseStock> stocks,
                                           List<InventoryReplenishmentRecommendationDto> recommendations) {
        Set<UUID> ids = stocks.stream().map(WarehouseStock::getWarehouseId).filter(Objects::nonNull).collect(Collectors.toSet());
        recommendations.stream().map(InventoryReplenishmentRecommendationDto::warehouseId).filter(Objects::nonNull).forEach(ids::add);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return warehouseRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));
    }

    private Map<UUID, SparePart> spareParts(List<WarehouseStock> stocks,
                                            List<InventoryReplenishmentRecommendationDto> recommendations,
                                            List<StockMovement> movements) {
        Set<UUID> ids = stocks.stream().map(WarehouseStock::getSparePartId).filter(Objects::nonNull).collect(Collectors.toSet());
        recommendations.stream().map(InventoryReplenishmentRecommendationDto::sparePartId).filter(Objects::nonNull).forEach(ids::add);
        movements.stream().map(StockMovement::getSparePartId).filter(Objects::nonNull).forEach(ids::add);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return sparePartRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(SparePart::getId, Function.identity()));
    }

    private Map<UUID, WorkOrder> workOrders(List<Reservation> reservations) {
        Set<UUID> ids = reservations.stream().map(Reservation::getWorkOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return workOrderRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(WorkOrder::getId, Function.identity()));
    }

    private Map<UUID, Equipment> equipment(Iterable<WorkOrder> workOrders) {
        List<WorkOrder> rows = new ArrayList<>();
        workOrders.forEach(rows::add);
        Set<UUID> ids = rows.stream()
                .map(WorkOrder::getEquipmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return equipmentRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(Equipment::getId, Function.identity()));
    }

    private Set<UUID> movedPartIds(List<StockMovement> movements) {
        return movements.stream()
                .map(StockMovement::getSparePartId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private boolean matchesSearch(SparePart part, String search, String fallbackCode, String fallbackName) {
        return search == null
                || contains(fallbackCode, search)
                || contains(fallbackName, search)
                || (part != null && (contains(part.getCode(), search)
                || contains(part.getName(), search)
                || contains(part.getSku(), search)
                || contains(part.getManufacturer(), search)
                || contains(part.getSpecification(), search)));
    }

    private boolean matchesCategory(SparePart part, String categoryId) {
        String normalized = normalize(categoryId);
        if (normalized == null) {
            return true;
        }
        if (part == null) {
            return false;
        }
        if (part.getType() != null && part.getType().getId() != null
                && part.getType().getId().toString().equalsIgnoreCase(normalized)) {
            return true;
        }
        return part.getLegacyType() != null && part.getLegacyType().equalsIgnoreCase(normalized);
    }

    private boolean matchesStatus(String actual, String requested) {
        String normalized = normalize(requested);
        return normalized == null || actual.equalsIgnoreCase(normalized);
    }

    private String statusForStock(WarehouseStock stock,
                                  SparePart part,
                                  Map<LegacyStockProjectionService.StockKey, WmsStockSnapshot> stockSnapshots) {
        if (isDeficit(stock, part, stockSnapshots)) {
            return isCritical(part) ? "CRITICAL" : "WARNING";
        }
        return "NORMAL";
    }

    private String statusForDeficit(SparePart part) {
        return isCritical(part) ? "CRITICAL" : "WARNING";
    }

    private String statusForReservation(SparePart part, boolean deficit) {
        if (deficit) {
            return isCritical(part) ? "CRITICAL" : "WARNING";
        }
        return isCritical(part) ? "CRITICAL" : "NORMAL";
    }

    private boolean isCritical(SparePart part) {
        return part != null && part.getCriticality() == CriticalityLevel.CRITICAL;
    }

    private boolean isDeficit(WarehouseStock stock,
                              SparePart part,
                              Map<LegacyStockProjectionService.StockKey, WmsStockSnapshot> stockSnapshots) {
        double trigger = stock.getReorderPoint() != null && stock.getReorderPoint() > 0
                ? stock.getReorderPoint()
                : stock.getMinQty() > 0 ? stock.getMinQty() : part == null ? 0 : part.getMinStock();
        return trigger > 0 && usableAvailable(stock, stockSnapshots) <= trigger;
    }

    private double usableAvailable(WarehouseStock stock,
                                   Map<LegacyStockProjectionService.StockKey, WmsStockSnapshot> stockSnapshots) {
        WmsStockSnapshot snapshot = legacyStockProjectionService.snapshot(
                stockSnapshots,
                stock.getWarehouseId(),
                stock.getSparePartId()
        );
        return snapshot == null ? stock.getAvailable() : snapshot.availableQty().doubleValue();
    }

    private Map<LegacyStockProjectionService.StockKey, WmsStockSnapshot> stockSnapshots(UUID warehouseId) {
        Map<LegacyStockProjectionService.StockKey, WmsStockSnapshot> snapshots = warehouseId == null
                ? legacyStockProjectionService.currentAll()
                : legacyStockProjectionService.currentForWarehouse(warehouseId);
        return snapshots == null ? Map.of() : snapshots;
    }

    private String dominantReason(List<StockMovement> movements) {
        boolean hasEmergency = movements.stream().anyMatch(item -> contains(item.getNotes(), "авар") || contains(item.getComment(), "авар"));
        if (hasEmergency) {
            return "Аварийный ремонт";
        }
        boolean hasWorkOrder = movements.stream().anyMatch(item -> item.getWorkOrderId() != null);
        return hasWorkOrder ? "Плановый ремонт" : "Техническое обслуживание";
    }

    private String statusForWorkOrder(WorkOrder workOrder) {
        return switch (workOrder.getStatus()) {
            case IN_PROGRESS -> "IN_WORK";
            case DRAFT, PLANNED, APPROVED -> "PLANNED";
            case COMPLETED, CLOSED -> "FULFILLED";
            default -> "WAITING_START";
        };
    }

    private PeriodRange range(String period) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String normalized = period == null ? "MONTH" : period.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "TODAY" -> new PeriodRange(today, today);
            case "WEEK" -> new PeriodRange(today.minusDays(6), today);
            case "QUARTER" -> new PeriodRange(today.minusDays(89), today);
            case "YEAR" -> new PeriodRange(today.minusDays(364), today);
            default -> new PeriodRange(today.minusDays(29), today);
        };
    }

    private int daysBetween(PeriodRange range) {
        return (int) ChronoUnit.DAYS.between(range.from(), range.to()) + 1;
    }

    private LocalDate movementDate(StockMovement movement) {
        if (movement.getMovementDate() != null) {
            return movement.getMovementDate();
        }
        Instant occurredAt = movement.getOccurredAt() == null ? Instant.now() : movement.getOccurredAt();
        return occurredAt.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean contains(String value, String needle) {
        return value != null && needle != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private double safe(Double value) {
        return value == null ? 0 : value;
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    private String format(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private record PeriodRange(LocalDate from, LocalDate to) {
        Instant fromInstant() {
            return from.atStartOfDay().toInstant(ZoneOffset.UTC);
        }

        Instant toInstant() {
            return to.plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC);
        }
    }

    private static class MovementBucket {
        double receipt;
        double issue;
        double reserved;
    }

    private static class AbcXyzAccumulator {
        final String abcClass;
        final String xyzClass;
        long count;
        double value;
        long riskCount;

        AbcXyzAccumulator(String abcClass, String xyzClass) {
            this.abcClass = abcClass;
            this.xyzClass = xyzClass;
        }
    }
}
