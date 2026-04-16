package com.toir.stockmovement;

import com.toir.common.exception.RestException;
import com.toir.stockmovement.dto.StockMovementRequest;
import com.toir.warehouse.WarehouseStock;
import com.toir.warehouse.WarehouseStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockMovementServiceTest {

    private StockMovementRepository movementRepository;
    private WarehouseStockRepository stockRepository;
    private StockMovementService service;

    private final UUID warehouseId = UUID.randomUUID();
    private final UUID sparePartId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        movementRepository = mock(StockMovementRepository.class);
        stockRepository = mock(WarehouseStockRepository.class);
        service = new StockMovementService(movementRepository, stockRepository);
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void receiptIncreasesQuantity() {
        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(10);
        when(stockRepository.findByWarehouseIdAndSparePartId(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));

        service.create(new StockMovementRequest(
                warehouseId, sparePartId, null, StockMovementType.RECEIPT, 5,
                null, null, null, null));

        assertThat(stock.getQuantity()).isEqualTo(15);
    }

    @Test
    void issueFailsWhenAvailableLessThanRequested() {
        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(3);
        stock.setReservedQty(0);
        when(stockRepository.findByWarehouseIdAndSparePartId(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));

        StockMovementRequest req = new StockMovementRequest(
                warehouseId, sparePartId, null, StockMovementType.ISSUE, 10,
                null, null, null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot issue more than available");
    }

    @Test
    void reservationBlocksWhenAvailableIsLow() {
        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(10);
        stock.setReservedQty(8);
        when(stockRepository.findByWarehouseIdAndSparePartId(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));

        StockMovementRequest req = new StockMovementRequest(
                warehouseId, sparePartId, null, StockMovementType.RESERVATION, 5,
                null, null, null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot reserve more than available");
    }
}
