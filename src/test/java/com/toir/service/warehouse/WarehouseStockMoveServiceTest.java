package com.toir.service.warehouse;

import com.toir.dto.warehouse.StockIssueCommand;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.dto.warehouse.WarehouseStockMoveRequest;
import com.toir.entity.StockMovement;
import com.toir.entity.warehouse.WarehouseStockLedger;
import com.toir.enums.StockLedgerMovementType;
import com.toir.enums.StockMovementSourceType;
import com.toir.enums.StockMovementType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.exception.RestException;
import com.toir.repository.StockMovementRepository;
import com.toir.service.warehouse.LegacyStockProjectionService;
import com.toir.service.warehouse.ToirStockService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseStockMoveServiceTest {

    @Mock
    StockMovementRepository movementRepository;

    @Mock
    ToirStockService stockService;

    @Mock
    WmsStockCoordinateValidator coordinateValidator;

    @Mock
    LegacyStockProjectionService legacyStockProjectionService;

    @Mock
    AuditBuilderService auditBuilderService;

    WarehouseStockMoveService service;

    @BeforeEach
    void setUp() {
        service = new WarehouseStockMoveService(
                movementRepository,
                stockService,
                coordinateValidator,
                legacyStockProjectionService,
                auditBuilderService
        );
    }

    @Test
    void movePostsOutAndInLedgersAndPreservesIdentity() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID fromBinId = UUID.randomUUID();
        UUID toBinId = UUID.randomUUID();
        UUID movementId = UUID.randomUUID();
        UUID outLedgerId = UUID.randomUUID();
        UUID inLedgerId = UUID.randomUUID();
        LocalDate expiryDate = LocalDate.of(2027, 3, 15);

        when(movementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(movementId);
            return movement;
        });
        when(stockService.postDecrease(any(StockIssueCommand.class), eq(StockLedgerMovementType.MOVE_OUT)))
                .thenReturn(ledger(outLedgerId, new BigDecimal("12.50")));
        when(stockService.postIncrease(any(StockReceiptCommand.class), eq(StockLedgerMovementType.MOVE_IN)))
                .thenReturn(ledger(inLedgerId, new BigDecimal("12.50")));

        var response = service.move(new WarehouseStockMoveRequest(
                warehouseId,
                sparePartId,
                fromBinId,
                toBinId,
                new BigDecimal("5.0000"),
                "LOT-7",
                "SN-8",
                expiryDate,
                WarehouseStockStatus.AVAILABLE,
                "MOVE-1",
                "relocation"
        ));

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepository).save(movementCaptor.capture());
        StockMovement movement = movementCaptor.getValue();
        assertThat(movement.getType()).isEqualTo(StockMovementType.BIN_MOVE);
        assertThat(movement.getSourceType()).isEqualTo(StockMovementSourceType.MANUAL);
        assertThat(movement.getWarehouseId()).isEqualTo(warehouseId);
        assertThat(movement.getSparePartId()).isEqualTo(sparePartId);
        assertThat(movement.getFromBinId()).isEqualTo(fromBinId);
        assertThat(movement.getToBinId()).isEqualTo(toBinId);
        assertThat(movement.getBinId()).isEqualTo(toBinId);
        assertThat(movement.getLotNumber()).isEqualTo("LOT-7");
        assertThat(movement.getSerialNumber()).isEqualTo("SN-8");
        assertThat(movement.getExpiryDate()).isEqualTo(expiryDate);
        assertThat(movement.getStockStatus()).isEqualTo(WarehouseStockStatus.AVAILABLE);

        ArgumentCaptor<StockIssueCommand> outCaptor = ArgumentCaptor.forClass(StockIssueCommand.class);
        verify(stockService).postDecrease(outCaptor.capture(), eq(StockLedgerMovementType.MOVE_OUT));
        assertThat(outCaptor.getValue().binId()).isEqualTo(fromBinId);
        assertThat(outCaptor.getValue().lotNumber()).isEqualTo("LOT-7");
        assertThat(outCaptor.getValue().serialNumber()).isEqualTo("SN-8");
        assertThat(outCaptor.getValue().expiryDate()).isEqualTo(expiryDate);
        assertThat(outCaptor.getValue().stockStatus()).isEqualTo(WarehouseStockStatus.AVAILABLE);
        assertThat(outCaptor.getValue().idempotencyKey()).isEqualTo("warehouse-bin-move-out:" + movementId);

        ArgumentCaptor<StockReceiptCommand> inCaptor = ArgumentCaptor.forClass(StockReceiptCommand.class);
        verify(stockService).postIncrease(inCaptor.capture(), eq(StockLedgerMovementType.MOVE_IN));
        assertThat(inCaptor.getValue().binId()).isEqualTo(toBinId);
        assertThat(inCaptor.getValue().unitCost()).isEqualByComparingTo("12.50");
        assertThat(inCaptor.getValue().lotNumber()).isEqualTo("LOT-7");
        assertThat(inCaptor.getValue().serialNumber()).isEqualTo("SN-8");
        assertThat(inCaptor.getValue().expiryDate()).isEqualTo(expiryDate);
        assertThat(inCaptor.getValue().stockStatus()).isEqualTo(WarehouseStockStatus.AVAILABLE);
        assertThat(inCaptor.getValue().idempotencyKey()).isEqualTo("warehouse-bin-move-in:" + movementId);

        verify(coordinateValidator).assertCanReadFrom(warehouseId, fromBinId);
        verify(coordinateValidator).assertCanReceiveOrMoveInto(warehouseId, toBinId, WarehouseStockStatus.AVAILABLE);
        verify(legacyStockProjectionService).sync(warehouseId, sparePartId);
        assertThat(response.stockMovementId()).isEqualTo(movementId);
        assertThat(response.moveOutLedgerId()).isEqualTo(outLedgerId);
        assertThat(response.moveInLedgerId()).isEqualTo(inLedgerId);
        assertThat(response.quantity()).isEqualByComparingTo("5.0000");
    }

    @Test
    void moveIntoBlockedBinIsRejectedBeforeSavingMovement() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID fromBinId = UUID.randomUUID();
        UUID toBinId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(RestException.badRequest("Cannot receive or move stock into blocked bin"))
                .when(coordinateValidator)
                .assertCanReceiveOrMoveInto(warehouseId, toBinId, WarehouseStockStatus.AVAILABLE);

        assertThatThrownBy(() -> service.move(new WarehouseStockMoveRequest(
                warehouseId,
                sparePartId,
                fromBinId,
                toBinId,
                BigDecimal.ONE,
                null,
                null,
                null,
                WarehouseStockStatus.AVAILABLE,
                null,
                null
        ))).isInstanceOf(RestException.class)
                .hasMessageContaining("blocked bin");

        verify(movementRepository, never()).save(any());
        verify(stockService, never()).postDecrease(any(), any());
        verify(stockService, never()).postIncrease(any(), any());
    }

    @Test
    void moveWithinSameBinIsRejected() {
        UUID binId = UUID.randomUUID();

        assertThatThrownBy(() -> service.move(new WarehouseStockMoveRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                binId,
                binId,
                BigDecimal.ONE,
                null,
                null,
                null,
                null,
                null,
                null
        ))).isInstanceOf(RestException.class)
                .hasMessageContaining("different");

        verify(movementRepository, never()).save(any());
    }

    private WarehouseStockLedger ledger(UUID id, BigDecimal unitCost) {
        WarehouseStockLedger ledger = new WarehouseStockLedger();
        ledger.setId(id);
        ledger.setUnitCost(unitCost);
        return ledger;
    }
}
