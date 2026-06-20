package com.toir.service;

import com.toir.entity.SparePart;
import com.toir.repository.SparePartRepository;
import com.toir.service.warehouse.LegacyStockProjectionService;
import com.toir.service.warehouse.WmsStockSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryCostServiceTest {

    @Mock
    SparePartRepository sparePartRepository;

    @Mock
    LegacyStockProjectionService legacyStockProjectionService;

    @Test
    void receiptRecalculatesWeightedAverageCostAndInventoryValue() {
        UUID sparePartId = UUID.randomUUID();
        SparePart sparePart = new SparePart();
        sparePart.setId(sparePartId);
        sparePart.setAverageCost(BigDecimal.valueOf(50000));
        UUID warehouseId = UUID.randomUUID();
        when(legacyStockProjectionService.currentForSparePart(sparePartId)).thenReturn(Map.of(
                new LegacyStockProjectionService.StockKey(warehouseId, sparePartId),
                new WmsStockSnapshot(warehouseId, sparePartId, BigDecimal.valueOf(150), BigDecimal.ZERO)
        ));
        when(legacyStockProjectionService.totalAvailable(any())).thenReturn(BigDecimal.valueOf(150));

        InventoryCostService service = new InventoryCostService(sparePartRepository, legacyStockProjectionService);
        service.applyReceiptCost(sparePart, BigDecimal.valueOf(100), BigDecimal.valueOf(50), BigDecimal.valueOf(60000));

        assertThat(sparePart.getAverageCost()).isEqualByComparingTo("53333.33");
        assertThat(sparePart.getLastPurchaseCost()).isEqualByComparingTo("60000");
        assertThat(sparePart.getInventoryValue()).isEqualByComparingTo("7999999.50");
        verify(sparePartRepository).save(sparePart);
    }
}
