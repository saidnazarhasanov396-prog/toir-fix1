package com.toir.service;

import com.toir.entity.SparePart;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseStockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryCostServiceTest {

    @Mock
    SparePartRepository sparePartRepository;

    @Mock
    WarehouseStockRepository stockRepository;

    @Test
    void receiptRecalculatesWeightedAverageCostAndInventoryValue() {
        UUID sparePartId = UUID.randomUUID();
        SparePart sparePart = new SparePart();
        sparePart.setId(sparePartId);
        sparePart.setAverageCost(BigDecimal.valueOf(50000));
        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(UUID.randomUUID());
        stock.setSparePart(sparePart);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(150);
        stock.setReservedQty(0);

        when(stockRepository.findAllBySparePartIdAndIsDeletedFalse(sparePartId)).thenReturn(List.of(stock));

        InventoryCostService service = new InventoryCostService(sparePartRepository, stockRepository);
        service.applyReceiptCost(sparePart, BigDecimal.valueOf(100), BigDecimal.valueOf(50), BigDecimal.valueOf(60000));

        assertThat(sparePart.getAverageCost()).isEqualByComparingTo("53333.33");
        assertThat(sparePart.getLastPurchaseCost()).isEqualByComparingTo("60000");
        assertThat(sparePart.getInventoryValue()).isEqualByComparingTo("7999999.50");
        verify(sparePartRepository).save(sparePart);
    }
}
