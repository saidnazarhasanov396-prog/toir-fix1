package com.toir.service;

import com.toir.dto.stockmovement.StockMovementDto;
import com.toir.dto.stockmovement.StockMovementRequest;
import com.toir.entity.StockMovement;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.StockMovementType;
import com.toir.exception.RestException;
import com.toir.repository.StockMovementListRow;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
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

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    LowStockRecommendationService lowStockRecommendationService;

    @InjectMocks
    StockMovementService service;

    @BeforeEach
    void setUp() {
        lenient().when(warehouseRepository.findByIdAndIsDeletedFalse(any())).thenAnswer(invocation -> {
            UUID warehouseId = invocation.getArgument(0);
            return Optional.of(warehouse(warehouseId));
        });
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
    }

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
        verify(lowStockRecommendationService).evaluateStockSafely(stock);
    }

    @Test
    void receiptDoesNotTriggerLowStockEvaluation() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 8, 0);

        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));
        when(repository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> saveWithId(invocation.getArgument(0)));

        service.create(request(warehouseId, sparePartId, StockMovementType.RECEIPT, 5));

        assertThat(stock.getQuantity()).isEqualTo(13);
        verify(lowStockRecommendationService, never()).evaluateStockSafely(any(WarehouseStock.class));
    }

    @Test
    void findAllPageReturnsRelationDisplayFieldsFromProjection() {
        UUID movementId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID createdById = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-06-05T08:15:30Z");
        PageRequest pageRequest = PageRequest.of(0, 8);

        when(repository.findListRows(
                eq(true),
                eq(null),
                eq(null),
                eq(pageRequest)))
                .thenReturn(new PageImpl<>(
                        List.of(stockMovementRow(
                                movementId,
                                warehouseId,
                                "Main Warehouse",
                                sparePartId,
                                "Bearing 6205",
                                workOrderId,
                                "WO-42",
                                "Pump repair",
                                createdById,
                                "Jane Smith",
                                occurredAt)),
                        pageRequest,
                        1));

        Page<StockMovementDto> result = service.findAll(0, 8);

        StockMovementDto dto = result.getContent().getFirst();
        assertThat(dto.id()).isEqualTo(movementId);
        assertThat(dto.warehouseId()).isEqualTo(warehouseId);
        assertThat(dto.warehouseName()).isEqualTo("Main Warehouse");
        assertThat(dto.sparePartId()).isEqualTo(sparePartId);
        assertThat(dto.sparePartName()).isEqualTo("Bearing 6205");
        assertThat(dto.workOrderId()).isEqualTo(workOrderId);
        assertThat(dto.workOrderName()).isEqualTo("Pump repair");
        assertThat(dto.workOrderNumber()).isEqualTo("WO-42");
        assertThat(dto.createdById()).isEqualTo(createdById);
        assertThat(dto.createdByFullName()).isEqualTo("Jane Smith");
        assertThat(dto.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    void issueWithWorkOrderIdIsBlockedToPreserveMaterialUsageSourceOfTruth() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(requestWithWorkOrder(
                warehouseId,
                sparePartId,
                StockMovementType.ISSUE,
                1,
                UUID.randomUUID())))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("/api/v1/work-orders/{workOrderId}/material-usage");

        verifyNoInteractions(stockRepository, repository, sparePartRepository);
    }

    @Test
    void manualStockMovementRequiresReasonOrSourceDocument() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(new StockMovementRequest(
                warehouseId,
                sparePartId,
                null,
                StockMovementType.ADJUSTMENT,
                10,
                null,
                null,
                UUID.randomUUID(),
                " "
        )))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("reason or source document");

        verifyNoInteractions(stockRepository, repository, sparePartRepository);
    }

    private StockMovementRequest request(UUID warehouseId, UUID sparePartId, StockMovementType type, double quantity) {
        return requestWithWorkOrder(warehouseId, sparePartId, type, quantity, null);
    }

    private StockMovementRequest requestWithWorkOrder(
            UUID warehouseId,
            UUID sparePartId,
            StockMovementType type,
            double quantity,
            UUID workOrderId) {
        return new StockMovementRequest(
                warehouseId,
                sparePartId,
                workOrderId,
                type,
                quantity,
                null,
                "DOC-1",
                null,
                "Manual stock movement reason"
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

    private Warehouse warehouse(UUID warehouseId) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setActive(true);
        return warehouse;
    }

    private StockMovement saveWithId(StockMovement movement) {
        ReflectionTestUtils.setField(movement, "id", UUID.randomUUID());
        return movement;
    }

    private StockMovementListRow stockMovementRow(
            UUID id,
            UUID warehouseId,
            String warehouseName,
            UUID sparePartId,
            String sparePartName,
            UUID workOrderId,
            String workOrderNumber,
            String workOrderName,
            UUID createdById,
            String createdByFullName,
            Instant occurredAt) {
        return new StockMovementListRow() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public UUID getWarehouseId() {
                return warehouseId;
            }

            @Override
            public String getWarehouseName() {
                return warehouseName;
            }

            @Override
            public UUID getSparePartId() {
                return sparePartId;
            }

            @Override
            public String getSparePartName() {
                return sparePartName;
            }

            @Override
            public UUID getWorkOrderId() {
                return workOrderId;
            }

            @Override
            public String getWorkOrderNumber() {
                return workOrderNumber;
            }

            @Override
            public String getWorkOrderName() {
                return workOrderName;
            }

            @Override
            public String getType() {
                return StockMovementType.RECEIPT.name();
            }

            @Override
            public double getQuantity() {
                return 2;
            }

            @Override
            public Double getUnitCost() {
                return 10.0;
            }

            @Override
            public String getDocumentNumber() {
                return "DOC-1";
            }

            @Override
            public UUID getCreatedById() {
                return createdById;
            }

            @Override
            public String getCreatedByFullName() {
                return createdByFullName;
            }

            @Override
            public Instant getOccurredAt() {
                return occurredAt;
            }

            @Override
            public String getNotes() {
                return null;
            }
        };
    }
}
