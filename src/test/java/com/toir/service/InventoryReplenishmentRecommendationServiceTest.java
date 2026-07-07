package com.toir.service;

import com.toir.dto.sparepartforecast.SparePartForecastItemDto;
import com.toir.dto.sparepartforecast.SparePartForecastSourceDto;
import com.toir.dto.sparepartforecast.SparePartForecastSummaryDto;
import com.toir.dto.warehouse.InventoryReplenishmentReason;
import com.toir.dto.warehouse.InventoryReplenishmentRecommendationDto;
import com.toir.dto.warehouse.ReorderSuggestionDto;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.NotificationSeverity;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.service.maintanance.SparePartForecastService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryReplenishmentRecommendationServiceTest {

    @Mock
    WarehouseReorderService reorderService;

    @Mock
    SparePartForecastService forecastService;

    @Mock
    SparePartRepository sparePartRepository;

    @Mock
    WarehouseStockRepository stockRepository;

    @Mock
    CounteragentService counteragentService;

    @InjectMocks
    InventoryReplenishmentRecommendationService service;

    @Test
    void recommendationsMergeLowStockAndForecastBySparePartAndWarehouse() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        Instant dueAt = Instant.parse("2026-06-10T09:00:00Z");
        ReorderSuggestionDto reorder = new ReorderSuggestionDto(
                UUID.randomUUID(),
                warehouseId,
                "Central warehouse",
                sparePartId,
                "Bearing",
                "BRG-001",
                "pcs",
                10.0,
                6.0,
                5.0,
                8.0,
                20.0,
                2.0,
                20.0,
                "WARNING",
                6.0,
                0.0,
                8.0,
                5.0,
                null,
                "LOW_STOCK"
        );
        SparePartForecastItemDto forecast = new SparePartForecastItemDto(
                sparePartId,
                "BRG-001",
                "Bearing",
                warehouseId,
                "Central warehouse",
                7.0,
                6.0,
                4.0,
                1.0,
                "pcs",
                NotificationSeverity.WARNING,
                dueAt,
                1,
                List.of(new SparePartForecastSourceDto(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Pump",
                        null,
                        null,
                        dueAt,
                        7.0
                ))
        );

        when(reorderService.allSuggestions(warehouseId)).thenReturn(List.of(reorder));
        when(forecastService.forecast(any()))
                .thenReturn(new SparePartForecastSummaryDto(now, now.plusSeconds(30L * 24 * 60 * 60), List.of(forecast)));

        Page<InventoryReplenishmentRecommendationDto> result = service.recommendations(
                30,
                now,
                null,
                warehouseId,
                true,
                0,
                20
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        InventoryReplenishmentRecommendationDto item = result.getContent().getFirst();
        assertThat(item.sparePartId()).isEqualTo(sparePartId);
        assertThat(item.warehouseId()).isEqualTo(warehouseId);
        assertThat(item.currentStock()).isEqualTo(10.0);
        assertThat(item.reservedStock()).isEqualTo(4.0);
        assertThat(item.availableStock()).isEqualTo(6.0);
        assertThat(item.maintenanceDemandQty()).isEqualTo(7.0);
        assertThat(item.maintenanceShortageQty()).isEqualTo(1.0);
        assertThat(item.projectedBalance()).isEqualTo(-1.0);
        assertThat(item.totalShortageQty()).isEqualTo(9.0);
        assertThat(item.suggestedOrderQty()).isEqualTo(20.0);
        assertThat(item.severity()).isEqualTo(NotificationSeverity.WARNING);
        assertThat(item.reason()).isEqualTo(InventoryReplenishmentReason.LOW_STOCK_AND_MAINTENANCE_FORECAST);
        assertThat(item.sourceCount()).isEqualTo(1);
        assertThat(item.firstDueAt()).isEqualTo(dueAt);
        verify(reorderService).allSuggestions(warehouseId);
    }

    @Test
    void recommendationsKeepEnterpriseForecastSeparateWhenWarehouseFilterIsMissing() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        ReorderSuggestionDto reorder = new ReorderSuggestionDto(
                UUID.randomUUID(),
                warehouseId,
                "Central warehouse",
                sparePartId,
                "Bearing",
                "BRG-001",
                "pcs",
                3.0,
                3.0,
                5.0,
                null,
                null,
                2.0,
                2.0,
                "CRITICAL"
        );
        SparePartForecastItemDto enterpriseForecast = new SparePartForecastItemDto(
                sparePartId,
                "BRG-001",
                "Bearing",
                null,
                null,
                4.0,
                10.0,
                1.0,
                0.0,
                "pcs",
                NotificationSeverity.INFO,
                now.plusSeconds(3600),
                1,
                List.of()
        );

        when(reorderService.allSuggestions(null)).thenReturn(List.of(reorder));
        when(forecastService.forecast(any()))
                .thenReturn(new SparePartForecastSummaryDto(now, now.plusSeconds(30L * 24 * 60 * 60), List.of(enterpriseForecast)));

        Page<InventoryReplenishmentRecommendationDto> result = service.recommendations(
                30,
                now,
                null,
                null,
                false,
                0,
                20
        );

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(InventoryReplenishmentRecommendationDto::reason)
                .containsExactly(
                        InventoryReplenishmentReason.LOW_STOCK,
                        InventoryReplenishmentReason.MAINTENANCE_FORECAST
                );
        InventoryReplenishmentRecommendationDto forecastRow = result.getContent().get(1);
        assertThat(forecastRow.warehouseId()).isNull();
        assertThat(forecastRow.warehouseName()).isEqualTo("Enterprise");
        assertThat(forecastRow.totalShortageQty()).isZero();
        assertThat(forecastRow.severity()).isEqualTo(NotificationSeverity.INFO);
    }


    @Test
    void recommendationsSortBeforePaginationByComputedAndTextFields() {
        UUID warehouseId = UUID.randomUUID();
        UUID alphaId = UUID.randomUUID();
        UUID betaId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        ReorderSuggestionDto alpha = new ReorderSuggestionDto(
                UUID.randomUUID(),
                warehouseId,
                "Zulu warehouse",
                alphaId,
                "Alpha bearing",
                "SP-002",
                "pcs",
                12.0,
                3.0,
                5.0,
                8.0,
                10.0,
                9.0,
                10.0,
                "WARNING"
        );
        ReorderSuggestionDto beta = new ReorderSuggestionDto(
                UUID.randomUUID(),
                warehouseId,
                "Alpha warehouse",
                betaId,
                "Beta bearing",
                "SP-001",
                "pcs",
                20.0,
                15.0,
                5.0,
                8.0,
                10.0,
                5.0,
                10.0,
                "CRITICAL"
        );

        when(reorderService.allSuggestions(warehouseId)).thenReturn(List.of(alpha, beta));
        when(forecastService.forecast(any()))
                .thenReturn(new SparePartForecastSummaryDto(now, now.plusSeconds(30L * 24 * 60 * 60), List.of()));

        assertThat(service.recommendations(30, now, null, warehouseId, true, 0, 1, "warehouseName", "asc")
                .getContent())
                .extracting(InventoryReplenishmentRecommendationDto::sparePartId)
                .containsExactly(betaId);
        assertThat(service.recommendations(30, now, null, warehouseId, true, 0, 1, "nonAvailableStock", "desc")
                .getContent())
                .extracting(InventoryReplenishmentRecommendationDto::sparePartId)
                .containsExactly(alphaId);
    }

    @Test
    void recommendationsUseReorderRecommendedQuantityForCatalogMinLowStockRows() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        ReorderSuggestionDto reorder = new ReorderSuggestionDto(
                UUID.randomUUID(),
                warehouseId,
                "Central warehouse",
                sparePartId,
                "Catalog minimum part",
                "SP-MIN",
                "pcs",
                5.0,
                5.0,
                5.0,
                null,
                null,
                0.0,
                5.0,
                "WARNING"
        );

        when(reorderService.allSuggestions(warehouseId)).thenReturn(List.of(reorder));
        when(forecastService.forecast(any()))
                .thenReturn(new SparePartForecastSummaryDto(now, now.plusSeconds(30L * 24 * 60 * 60), List.of()));

        Page<InventoryReplenishmentRecommendationDto> result = service.recommendations(
                30,
                now,
                null,
                warehouseId,
                true,
                0,
                20
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        InventoryReplenishmentRecommendationDto item = result.getContent().getFirst();
        assertThat(item.reason()).isEqualTo(InventoryReplenishmentReason.LOW_STOCK);
        assertThat(item.totalShortageQty()).isZero();
        assertThat(item.suggestedOrderQty()).isEqualTo(5.0);
        assertThat(item.recommendedQuantity()).isEqualTo(5.0);
        assertThat(item.severity()).isEqualTo(NotificationSeverity.WARNING);
    }

    @Test
    void recommendationsDoNotCountNonAvailableStockAsReservedStock() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        ReorderSuggestionDto reorder = new ReorderSuggestionDto(
                UUID.randomUUID(),
                warehouseId,
                "Central warehouse",
                sparePartId,
                "Blocked bearing",
                "BRG-BLOCK",
                "pcs",
                20.0,
                3.0,
                5.0,
                8.0,
                null,
                5.0,
                13.0,
                "WARNING",
                3.0,
                10.0,
                8.0,
                5.0,
                null,
                "LOW_STOCK"
        );

        when(reorderService.allSuggestions(warehouseId)).thenReturn(List.of(reorder));
        when(forecastService.forecast(any()))
                .thenReturn(new SparePartForecastSummaryDto(now, now.plusSeconds(30L * 24 * 60 * 60), List.of()));

        Page<InventoryReplenishmentRecommendationDto> result = service.recommendations(
                30,
                now,
                null,
                warehouseId,
                true,
                0,
                20
        );

        InventoryReplenishmentRecommendationDto item = result.getContent().getFirst();
        assertThat(item.currentStock()).isEqualTo(20.0);
        assertThat(item.availableStock()).isEqualTo(3.0);
        assertThat(item.reservedStock()).isEqualTo(7.0);
    }

    @Test
    void recommendationsUseEffectiveReorderPointForMinQtyLowStockRows() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        ReorderSuggestionDto reorder = new ReorderSuggestionDto(
                UUID.randomUUID(),
                warehouseId,
                "Central warehouse",
                sparePartId,
                "Min quantity part",
                "SP-MIN-QTY",
                "pcs",
                6.0,
                4.0,
                5.0,
                null,
                null,
                1.0,
                6.0,
                "CRITICAL",
                4.0,
                0.0,
                5.0,
                5.0,
                null,
                "LOW_STOCK"
        );

        when(reorderService.allSuggestions(warehouseId)).thenReturn(List.of(reorder));
        when(forecastService.forecast(any()))
                .thenReturn(new SparePartForecastSummaryDto(now, now.plusSeconds(30L * 24 * 60 * 60), List.of()));

        Page<InventoryReplenishmentRecommendationDto> result = service.recommendations(
                30,
                now,
                null,
                warehouseId,
                true,
                0,
                20
        );

        InventoryReplenishmentRecommendationDto item = result.getContent().getFirst();
        assertThat(item.minStock()).isEqualTo(5.0);
        assertThat(item.reorderPoint()).isEqualTo(5.0);
    }

    @Test
    void recommendationsIncludePolicyForForecastOnlyWarehouseRows() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        Instant dueAt = Instant.parse("2026-06-10T09:00:00Z");
        SparePartForecastItemDto forecast = new SparePartForecastItemDto(
                sparePartId,
                "BRG-FORECAST",
                "Forecast bearing",
                warehouseId,
                "Central warehouse",
                12.0,
                20.0,
                2.0,
                0.0,
                "pcs",
                NotificationSeverity.INFO,
                dueAt,
                1,
                List.of()
        );
        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setMinQty(5.0);
        stock.setReorderPoint(8.0);
        stock.setReorderQty(15.0);

        when(reorderService.allSuggestions(warehouseId)).thenReturn(List.of());
        when(forecastService.forecast(any()))
                .thenReturn(new SparePartForecastSummaryDto(now, now.plusSeconds(30L * 24 * 60 * 60), List.of(forecast)));
        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));

        Page<InventoryReplenishmentRecommendationDto> result = service.recommendations(
                30,
                now,
                null,
                warehouseId,
                false,
                0,
                20
        );

        InventoryReplenishmentRecommendationDto item = result.getContent().getFirst();
        assertThat(item.reason()).isEqualTo(InventoryReplenishmentReason.MAINTENANCE_FORECAST);
        assertThat(item.minStock()).isEqualTo(5.0);
        assertThat(item.reorderPoint()).isEqualTo(8.0);
        assertThat(item.reorderQty()).isEqualTo(15.0);
    }
}
