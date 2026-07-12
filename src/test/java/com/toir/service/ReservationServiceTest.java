package com.toir.service;

import com.toir.dto.reservation.ReservationDto;
import com.toir.dto.reservation.ReservationRequest;
import com.toir.entity.Reservation;
import com.toir.entity.StockMovement;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.entity.maintenance.WorkOrderSparePartRequirement;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.SparePart;
import com.toir.entity.warehouse.Warehouse;
import com.toir.enums.ReservationStatus;
import com.toir.enums.StockMovementType;
import com.toir.exception.RestException;
import com.toir.repository.ReservationRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.maintenance.WorkOrderSparePartRequirementRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.SparePartRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.warehouse.ToirStockService;
import com.toir.service.warehouse.LegacyStockProjectionService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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

    @Mock
    ToirStockService toirStockService;

    @Mock
    LegacyStockProjectionService legacyStockProjectionService;

    @Mock
    WorkOrderSparePartRequirementRepository requirementRepository;

    @Mock WorkOrderRepository workOrderRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock SparePartRepository sparePartRepository;
    @Mock ScopeAccessService scopeAccessService;

    @InjectMocks
    ReservationService service;

    @BeforeEach void authorizationFixtures(){
        org.mockito.Mockito.lenient().when(workOrderRepository.findByIdAndIsDeletedFalse(any())).thenAnswer(i->{WorkOrder w=new WorkOrder();w.setId(i.getArgument(0));return Optional.of(w);});
        org.mockito.Mockito.lenient().when(warehouseRepository.findByIdAndIsDeletedFalse(any())).thenAnswer(i->{Warehouse w=new Warehouse();w.setId(i.getArgument(0));w.setActive(true);return Optional.of(w);});
        org.mockito.Mockito.lenient().when(sparePartRepository.findByIdAndIsDeletedFalse(any())).thenAnswer(i->{SparePart p=new SparePart();p.setId(i.getArgument(0));return Optional.of(p);});
    }

    @Test
    void reserveIncreasesReservedQtyAndCreatesReservationMovement() {
        UUID stockId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID reservedById = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();

        WarehouseStock stock = stock(stockId, warehouseId, sparePartId, 10, 3);
        when(stockRepository.findByIdAndIsDeletedFalse(stockId)).thenReturn(Optional.of(stock));
        WorkOrderSparePartRequirement requirement=new WorkOrderSparePartRequirement();requirement.setId(requirementId);requirement.setWorkOrderId(workOrderId);requirement.setSparePartId(sparePartId);requirement.setRequiredQty(new java.math.BigDecimal("10.0000"));
        when(requirementRepository.findByIdAndWorkOrderIdAndIsDeletedFalseForUpdate(requirementId,workOrderId)).thenReturn(Optional.of(requirement));
        when(repository.saveAndFlush(any(Reservation.class)))
                .thenAnswer(invocation -> {
                    Reservation reservation = invocation.getArgument(0);
                    ReflectionTestUtils.setField(reservation, "id", UUID.randomUUID());
                    return reservation;
                });
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(legacyStockProjectionService.sync(warehouseId, sparePartId)).thenAnswer(invocation -> {
            stock.setReservedQty(7);
            return stock;
        });

        ReservationDto result = service.reserve(new ReservationRequest(stockId,null,null,null,requirementId,null,null,null,null,workOrderId,null,reservedById,new java.math.BigDecimal("4.0000")));

        assertThat(result.workOrderId()).isEqualTo(workOrderId);
        assertThat(result.quantity()).isEqualByComparingTo("4.0000");
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
        assertThat(movement.getQuantity()).isEqualByComparingTo("4.0000");
        verify(toirStockService).reserve(
                warehouseId,
                sparePartId,
                null,
                java.math.BigDecimal.valueOf(4),
                "RESERVATION",
                result.id(),
                null,
                "reservation-reserve:" + result.id()
        );
        verify(lowStockRecommendationService).evaluateStockSafely(stock);
    }

    @Test
    void reserveFailsWhenRequestedQuantityIsGreaterThanAvailable() {
        UUID stockId = UUID.randomUUID();
        WarehouseStock stock = stock(stockId, UUID.randomUUID(), UUID.randomUUID(), 10, 8);
        when(stockRepository.findByIdAndIsDeletedFalse(stockId)).thenReturn(Optional.of(stock));

        when(repository.saveAndFlush(any(Reservation.class)))
                .thenAnswer(invocation -> {
                    Reservation reservation = invocation.getArgument(0);
                    ReflectionTestUtils.setField(reservation, "id", UUID.randomUUID());
                    return reservation;
                });
        org.mockito.Mockito.doThrow(RestException.badRequest(
                        "Insufficient available stock: available=2, requested=3"))
                .when(toirStockService).reserve(any(), any(), any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.reserve(new ReservationRequest(
                stockId,
                null,
                null,
                null,
                new java.math.BigDecimal("3.0000")
        )))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Insufficient available stock");

        verify(stockRepository, never()).save(any(WarehouseStock.class));
        verify(repository).saveAndFlush(any(Reservation.class));
        verify(stockMovementRepository, never()).save(any(StockMovement.class));
    }

    @Test
    void reserveFailsForZeroOrNegativeQuantity() {
        assertThatThrownBy(() -> service.reserve(new ReservationRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                java.math.BigDecimal.ZERO
        )))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Quantity must be greater than 0");

        assertThatThrownBy(() -> service.reserve(new ReservationRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                new java.math.BigDecimal("-2")
        )))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Quantity must be greater than 0");

        verifyNoInteractions(stockRepository, repository, stockMovementRepository);
    }

    @Test
    void canonicalReservationReplayIsRejectedBeforeStockMutation() {
        UUID stockId=UUID.randomUUID(),warehouseId=UUID.randomUUID(),sparePartId=UUID.randomUUID(),workOrderId=UUID.randomUUID(),requirementId=UUID.randomUUID();
        WarehouseStock stock=stock(stockId,warehouseId,sparePartId,10,0);
        WorkOrderSparePartRequirement requirement=new WorkOrderSparePartRequirement();requirement.setId(requirementId);requirement.setWorkOrderId(workOrderId);requirement.setSparePartId(sparePartId);requirement.setRequiredQty(new java.math.BigDecimal("5.0000"));
        when(stockRepository.findByIdAndIsDeletedFalse(stockId)).thenReturn(Optional.of(stock));
        when(requirementRepository.findByIdAndWorkOrderIdAndIsDeletedFalseForUpdate(requirementId,workOrderId)).thenReturn(Optional.of(requirement));
        when(repository.findAllByWorkOrderIdAndRequirementIdAndSparePartIdAndStatusAndIsDeletedFalse(workOrderId,requirementId,sparePartId,ReservationStatus.ACTIVE)).thenReturn(java.util.List.of(new Reservation()));
        assertThatThrownBy(()->service.reserve(canonical(stockId,workOrderId,requirementId,new java.math.BigDecimal("1.0000")))).hasMessageContaining("RESERVATION_DUPLICATE");
        verify(repository,never()).save(any());verifyNoInteractions(toirStockService);
    }

    @Test
    void canonicalReservationCannotExceedWorkOrderRequirement() {
        UUID stockId=UUID.randomUUID(),warehouseId=UUID.randomUUID(),sparePartId=UUID.randomUUID(),workOrderId=UUID.randomUUID(),requirementId=UUID.randomUUID();
        WarehouseStock stock=stock(stockId,warehouseId,sparePartId,10,0);
        WorkOrderSparePartRequirement requirement=new WorkOrderSparePartRequirement();requirement.setId(requirementId);requirement.setWorkOrderId(workOrderId);requirement.setSparePartId(sparePartId);requirement.setRequiredQty(new java.math.BigDecimal("5.0000"));
        when(stockRepository.findByIdAndIsDeletedFalse(stockId)).thenReturn(Optional.of(stock));
        when(requirementRepository.findByIdAndWorkOrderIdAndIsDeletedFalseForUpdate(requirementId,workOrderId)).thenReturn(Optional.of(requirement));
        assertThatThrownBy(()->service.reserve(canonical(stockId,workOrderId,requirementId,new java.math.BigDecimal("5.0001")))).hasMessageContaining("RESERVATION_EXCEEDS_REQUIREMENT");
        verify(repository,never()).save(any());verifyNoInteractions(toirStockService);
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

        when(repository.findByIdAndIsDeletedFalseForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(stockRepository.findByIdAndIsDeletedFalse(stockId)).thenReturn(Optional.of(stock));
        when(repository.saveAndFlush(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(legacyStockProjectionService.sync(warehouseId, sparePartId)).thenAnswer(invocation -> {
            stock.setReservedQty(6);
            return stock;
        });

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
        assertThat(movement.getQuantity()).isEqualByComparingTo("4");
        verify(toirStockService).releaseReservation(
                warehouseId,
                sparePartId,
                null,
                java.math.BigDecimal.valueOf(4),
                "RESERVATION",
                reservationId,
                null,
                "reservation-cancel:" + reservationId
        );
        verify(lowStockRecommendationService).evaluateStockSafely(stock);
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

        when(repository.findByIdAndIsDeletedFalseForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(stockRepository.findByIdAndIsDeletedFalse(stockId)).thenReturn(Optional.of(stock));
        when(repository.saveAndFlush(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(legacyStockProjectionService.sync(warehouseId, sparePartId)).thenAnswer(invocation -> {
            stock.setQuantity(15);
            stock.setReservedQty(3);
            return stock;
        });

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
        assertThat(movement.getQuantity()).isEqualByComparingTo("5");
        verify(toirStockService).fulfillReservation(
                warehouseId,
                sparePartId,
                null,
                java.math.BigDecimal.valueOf(5),
                "RESERVATION",
                reservationId,
                null,
                "reservation-fulfill:" + reservationId
        );
        verify(lowStockRecommendationService).evaluateStockSafely(stock);
    }

    @Test
    void fulfillRejectsReservationWhenReservedQuantityIsAlreadyLowerThanReservationQuantity() {
        UUID reservationId = UUID.randomUUID();
        UUID stockId = UUID.randomUUID();
        Reservation reservation = activeReservation(reservationId, stockId, UUID.randomUUID(), UUID.randomUUID(), 5);
        WarehouseStock stock = stock(stockId, UUID.randomUUID(), UUID.randomUUID(), 20, 3);

        when(repository.findByIdAndIsDeletedFalseForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(stockRepository.findByIdAndIsDeletedFalse(stockId)).thenReturn(Optional.of(stock));

        when(repository.saveAndFlush(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doThrow(RestException.badRequest(
                        "Stock reserved quantity is lower than reservation quantity: reserved=3, requested=5"))
                .when(toirStockService).fulfillReservation(any(), any(), any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.fulfill(reservationId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("reserved quantity");

        verify(stockRepository, never()).save(any(WarehouseStock.class));
        verify(repository).saveAndFlush(any(Reservation.class));
        verify(stockMovementRepository, never()).save(any(StockMovement.class));
    }

    @Test void newWorkOrderReservationRequiresCanonicalRequirement(){
        UUID stockId=UUID.randomUUID(),warehouseId=UUID.randomUUID(),sparePartId=UUID.randomUUID();
        when(stockRepository.findByIdAndIsDeletedFalse(stockId)).thenReturn(Optional.of(stock(stockId,warehouseId,sparePartId,10,0)));
        assertThatThrownBy(()->service.reserve(new ReservationRequest(stockId,UUID.randomUUID(),null,null,java.math.BigDecimal.ONE)))
                .isInstanceOf(RestException.class).hasMessageContaining("RESERVATION_REQUIREMENT_REQUIRED");
        verify(repository,never()).saveAndFlush(any());
    }

    @Test void repeatedCancelIsIdempotentAndUsesPessimisticLock(){
        UUID id=UUID.randomUUID(); Reservation r=activeReservation(id,null,null,null,2);r.setWarehouseId(UUID.randomUUID());r.setSparePartId(UUID.randomUUID());r.setStatus(ReservationStatus.CANCELLED);
        when(repository.findByIdAndIsDeletedFalseForUpdate(id)).thenReturn(Optional.of(r));
        assertThat(service.cancel(id).status()).isEqualTo(ReservationStatus.CANCELLED);
        verify(repository).findByIdAndIsDeletedFalseForUpdate(id);verify(repository,never()).saveAndFlush(any());verifyNoInteractions(toirStockService);
    }

    @Test void fulfilledTransitionCannotBeCancelled(){
        UUID id=UUID.randomUUID(); Reservation r=activeReservation(id,null,null,null,2);r.setWarehouseId(UUID.randomUUID());r.setSparePartId(UUID.randomUUID());r.setStatus(ReservationStatus.FULFILLED);
        when(repository.findByIdAndIsDeletedFalseForUpdate(id)).thenReturn(Optional.of(r));
        assertThatThrownBy(()->service.cancel(id)).isInstanceOf(RestException.class).hasMessageContaining("RESERVATION_ALREADY_FULFILLED");
        verify(repository,never()).saveAndFlush(any());verifyNoInteractions(toirStockService);
    }

    @Test void inaccessibleWorkOrderIsGenericForbiddenBeforeReservationFactsAreRevealed(){
        UUID stockId=UUID.randomUUID(),workOrderId=UUID.randomUUID(),warehouseId=UUID.randomUUID(),sparePartId=UUID.randomUUID();
        when(stockRepository.findByIdAndIsDeletedFalse(stockId)).thenReturn(Optional.of(stock(stockId,warehouseId,sparePartId,10,0)));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.reserve(new ReservationRequest(stockId,workOrderId,null,null,java.math.BigDecimal.ONE)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class).hasMessage("Access denied");
        verify(repository,never()).saveAndFlush(any());
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
        reservation.setQuantity(java.math.BigDecimal.valueOf(quantity));
        reservation.setStatus(ReservationStatus.ACTIVE);
        return reservation;
    }

    private ReservationRequest canonical(UUID stockId,UUID workOrderId,UUID requirementId,java.math.BigDecimal quantity){return new ReservationRequest(stockId,null,null,null,requirementId,null,null,null,null,workOrderId,null,null,quantity);}
}
