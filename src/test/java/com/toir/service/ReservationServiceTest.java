package com.toir.service;

import com.toir.dto.reservation.ReservationDto;
import com.toir.dto.reservation.ReservationRequest;
import com.toir.entity.Reservation;
import com.toir.entity.StockMovement;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.ReservationStatus;
import com.toir.enums.StockMovementType;
import com.toir.exception.RestException;
import com.toir.repository.ReservationRepository;
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
class ReservationServiceTest {

    @Mock
    ReservationRepository repository;

    @Mock
    WarehouseStockRepository stockRepository;

    @Mock
    StockMovementRepository stockMovementRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    LowStockRecommendationService lowStockRecommendationService;

    @InjectMocks
    ReservationService service;

    @Test
    void reserveIncreasesReservedQtyAndCreatesReservationMovement() {
        UUID stockId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID reservedById = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();

        WarehouseStock stock = stock(stockId, warehouseId, sparePartId, 10, 3);
        when(stockRepository.findByIdAndIsDeletedFalse(stockId)).thenReturn(Optional.of(stock));
        when(stockRepository.save(any(WarehouseStock.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(Reservation.class)))
                .thenAnswer(invocation -> {
                    Reservation reservation = invocation.getArgument(0);
                    ReflectionTestUtils.setField(reservation, "id", UUID.randomUUID());
                    return reservation;
                });
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationDto result = service.reserve(new ReservationRequest(
                stockId,
                workOrderId,
                null,
                reservedById,
                4
        ));

        assertThat(result.workOrderId()).isEqualTo(workOrderId);
        assertThat(result.quantity()).isEqualTo(4);
        assertThat(result.status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(stock.getReservedQty()).isEqualTo(7);
        assertThat(stock.getQuantity()).isEqualTo(10);

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());
        StockMovement movement = movementCaptor.getValue();
        assertThat(movement.getType()).isEqualTo(StockMovementType.RESERVATION);
        assertThat(movement.getWorkOrderId()).isEqualTo(workOrderId);
        assertThat(movement.getWarehouseId()).isEqualTo(warehouseId);
        assertThat(movement.getSparePartId()).isEqualTo(sparePartId);
        assertThat(movement.getCreatedById()).isEqualTo(reservedById);
        assertThat(movement.getQuantity()).isEqualTo(4);
    }

    @Test
    void reserveFailsWhenRequestedQuantityIsGreaterThanAvailable() {
        UUID stockId = UUID.randomUUID();
        WarehouseStock stock = stock(stockId, UUID.randomUUID(), UUID.randomUUID(), 10, 8);
        when(stockRepository.findByIdAndIsDeletedFalse(stockId)).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> service.reserve(new ReservationRequest(
                stockId,
                UUID.randomUUID(),
                null,
                null,
                3
        )))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot reserve more than available");

        verify(stockRepository, never()).save(any(WarehouseStock.class));
        verify(repository, never()).save(any(Reservation.class));
        verify(stockMovementRepository, never()).save(any(StockMovement.class));
    }

    @Test
    void reserveFailsForZeroOrNegativeQuantity() {
        assertThatThrownBy(() -> service.reserve(new ReservationRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                0
        )))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Quantity must be greater than 0");

