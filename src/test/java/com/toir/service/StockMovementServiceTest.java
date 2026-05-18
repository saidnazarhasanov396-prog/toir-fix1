package com.toir.service;

import com.toir.dto.stockmovement.StockMovementRequest;
import com.toir.entity.StockMovement;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.StockMovementType;
import com.toir.exception.RestException;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockMovementServiceTest {

    @Mock
    StockMovementRepository repository;

    @Mock
    WarehouseStockRepository stockRepository;

    @Mock
    SparePartRepository sparePartRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    StockMovementService service;

    @Test
    void issueFailsWhenQuantityIsZeroOrNegative() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(request(warehouseId, sparePartId, StockMovementType.ISSUE, 0)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Quantity must be greater than 0");

        assertThatThrownBy(() -> service.create(request(warehouseId, sparePartId, StockMovementType.ISSUE, -1)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Quantity must be greater than 0");

        verifyNoInteractions(stockRepository, repository, sparePartRepository);
    }

    @Test
    void reservationFailsWhenQuantityIsZeroOrNegative() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(request(warehouseId, sparePartId, StockMovementType.RESERVATION, 0)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Quantity must be greater than 0");

        assertThatThrownBy(() -> service.create(request(warehouseId, sparePartId, StockMovementType.RESERVATION, -2)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Quantity must be greater than 0");

        verifyNoInteractions(stockRepository, repository, sparePartRepository);
    }

    @Test
    void adjustmentFailsWhenAdjustedQuantityIsLessThanReserved() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 10, 5);

        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> service.create(request(warehouseId, sparePartId, StockMovementType.ADJUSTMENT, 4)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot adjust quantity below reserved");

        assertThat(stock.getQuantity()).isEqualTo(10);
        verify(repository, never()).save(any(StockMovement.class));
    }

    @Test
    void adjustmentSucceedsWhenAdjustedQuantityIsGreaterThanOrEqualReserved() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 10, 5);

        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));

        when(repository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> saveWithId(invocation.getArgument(0)));

        service.create(request(warehouseId, sparePartId, StockMovementType.ADJUSTMENT, 6));

        assertThat(stock.getQuantity()).isEqualTo(6);
        assertThat(stock.getQuantity()).isGreaterThanOrEqualTo(stock.getReservedQty());

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(repository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getType()).isEqualTo(StockMovementType.ADJUSTMENT);
        assertThat(movementCaptor.getValue().getQuantity()).isEqualTo(6);
    }

    @Test
    void issueMutationNeverMakesQuantityNegative() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 8, 3);

        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));

        when(repository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> saveWithId(invocation.getArgument(0)));

        service.create(request(warehouseId, sparePartId, StockMovementType.ISSUE, 5));

        assertThat(stock.getQuantity()).isEqualTo(3);
        assertThat(stock.getQuantity()).isGreaterThanOrEqualTo(0);

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(repository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getType()).isEqualTo(StockMovementType.ISSUE);
    }

    private StockMovementRequest request(UUID warehouseId, UUID sparePartId, StockMovementType type, double quantity) {
        return new StockMovementRequest(
                warehouseId,
                sparePartId,
                UUID.randomUUID(),
                type,
                quantity,
                null,
                null,
                null,
                null
        );
    }

    private WarehouseStock stock(UUID warehouseId, UUID sparePartId, double quantity, double reservedQty) {
        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(quantity);
        stock.setReservedQty(reservedQty);
        stock.setMinQty(0);
        return stock;
    }

    private StockMovement saveWithId(StockMovement movement) {
        ReflectionTestUtils.setField(movement, "id", UUID.randomUUID());
        return movement;
    }
}
