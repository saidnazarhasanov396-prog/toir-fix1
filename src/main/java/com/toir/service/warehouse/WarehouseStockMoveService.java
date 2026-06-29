package com.toir.service.warehouse;

import com.toir.dto.warehouse.StockIssueCommand;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.dto.warehouse.WarehouseStockMoveRequest;
import com.toir.dto.warehouse.WarehouseStockMoveResponse;
import com.toir.entity.StockMovement;
import com.toir.entity.warehouse.WarehouseStockLedger;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.StockLedgerMovementType;
import com.toir.enums.StockMovementSourceType;
import com.toir.enums.StockMovementType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.exception.RestException;
import com.toir.repository.StockMovementRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseStockMoveService {

    private static final String REFERENCE_TYPE = "WAREHOUSE_BIN_MOVE";

    private final StockMovementRepository movementRepository;
    private final ToirStockService stockService;
    private final WmsStockCoordinateValidator coordinateValidator;
    private final LegacyStockProjectionService legacyStockProjectionService;
    private final AuditBuilderService auditBuilderService;

    @Transactional
    public WarehouseStockMoveResponse move(WarehouseStockMoveRequest request) {
        validate(request);
        WarehouseStockStatus status = request.effectiveStatus();

        coordinateValidator.assertCanReadFrom(request.warehouseId(), request.fromBinId());
        coordinateValidator.assertCanReceiveOrMoveInto(request.warehouseId(), request.toBinId(), status);

        StockMovement movement = movementRepository.save(movement(request, status));
        WarehouseStockLedger moveOutLedger = stockService.postDecrease(
                moveOutCommand(request, movement.getId(), status),
                StockLedgerMovementType.MOVE_OUT
        );
        WarehouseStockLedger moveInLedger = stockService.postIncrease(
                moveInCommand(request, movement.getId(), status, moveOutLedger.getUnitCost()),
                StockLedgerMovementType.MOVE_IN
        );

        legacyStockProjectionService.sync(request.warehouseId(), request.sparePartId());
        auditBuilderService.log(
                "stock_movement",
                movement.getId().toString(),
                AuditAction.CREATE,
                AuditModule.STOCK_MOVEMENT,
                "Перемещение между ячейками создано",
                null,
                movement
        );

        return new WarehouseStockMoveResponse(
                movement.getId(),
                moveOutLedger.getId(),
                moveInLedger.getId(),
                request.warehouseId(),
                request.sparePartId(),
                request.fromBinId(),
                request.toBinId(),
                request.quantity()
        );
    }

    private StockMovement movement(WarehouseStockMoveRequest request, WarehouseStockStatus status) {
        StockMovement movement = new StockMovement();
        movement.setWarehouseId(request.warehouseId());
        movement.setSparePartId(request.sparePartId());
        movement.setType(StockMovementType.BIN_MOVE);
        movement.setQuantity(request.quantity().doubleValue());
        movement.setDocumentNumber(trimToNull(request.documentNumber()));
        movement.setSourceType(StockMovementSourceType.MANUAL);
        movement.setMovementDate(LocalDate.now());
        movement.setOccurredAt(Instant.now());
        movement.setNotes(trimToNull(request.comment()));
        movement.setComment(trimToNull(request.comment()));
        movement.setBinId(request.toBinId());
        movement.setFromBinId(request.fromBinId());
        movement.setToBinId(request.toBinId());
        movement.setSourceBinId(request.fromBinId());
        movement.setDestinationBinId(request.toBinId());
        movement.setLotNumber(trimToNull(request.lotNumber()));
        movement.setSerialNumber(trimToNull(request.serialNumber()));
        movement.setExpiryDate(request.expiryDate());
        movement.setStockStatus(status);
        movement.setSourceDocumentNo(trimToNull(request.documentNumber()));
        return movement;
    }

    private StockIssueCommand moveOutCommand(WarehouseStockMoveRequest request,
                                             UUID movementId,
                                             WarehouseStockStatus status) {
        return new StockIssueCommand(
                request.warehouseId(),
                request.sparePartId(),
                request.fromBinId(),
                request.quantity(),
                request.lotNumber(),
                request.serialNumber(),
                request.expiryDate(),
                status,
                REFERENCE_TYPE,
                movementId,
                request.documentNumber(),
                request.comment(),
                "warehouse-bin-move-out:" + movementId
        );
    }

    private StockReceiptCommand moveInCommand(WarehouseStockMoveRequest request,
                                              UUID movementId,
                                              WarehouseStockStatus status,
                                              BigDecimal unitCost) {
        return new StockReceiptCommand(
                request.warehouseId(),
                request.sparePartId(),
                request.toBinId(),
                request.quantity(),
                unitCost,
                request.lotNumber(),
                request.serialNumber(),
                request.expiryDate(),
                status,
                REFERENCE_TYPE,
                movementId,
                request.documentNumber(),
                request.comment(),
                "warehouse-bin-move-in:" + movementId
        );
    }

    private void validate(WarehouseStockMoveRequest request) {
        if (request == null) {
            throw RestException.badRequest("request is required");
        }
        if (request.warehouseId() == null) {
            throw RestException.badRequest("warehouseId is required");
        }
        if (request.sparePartId() == null) {
            throw RestException.badRequest("sparePartId is required");
        }
        if (request.fromBinId() == null) {
            throw RestException.badRequest("fromBinId is required");
        }
        if (request.toBinId() == null) {
            throw RestException.badRequest("toBinId is required");
        }
        if (Objects.equals(request.fromBinId(), request.toBinId())) {
            throw RestException.badRequest("fromBinId and toBinId must be different");
        }
        if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw RestException.badRequest("Quantity must be greater than 0");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