        assertThatThrownBy(() -> service.reserve(new ReservationRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                -2
        )))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Quantity must be greater than 0");

        verifyNoInteractions(stockRepository, repository, stockMovementRepository);
    }

    @Test
    void cancelDecreasesReservedQtyAndCreatesReleaseMovement() {
        UUID reservationId = UUID.randomUUID();
        UUID stockId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();

        Reservation reservation = activeReservation(reservationId, stockId, workOrderId, UUID.randomUUID(), 4);
        WarehouseStock stock = stock(stockId, warehouseId, sparePartId, 20, 10);

        when(repository.findByIdAndIsDeletedFalse(reservationId)).thenReturn(Optional.of(reservation));
        when(stockRepository.findByIdAndIsDeletedFalse(stockId)).thenReturn(Optional.of(stock));
        when(stockRepository.save(any(WarehouseStock.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationDto result = service.cancel(reservationId);

        assertThat(result.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(stock.getReservedQty()).isEqualTo(6);
        assertThat(stock.getQuantity()).isEqualTo(20);

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());
        StockMovement movement = movementCaptor.getValue();
        assertThat(movement.getType()).isEqualTo(StockMovementType.RELEASE);
        assertThat(movement.getWorkOrderId()).isEqualTo(workOrderId);
        assertThat(movement.getWarehouseId()).isEqualTo(warehouseId);
        assertThat(movement.getSparePartId()).isEqualTo(sparePartId);
        assertThat(movement.getQuantity()).isEqualTo(4);
    }

    @Test
    void fulfillDecreasesQuantityAndReservedQtyAndCreatesIssueMovement() {
        UUID reservationId = UUID.randomUUID();
        UUID stockId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();

        Reservation reservation = activeReservation(reservationId, stockId, workOrderId, UUID.randomUUID(), 5);
        WarehouseStock stock = stock(stockId, warehouseId, sparePartId, 20, 8);

        when(repository.findByIdAndIsDeletedFalse(reservationId)).thenReturn(Optional.of(reservation));
        when(stockRepository.findByIdAndIsDeletedFalse(stockId)).thenReturn(Optional.of(stock));
        when(stockRepository.save(any(WarehouseStock.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationDto result = service.fulfill(reservationId);

        assertThat(result.status()).isEqualTo(ReservationStatus.FULFILLED);
        assertThat(stock.getQuantity()).isEqualTo(15);
        assertThat(stock.getReservedQty()).isEqualTo(3);

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());
        StockMovement movement = movementCaptor.getValue();
        assertThat(movement.getType()).isEqualTo(StockMovementType.ISSUE);
        assertThat(movement.getWorkOrderId()).isEqualTo(workOrderId);
        assertThat(movement.getWarehouseId()).isEqualTo(warehouseId);
        assertThat(movement.getSparePartId()).isEqualTo(sparePartId);
        assertThat(movement.getQuantity()).isEqualTo(5);
        verify(lowStockRecommendationService).evaluateStockSafely(stock);
    }

    @Test
    void fulfillRejectsReservationWhenReservedQuantityIsAlreadyLowerThanReservationQuantity() {
        UUID reservationId = UUID.randomUUID();
        UUID stockId = UUID.randomUUID();
        Reservation reservation = activeReservation(reservationId, stockId, UUID.randomUUID(), UUID.randomUUID(), 5);
        WarehouseStock stock = stock(stockId, UUID.randomUUID(), UUID.randomUUID(), 20, 3);

        when(repository.findByIdAndIsDeletedFalse(reservationId)).thenReturn(Optional.of(reservation));
        when(stockRepository.findByIdAndIsDeletedFalse(stockId)).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> service.fulfill(reservationId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("reserved quantity");

        verify(stockRepository, never()).save(any(WarehouseStock.class));
        verify(repository, never()).save(any(Reservation.class));
        verify(stockMovementRepository, never()).save(any(StockMovement.class));
    }

    private WarehouseStock stock(UUID stockId, UUID warehouseId, UUID sparePartId, double quantity, double reservedQty) {
        WarehouseStock stock = new WarehouseStock();
        stock.setId(stockId);
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(quantity);
        stock.setReservedQty(reservedQty);
        return stock;
    }

    private Reservation activeReservation(UUID reservationId,
                                          UUID stockId,
                                          UUID workOrderId,
                                          UUID reservedById,
                                          double quantity) {
        Reservation reservation = new Reservation();
        reservation.setId(reservationId);
        reservation.setWarehouseStockId(stockId);
        reservation.setWorkOrderId(workOrderId);
        reservation.setReservedById(reservedById);
        reservation.setQuantity(quantity);
        reservation.setStatus(ReservationStatus.ACTIVE);
        return reservation;
    }
}
