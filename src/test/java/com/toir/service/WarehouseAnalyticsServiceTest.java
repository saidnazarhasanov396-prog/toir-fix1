package com.toir.service;

import com.toir.dto.inventory.InventoryAbcAnalysisDto;
import com.toir.dto.inventory.InventoryStockoutRiskDto;
import com.toir.dto.inventory.InventoryXyzAnalysisDto;
import com.toir.dto.warehouse.InventoryReplenishmentReason;
import com.toir.dto.warehouse.InventoryReplenishmentRecommendationDto;
import com.toir.dto.warehouse.SparePartsWarehouseStatsResponse;
import com.toir.dto.warehouseanalytics.WarehouseAnalyticsFilter;
import com.toir.dto.warehouseanalytics.WarehouseAnalyticsKpiDto;
import com.toir.dto.warehouseanalytics.WarehouseAnalyticsOverviewDto;
import com.toir.entity.SparePart;
import com.toir.entity.SparePartType;
import com.toir.entity.StockMovement;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.InventoryItemKind;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.ReservationStatus;
import com.toir.repository.ReservationRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.service.warehouse.LegacyStockProjectionService;
import com.toir.service.warehouse.WmsStockSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseAnalyticsServiceTest {

    @Mock
    WarehouseSparePartsStatsService sparePartsStatsService;

    @Mock
    InventoryReplenishmentRecommendationService replenishmentService;

    @Mock
    InventoryAnalyticsService inventoryAnalyticsService;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    WarehouseStockRepository stockRepository;

    @Mock
    SparePartRepository sparePartRepository;

    @Mock
    StockMovementRepository stockMovementRepository;

    @Mock
    ReservationRepository reservationRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    LegacyStockProjectionService legacyStockProjectionService;

    @InjectMocks
    WarehouseAnalyticsService service;

    @BeforeEach
    void setUpCommonMocks() {
        when(sparePartsStatsService.getStats(
                nullable(UUID.class),
                nullable(String.class),
                nullable(UUID.class),
                nullable(String.class),
                nullable(String.class)
        )).thenReturn(new SparePartsWarehouseStatsResponse(0, 0, 0, 0));
        when(inventoryAnalyticsService.abcAnalysis()).thenReturn(List.<InventoryAbcAnalysisDto>of());
        when(inventoryAnalyticsService.xyzAnalysis()).thenReturn(List.<InventoryXyzAnalysisDto>of());
        when(inventoryAnalyticsService.stockoutRisk()).thenReturn(List.<InventoryStockoutRiskDto>of());
        when(reservationRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ReservationStatus.ACTIVE)).thenReturn(List.of());
    }

    @Test
    void overviewKeepsCriticalityForRecommendationWithoutStockRow() {
        UUID sparePartId = UUID.randomUUID();
        WarehouseAnalyticsFilter filter = new WarehouseAnalyticsFilter();
        filter.setOnlyCritical(true);
        SparePart criticalPart = sparePart(sparePartId, "BRG-CRIT", "Critical bearing", CriticalityLevel.CRITICAL);
        InventoryReplenishmentRecommendationDto recommendation = recommendation(sparePartId, null, "BRG-CRIT", "Critical bearing", 0, 12, 12);

        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(stockMovementRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.<StockMovement>of());
        when(replenishmentService.recommendationRows(anyInt(), any(Instant.class), any(Instant.class), isNull(), org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(List.of(recommendation));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(criticalPart));

        WarehouseAnalyticsOverviewDto result = service.overview(filter);

        assertThat(result.deficits()).hasSize(1);
        assertThat(result.deficits().get(0).criticality()).isEqualTo("CRITICAL");
        assertThat(kpi(result, "criticalItems").value()).isEqualTo(1.0);
    }

    @Test
    void overviewAppliesReserveCriticalNoMovementAndStatusFiltersToWarehouseSlice() {
        UUID warehouseId = UUID.randomUUID();
        UUID criticalPartId = UUID.randomUUID();
        UUID normalPartId = UUID.randomUUID();
        WarehouseAnalyticsFilter filter = new WarehouseAnalyticsFilter();
        filter.setOnlyCritical(true);
        filter.setHasReserve(true);
        filter.setNoMovement(true);
        filter.setStatus("CRITICAL");
        SparePart criticalPart = sparePart(criticalPartId, "CRT", "Critical seal", CriticalityLevel.CRITICAL);
        SparePart normalPart = sparePart(normalPartId, "NRM", "Normal seal", CriticalityLevel.LOW);
        WarehouseStock criticalStock = stock(warehouseId, criticalPartId, 3, 1, 5);
        WarehouseStock normalStock = stock(warehouseId, normalPartId, 3, 1, 5);
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setName("Main warehouse");

        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(criticalStock, normalStock));
        when(stockMovementRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.<StockMovement>of());
        when(replenishmentService.recommendationRows(anyInt(), any(Instant.class), any(Instant.class), isNull(), org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(List.of());
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(criticalPart, normalPart));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(warehouse));

        WarehouseAnalyticsOverviewDto result = service.overview(filter);

        assertThat(result.warehouseDistribution()).hasSize(1);
        assertThat(result.warehouseDistribution().get(0).warehouseId()).isEqualTo(warehouseId);
        assertThat(result.warehouseDistribution().get(0).positions()).isEqualTo(1);
        assertThat(result.warehouseDistribution().get(0).status()).isEqualTo("CRITICAL");
        assertThat(kpi(result, "criticalItems").value()).isEqualTo(1.0);
    }

    @Test
    void overviewUsesWmsUsableAvailabilityForWarehouseDeficitStatus() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseAnalyticsFilter filter = new WarehouseAnalyticsFilter();
        filter.setOnlyDeficit(true);
        SparePart part = sparePart(sparePartId, "AVL", "Available only part", CriticalityLevel.LOW);
        WarehouseStock stock = stock(warehouseId, sparePartId, 20, 0, 5);
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setName("Main warehouse");
        Map<LegacyStockProjectionService.StockKey, WmsStockSnapshot> snapshots = Map.of();

        when(stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(stock));
        when(stockMovementRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.<StockMovement>of());
        when(replenishmentService.recommendationRows(anyInt(), any(Instant.class), any(Instant.class), isNull(), org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(List.of());
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(part));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(warehouse));
        when(legacyStockProjectionService.currentAll()).thenReturn(snapshots);
        when(legacyStockProjectionService.snapshot(snapshots, warehouseId, sparePartId)).thenReturn(new WmsStockSnapshot(
                warehouseId,
                sparePartId,
                BigDecimal.valueOf(20),
                BigDecimal.ZERO,
                BigDecimal.valueOf(3),
                BigDecimal.ZERO
        ));

        WarehouseAnalyticsOverviewDto result = service.overview(filter);

        assertThat(result.warehouseDistribution()).hasSize(1);
        assertThat(result.warehouseDistribution().getFirst().deficitCount()).isEqualTo(1);
        assertThat(result.warehouseDistribution().getFirst().status()).isEqualTo("WARNING");
    }

    private WarehouseAnalyticsKpiDto kpi(WarehouseAnalyticsOverviewDto result, String key) {
        return result.kpis().stream()
                .filter(item -> key.equals(item.key()))
                .findFirst()
                .orElseThrow();
    }

    private SparePart sparePart(UUID id, String code, String name, CriticalityLevel criticality) {
        SparePartType type = new SparePartType();
        type.setId(UUID.randomUUID());
        SparePart part = new SparePart();
        part.setId(id);
        part.setCode(code);
        part.setName(name);
        part.setKind(InventoryItemKind.SPARE_PART);
        part.setType(type);
        part.setLegacyType("OTHER");
        part.setUnit("pcs");
        part.setCriticality(criticality);
        return part;
    }

    private WarehouseStock stock(UUID warehouseId, UUID sparePartId, double quantity, double reserved, double minQty) {
        WarehouseStock stock = new WarehouseStock();
        stock.setId(UUID.randomUUID());
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(quantity);
        stock.setReservedQty(reserved);
        stock.setMinQty(minQty);
        return stock;
    }

    private InventoryReplenishmentRecommendationDto recommendation(UUID sparePartId,
                                                                   UUID warehouseId,
                                                                   String code,
                                                                   String name,
                                                                   double available,
                                                                   double minimum,
                                                                   double shortage) {
        return new InventoryReplenishmentRecommendationDto(
                sparePartId,
                code,
                name,
                warehouseId,
                warehouseId == null ? null : "Main warehouse",
                available,
                0,
                available,
                minimum,
                minimum,
                null,
                0,
                0,
                available - shortage,
                shortage,
                shortage,
                null,
                null,
                null,
                NotificationSeverity.CRITICAL,
                InventoryReplenishmentReason.LOW_STOCK,
                0,
                null,
                List.of()
        );
    }
}
