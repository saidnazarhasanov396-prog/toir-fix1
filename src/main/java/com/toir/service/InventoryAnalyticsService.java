package com.toir.service;

import com.toir.dto.inventory.InventoryAbcAnalysisDto;
import com.toir.dto.inventory.InventoryKpiDto;
import com.toir.dto.inventory.InventoryMovementAnalyticsDto;
import com.toir.dto.inventory.InventoryStockoutRiskDto;
import com.toir.dto.inventory.InventoryValuationDto;
import com.toir.dto.inventory.InventoryValuationItemDto;
import com.toir.dto.inventory.InventoryXyzAnalysisDto;
import com.toir.dto.sparepart.SparePartAnalyticsDto;
import com.toir.entity.SparePart;
import com.toir.entity.StockMovement;
import com.toir.entity.warehouse.Warehouse;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.InventoryMovementClass;
import com.toir.enums.StockMovementType;
import com.toir.enums.StockoutRiskLevel;
import com.toir.exception.RestException;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.warehouse.LegacyStockProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryAnalyticsService {

    private static final int DEAD_STOCK_MONTHS = 6;

    private final SparePartRepository sparePartRepository;
    private final StockMovementRepository movementRepository;
    private final WarehouseRepository warehouseRepository;
    private final ScopeAccessService scopeAccessService;
    private final InventoryCostService inventoryCostService;
    private final LegacyStockProjectionService legacyStockProjectionService;

    @Transactional(readOnly = true)
    public InventoryValuationDto valuation() {
        List<InventoryValuationItemDto> items = valuationItems();
        BigDecimal totalValue = items.stream().map(InventoryValuationItemDto::inventoryValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalQuantity = items.stream().map(InventoryValuationItemDto::availableQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new InventoryValuationDto(
                totalValue,
                items.size(),
                totalQuantity,
                items.stream()
                        .sorted(Comparator.comparing(InventoryValuationItemDto::inventoryValue).reversed())
                        .limit(10)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public SparePartAnalyticsDto sparePartAnalytics(UUID sparePartId) {
        SparePart sparePart = sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)
                .orElseThrow(() -> RestException.notFound("Spare part not found: " + sparePartId));
        List<StockMovement> issues = scopedMovements().stream()
                .filter(movement -> sparePartId.equals(movement.getSparePartId()))
                .filter(movement -> movement.getType() == StockMovementType.ISSUE)
                .toList();
        Map<YearMonthKey, List<StockMovement>> byMonth = issues.stream()
                .collect(Collectors.groupingBy(movement -> {
                    LocalDate date = movementDate(movement);
                    return new YearMonthKey(date.getYear(), date.getMonthValue());
                }, LinkedHashMap::new, Collectors.toList()));
        List<SparePartAnalyticsDto.MonthlyConsumptionDto> monthly = byMonth.entrySet().stream()
                .map(entry -> new SparePartAnalyticsDto.MonthlyConsumptionDto(
                        entry.getKey().year(),
                        entry.getKey().month(),
                        quantity(entry.getValue()),
                        amount(entry.getValue(), sparePart)
                ))
                .sorted(Comparator.comparing(SparePartAnalyticsDto.MonthlyConsumptionDto::year)
                        .thenComparing(SparePartAnalyticsDto.MonthlyConsumptionDto::month))
                .toList();
        BigDecimal totalQty = quantity(issues);
        BigDecimal totalAmount = amount(issues, sparePart);
        BigDecimal averageMonthly = monthly.isEmpty()
                ? BigDecimal.ZERO
                : totalQty.divide(BigDecimal.valueOf(monthly.size()), 2, RoundingMode.HALF_UP);
        return new SparePartAnalyticsDto(sparePart.getId(), sparePart.getCode(), sparePart.getName(),
                totalQty, totalAmount, monthly, averageMonthly);
    }

    @Transactional(readOnly = true)
    public List<InventoryMovementAnalyticsDto> fastMoving() {
        return movementAnalytics().stream()
                .filter(item -> item.movementClass() == InventoryMovementClass.FAST)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryMovementAnalyticsDto> slowMoving() {
        return movementAnalytics().stream()
                .filter(item -> item.movementClass() == InventoryMovementClass.SLOW)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryMovementAnalyticsDto> deadStock() {
        return movementAnalytics().stream()
                .filter(item -> item.movementClass() == InventoryMovementClass.DEAD)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryAbcAnalysisDto> abcAnalysis() {
        List<InventoryAbcAnalysisDto> rows = sparePartsInScope().stream()
                .map(part -> new InventoryAbcAnalysisDto(part.getId(), part.getCode(), part.getName(), "C", annualConsumptionValue(part)))
                .sorted(Comparator.comparing(InventoryAbcAnalysisDto::annualConsumptionValue).reversed())
                .toList();
        BigDecimal total = rows.stream().map(InventoryAbcAnalysisDto::annualConsumptionValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return rows;
        }
        BigDecimal running = BigDecimal.ZERO;
        List<InventoryAbcAnalysisDto> result = new ArrayList<>();
        for (InventoryAbcAnalysisDto row : rows) {
            BigDecimal previousCumulative = running.divide(total, 4, RoundingMode.HALF_UP);
            running = running.add(row.annualConsumptionValue());
            BigDecimal cumulative = running.divide(total, 4, RoundingMode.HALF_UP);
            String classification = previousCumulative.compareTo(BigDecimal.valueOf(0.80)) < 0 ? "A"
                    : cumulative.compareTo(BigDecimal.valueOf(0.95)) <= 0 ? "B" : "C";
            result.add(new InventoryAbcAnalysisDto(row.sparePartId(), row.sparePartCode(), row.sparePartName(),
                    classification, row.annualConsumptionValue()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<InventoryXyzAnalysisDto> xyzAnalysis() {
        return sparePartsInScope().stream()
                .map(this::xyzRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryStockoutRiskDto> stockoutRisk() {
        return sparePartsInScope().stream()
                .map(this::stockoutRiskRow)
                .filter(row -> row.riskLevel() != StockoutRiskLevel.LOW_RISK)
                .toList();
    }

    @Transactional(readOnly = true)
    public InventoryKpiDto kpis() {
        List<InventoryMovementAnalyticsDto> movements = movementAnalytics();
        List<InventoryAbcAnalysisDto> abc = abcAnalysis();
        List<InventoryXyzAnalysisDto> xyz = xyzAnalysis();
        List<InventoryStockoutRiskDto> risks = stockoutRisk();
        BigDecimal value = valuation().totalInventoryValue();
        return new InventoryKpiDto(
                value,
                movements.stream().filter(item -> item.movementClass() == InventoryMovementClass.FAST).count(),
                movements.stream().filter(item -> item.movementClass() == InventoryMovementClass.SLOW).count(),
                movements.stream().filter(item -> item.movementClass() == InventoryMovementClass.DEAD).count(),
                sparePartsInScope().stream().filter(part -> part.getCriticality() == CriticalityLevel.CRITICAL).count(),
                risks.size(),
                abc.stream().filter(row -> "A".equals(row.classification())).count(),
                xyz.stream().filter(row -> "Z".equals(row.classification())).count()
        );
    }

    private List<InventoryValuationItemDto> valuationItems() {
        return sparePartsInScope().stream()
                .map(part -> {
                    BigDecimal available = inventoryCostService.availableQuantity(part);
                    BigDecimal averageCost = zero(part.getAverageCost());
                    BigDecimal value = available.multiply(averageCost).setScale(2, RoundingMode.HALF_UP);
                    return new InventoryValuationItemDto(part.getId(), part.getCode(), part.getName(), available, averageCost, value);
                })
                .toList();
    }

    private List<InventoryMovementAnalyticsDto> movementAnalytics() {
        Map<UUID, SparePart> parts = sparePartsInScope().stream().collect(Collectors.toMap(SparePart::getId, Function.identity()));
        Map<UUID, List<StockMovement>> issuesByPart = scopedMovements().stream()
                .filter(movement -> movement.getType() == StockMovementType.ISSUE)
                .filter(movement -> parts.containsKey(movement.getSparePartId()))
                .collect(Collectors.groupingBy(StockMovement::getSparePartId));
        LocalDate deadBefore = LocalDate.now(ZoneOffset.UTC).minusMonths(DEAD_STOCK_MONTHS);
        return parts.values().stream()
                .map(part -> {
                    List<StockMovement> issues = issuesByPart.getOrDefault(part.getId(), List.of());
                    LocalDate lastMovementDate = issues.stream().map(this::movementDate).max(LocalDate::compareTo).orElse(null);
                    BigDecimal issuedQty = quantity(issues);
                    BigDecimal avgMonthly = averageMonthlyQuantity(issues);
                    InventoryMovementClass movementClass = movementClass(issuedQty, avgMonthly, lastMovementDate, deadBefore);
                    return new InventoryMovementAnalyticsDto(part.getId(), part.getCode(), part.getName(),
                            issuedQty, amount(issues, part), avgMonthly, lastMovementDate, movementClass);
                })
                .toList();
    }

    private InventoryMovementClass movementClass(BigDecimal issuedQty, BigDecimal avgMonthly, LocalDate lastMovementDate, LocalDate deadBefore) {
        if (lastMovementDate == null || lastMovementDate.isBefore(deadBefore)) {
            return InventoryMovementClass.DEAD;
        }
        return avgMonthly.compareTo(BigDecimal.valueOf(5)) >= 0 || issuedQty.compareTo(BigDecimal.valueOf(20)) >= 0
                ? InventoryMovementClass.FAST
                : InventoryMovementClass.SLOW;
    }

    private BigDecimal annualConsumptionValue(SparePart part) {
        LocalDate since = LocalDate.now(ZoneOffset.UTC).minusYears(1);
        BigDecimal annualQty = scopedMovements().stream()
                .filter(movement -> part.getId().equals(movement.getSparePartId()))
                .filter(movement -> movement.getType() == StockMovementType.ISSUE)
                .filter(movement -> !movementDate(movement).isBefore(since))
                .map(StockMovement::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return annualQty.multiply(zero(part.getAverageCost())).setScale(2, RoundingMode.HALF_UP);
    }

    private InventoryXyzAnalysisDto xyzRow(SparePart part) {
        Map<Integer, BigDecimal> months = new HashMap<>();
        for (int month = 1; month <= 12; month++) {
            months.put(month, BigDecimal.ZERO);
        }
        LocalDate since = LocalDate.now(ZoneOffset.UTC).minusYears(1);
        scopedMovements().stream()
                .filter(movement -> part.getId().equals(movement.getSparePartId()))
                .filter(movement -> movement.getType() == StockMovementType.ISSUE)
                .filter(movement -> !movementDate(movement).isBefore(since))
                .forEach(movement -> months.merge(movementDate(movement).getMonthValue(), movement.getQuantity(), BigDecimal::add));
        BigDecimal average = months.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
        double avg = average.doubleValue();
        double variance = months.values().stream()
                .mapToDouble(value -> Math.pow(value.doubleValue() - avg, 2))
                .average()
                .orElse(0);
        BigDecimal stdDev = BigDecimal.valueOf(Math.sqrt(variance)).setScale(4, RoundingMode.HALF_UP);
        BigDecimal coefficient = avg == 0 ? BigDecimal.ZERO : stdDev.divide(average, 4, RoundingMode.HALF_UP);
        String classification = coefficient.compareTo(BigDecimal.valueOf(0.50)) <= 0 ? "X"
                : coefficient.compareTo(BigDecimal.ONE) <= 0 ? "Y" : "Z";
        return new InventoryXyzAnalysisDto(part.getId(), part.getCode(), part.getName(), classification, average, stdDev, coefficient);
    }

    private InventoryStockoutRiskDto stockoutRiskRow(SparePart part) {
        BigDecimal available = inventoryCostService.availableQuantity(part);
        BigDecimal avgDaily = averageDailyConsumption(part);
        int leadTime = part.getLeadTimeDays() == null ? 0 : Math.max(part.getLeadTimeDays(), 0);
        BigDecimal expectedConsumption = avgDaily.multiply(BigDecimal.valueOf(leadTime)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal safetyStock = BigDecimal.valueOf(Math.max(part.getMinStock(), 0)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal daysRemaining = avgDaily.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.valueOf(9999)
                : available.divide(avgDaily, 2, RoundingMode.HALF_UP);
        BigDecimal required = expectedConsumption.add(safetyStock);
        StockoutRiskLevel risk = available.compareTo(BigDecimal.ZERO) <= 0 ? StockoutRiskLevel.CRITICAL_RISK
                : available.compareTo(expectedConsumption) < 0 ? StockoutRiskLevel.HIGH_RISK
                : available.compareTo(required) < 0 ? StockoutRiskLevel.MEDIUM_RISK
                : StockoutRiskLevel.LOW_RISK;
        if (part.getCriticality() == CriticalityLevel.CRITICAL && risk == StockoutRiskLevel.MEDIUM_RISK) {
            risk = StockoutRiskLevel.HIGH_RISK;
        }
        return new InventoryStockoutRiskDto(part.getId(), part.getCode(), part.getName(), available, avgDaily,
                leadTime, safetyStock, daysRemaining, expectedConsumption, risk, part.getCriticality());
    }

    private BigDecimal averageDailyConsumption(SparePart part) {
        LocalDate since = LocalDate.now(ZoneOffset.UTC).minusDays(90);
        BigDecimal issued = scopedMovements().stream()
                .filter(movement -> part.getId().equals(movement.getSparePartId()))
                .filter(movement -> movement.getType() == StockMovementType.ISSUE)
                .filter(movement -> !movementDate(movement).isBefore(since))
                .map(StockMovement::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return issued.divide(BigDecimal.valueOf(90), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal averageMonthlyQuantity(List<StockMovement> movements) {
        if (movements.isEmpty()) {
            return BigDecimal.ZERO;
        }
        LocalDate min = movements.stream().map(this::movementDate).min(LocalDate::compareTo).orElse(LocalDate.now(ZoneOffset.UTC));
        LocalDate max = movements.stream().map(this::movementDate).max(LocalDate::compareTo).orElse(LocalDate.now(ZoneOffset.UTC));
        long months = Math.max(1, ChronoUnit.MONTHS.between(min.withDayOfMonth(1), max.withDayOfMonth(1)) + 1);
        return quantity(movements).divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal quantity(List<StockMovement> movements) {
        return movements.stream().map(StockMovement::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal amount(List<StockMovement> movements, SparePart part) {
        return movements.stream()
                .map(movement -> IndustrialKpiAggregations.stockIssueCost(movement, part))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate movementDate(StockMovement movement) {
        if (movement.getMovementDate() != null) {
            return movement.getMovementDate();
        }
        return movement.getOccurredAt() == null
                ? LocalDate.now(ZoneOffset.UTC)
                : movement.getOccurredAt().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private List<SparePart> sparePartsInScope() {
        List<SparePart> parts = sparePartRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        if (scopeAccessService.isScopeAdmin()) {
            return parts;
        }
        List<UUID> scopedWarehouses = scopedWarehouseIds();
        List<UUID> scopedPartIds = legacyStockProjectionService.currentAll().keySet().stream()
                .filter(key -> scopedWarehouses.contains(key.warehouseId()))
                .map(LegacyStockProjectionService.StockKey::sparePartId)
                .distinct()
                .toList();
        return parts.stream().filter(part -> scopedPartIds.contains(part.getId())).toList();
    }

    private List<StockMovement> scopedMovements() {
        List<UUID> scopedWarehouses = scopedWarehouseIds();
        return movementRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(movement -> scopeAccessService.isScopeAdmin() || scopedWarehouses.contains(movement.getWarehouseId()))
                .toList();
    }

    private List<UUID> scopedWarehouseIds() {
        if (scopeAccessService.isScopeAdmin()) {
            return List.of();
        }
        return warehouseRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(this::canAccessWarehouse)
                .map(Warehouse::getId)
                .toList();
    }

    private boolean canAccessWarehouse(Warehouse warehouse) {
        if (warehouse == null) {
            return false;
        }
        return (warehouse.getDepartmentId() != null && scopeAccessService.canAccessDepartment(warehouse.getDepartmentId()))
                || (warehouse.getResponsibleId() != null && scopeAccessService.canAccessEmployee(warehouse.getResponsibleId()));
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record YearMonthKey(int year, int month) {
    }
}
