package com.toir.service;

import com.toir.entity.SparePart;
import com.toir.entity.StockMovement;
import com.toir.entity.equipment.ProcurementRequestLine;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.enums.StockMovementType;
import com.toir.exception.RestException;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcurementRequestServiceTest {

    @Mock
    ProcurementRequestRepository repository;

    @Mock
    SparePartRepository sparePartRepository;

    @Mock
    WarehouseStockRepository stockRepository;

    @Mock
    StockMovementRepository stockMovementRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    LowStockRecommendationService lowStockRecommendationService;

    ProcurementRequestService service;

    @BeforeEach
    void setUp() {
        service = new ProcurementRequestService(
                repository,
                sparePartRepository,
                stockRepository,
                stockMovementRepository,
                auditBuilderService,
                warehouseRepository,
                scopeAccessService,
                lowStockRecommendationService
        );
    }

    @Test
    void receivingOrderedRequestUpdatesExistingStockAndCreatesReceiptMovement() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        ProcurementRequest request = request(requestId, warehouseId, ProcurementRequestStatus.ORDERED,
                List.of(line(sparePartId, 4, 12.5)));
        WarehouseStock stock = stock(warehouseId, sparePartId, 6);
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));
        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));
        when(stockRepository.save(any(WarehouseStock.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(ProcurementRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.markReceived(requestId);

        assertThat(result.status()).isEqualTo(ProcurementRequestStatus.RECEIVED);
        assertThat(result.receivedAt()).isNotNull();
        assertThat(stock.getQuantity()).isEqualTo(10);

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());
        StockMovement movement = movementCaptor.getValue();
        assertThat(movement.getType()).isEqualTo(StockMovementType.RECEIPT);
        assertThat(movement.getWarehouseId()).isEqualTo(warehouseId);
        assertThat(movement.getSparePartId()).isEqualTo(sparePartId);
        assertThat(movement.getQuantity()).isEqualTo(4);
        assertThat(movement.getUnitCost()).isEqualTo(12.5);
        assertThat(movement.getDocumentNumber()).isEqualTo("PR-2026-0001");
        assertThat(movement.getNotes()).contains(requestId.toString());
        verify(lowStockRecommendationService).evaluateStockSafely(stock);
    }

    @Test
    void receivingCreatesStockRowWhenMissing() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        SparePart sparePart = sparePart(sparePartId);
        ProcurementRequest request = request(requestId, warehouseId, ProcurementRequestStatus.ORDERED,
                List.of(line(sparePartId, 3, null)));
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));
        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.empty());
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(stockRepository.save(any(WarehouseStock.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(ProcurementRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.markReceived(requestId);

        ArgumentCaptor<WarehouseStock> stockCaptor = ArgumentCaptor.forClass(WarehouseStock.class);
        verify(stockRepository).save(stockCaptor.capture());
        WarehouseStock savedStock = stockCaptor.getValue();
        assertThat(savedStock.getWarehouseId()).isEqualTo(warehouseId);
        assertThat(savedStock.getSparePart()).isEqualTo(sparePart);
        assertThat(savedStock.getQuantity()).isEqualTo(3);
        assertThat(savedStock.getReservedQty()).isZero();
        assertThat(savedStock.getMinQty()).isZero();
    }

    @Test
    void receivingCreatesReceiptMovementPerLine() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID firstSparePartId = UUID.randomUUID();
        UUID secondSparePartId = UUID.randomUUID();
        ProcurementRequest request = request(requestId, warehouseId, ProcurementRequestStatus.ORDERED,
                List.of(line(firstSparePartId, 2, 5.0), line(secondSparePartId, 7, 9.0)));
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));
        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, firstSparePartId))
                .thenReturn(Optional.of(stock(warehouseId, firstSparePartId, 1)));
        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, secondSparePartId))
                .thenReturn(Optional.of(stock(warehouseId, secondSparePartId, 4)));
        when(stockRepository.save(any(WarehouseStock.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(ProcurementRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.markReceived(requestId);

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, org.mockito.Mockito.times(2)).save(movementCaptor.capture());
        assertThat(movementCaptor.getAllValues())
                .extracting(StockMovement::getSparePartId)
                .containsExactly(firstSparePartId, secondSparePartId);
    }

    @Test
    void repeatedReceiveIsBlocked() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        ProcurementRequest request = request(requestId, UUID.randomUUID(), ProcurementRequestStatus.RECEIVED,
                List.of(line(UUID.randomUUID(), 1, null)));
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.markReceived(requestId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("already RECEIVED");

        verify(stockRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void receivingWithoutWarehouseIsBlocked() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        ProcurementRequest request = request(requestId, null, ProcurementRequestStatus.ORDERED,
                List.of(line(UUID.randomUUID(), 1, null)));
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.markReceived(requestId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("warehouseId is required");

        verify(stockRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void receivingWithoutLinesIsBlocked() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        ProcurementRequest request = request(requestId, UUID.randomUUID(), ProcurementRequestStatus.ORDERED, List.of());
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.markReceived(requestId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("at least one line");

        verify(stockRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void receivingInvalidStatusesIsBlocked() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        for (ProcurementRequestStatus status : List.of(
                ProcurementRequestStatus.DRAFT,
                ProcurementRequestStatus.SUBMITTED,
                ProcurementRequestStatus.APPROVED,
                ProcurementRequestStatus.CANCELLED,
                ProcurementRequestStatus.REJECTED)) {
            UUID requestId = UUID.randomUUID();
            ProcurementRequest request = request(requestId, UUID.randomUUID(), status,
                    List.of(line(UUID.randomUUID(), 1, null)));
            when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.markReceived(requestId))
                    .isInstanceOf(RestException.class)
                    .hasMessageContaining("Only ORDERED can be marked RECEIVED");
        }

        verify(stockRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void receivingLineWithoutSparePartIsBlocked() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        ProcurementRequest request = request(requestId, UUID.randomUUID(), ProcurementRequestStatus.ORDERED,
                List.of(line(null, 1, null)));
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.markReceived(requestId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("sparePartId is required");

        verify(stockRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void receivingLineWithNonPositiveQuantityIsBlocked() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        ProcurementRequest request = request(requestId, UUID.randomUUID(), ProcurementRequestStatus.ORDERED,
                List.of(line(UUID.randomUUID(), 0, null)));
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.markReceived(requestId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("quantity must be greater than 0");

        verify(stockRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void missingRequestRemains404OnReceive() {
        UUID requestId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markReceived(requestId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Procurement request not found");

        verify(repository, never()).save(any());
        verify(stockRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
        verify(sparePartRepository, never()).findByIdAndIsDeletedFalse(any());
    }

    private ProcurementRequest request(UUID id,
                                       UUID warehouseId,
                                       ProcurementRequestStatus status,
                                       List<ProcurementRequestLine> lines) {
        ProcurementRequest request = new ProcurementRequest();
        request.setId(id);
        request.setNumber("PR-2026-0001");
        request.setTitle("Procurement");
        request.setWarehouseId(warehouseId);
        request.setStatus(status);
        for (ProcurementRequestLine line : lines) {
            line.setRequest(request);
            request.getLines().add(line);
        }
        return request;
    }

    private ProcurementRequestLine line(UUID sparePartId, double quantity, Double unitPrice) {
        ProcurementRequestLine line = new ProcurementRequestLine();
        line.setId(UUID.randomUUID());
        line.setSparePartId(sparePartId);
        line.setQuantity(quantity);
        line.setUnit("pcs");
        line.setUnitPrice(unitPrice);
        line.setEstimatedCost(unitPrice == null ? 0 : unitPrice * quantity);
        return line;
    }

    private WarehouseStock stock(UUID warehouseId, UUID sparePartId, double quantity) {
        WarehouseStock stock = new WarehouseStock();
        stock.setId(UUID.randomUUID());
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(quantity);
        stock.setReservedQty(0);
        stock.setMinQty(0);
        return stock;
    }

    private SparePart sparePart(UUID sparePartId) {
        SparePart sparePart = new SparePart();
        sparePart.setId(sparePartId);
        sparePart.setCode("SP-1");
        sparePart.setName("Bearing");
        sparePart.setUnit("pcs");
        return sparePart;
    }
}
