package com.toir.service.warehouse;

import com.toir.dto.warehouse.StockIssueCommand;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.entity.warehouse.WarehouseStockBalance;
import com.toir.entity.warehouse.WarehouseStockLedger;
import com.toir.entity.warehouse.WarehouseReservationLedger;
import com.toir.enums.StockLedgerMovementType;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseReservationLedgerRepository;
import com.toir.repository.WarehouseStockBalanceRepository;
import com.toir.repository.WarehouseStockLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ToirStockService {

    private static final int COST_SCALE = 4;

    private final WarehouseStockBalanceRepository balanceRepository;
    private final WarehouseStockLedgerRepository ledgerRepository;
    private final WarehouseReservationLedgerRepository reservationLedgerRepository;

    @Transactional
    public WarehouseStockLedger postReceipt(StockReceiptCommand command) {
        return postIncrease(command, StockLedgerMovementType.RECEIPT);
    }

    @Transactional
    public WarehouseStockLedger postIncrease(StockReceiptCommand command, StockLedgerMovementType movementType) {
        validateIncreaseMovementType(movementType);
        validateRequiredIds(command.warehouseId(), command.sparePartId());
        validatePositiveQuantity(command.quantity());
        validateNonNegativeCost(command.unitCost());

        Optional<WarehouseStockLedger> existingLedger = existingLedger(command.idempotencyKey());
        if (existingLedger.isPresent()) {
            return existingLedger.get();
        }

        String identityKey = identityKey(
                command.warehouseId(),
                command.sparePartId(),
                command.binId(),
                command.lotNumber(),
                command.serialNumber()
        );
        WarehouseStockBalance balance = balanceRepository.lockByIdentityKey(identityKey)
                .orElseGet(() -> newBalance(command, identityKey));

        BigDecimal previousQty = zero(balance.getQtyOnHand());
        BigDecimal receiptQty = command.quantity();
        balance.setQtyOnHand(previousQty.add(receiptQty));
        balance.setQtyReserved(zero(balance.getQtyReserved()));
        balance.setAvgCost(weightedAverageCost(previousQty, balance.getAvgCost(), receiptQty, command.unitCost()));
        balance.prepareForSave();
        balanceRepository.save(balance);

        WarehouseStockLedger ledger = ledger(
                command.warehouseId(),
                command.sparePartId(),
                command.binId(),
                command.lotNumber(),
                command.serialNumber(),
                movementType,
                receiptQty,
                command.unitCost(),
                command.referenceType(),
                command.referenceId(),
                command.referenceDocNo(),
                command.notes(),
                command.idempotencyKey()
        );
        return ledgerRepository.save(ledger);
    }

    @Transactional
    public WarehouseStockLedger postIssue(StockIssueCommand command) {
        return postDecrease(command, StockLedgerMovementType.ISSUE);
    }

    @Transactional
    public WarehouseReservationLedger reserve(UUID warehouseId,
                                              UUID sparePartId,
                                              UUID binId,
                                              BigDecimal quantity,
                                              String referenceType,
                                              UUID referenceId,
                                              String referenceDocNo,
                                              String idempotencyKey) {
        validateRequiredIds(warehouseId, sparePartId);
        validatePositiveQuantity(quantity);

        Optional<WarehouseReservationLedger> existingLedger = existingReservationLedger(idempotencyKey);
        if (existingLedger.isPresent()) {
            return existingLedger.get();
        }

        WarehouseStockBalance balance = lockedBalance(warehouseId, sparePartId, binId, null, null);
        BigDecimal availableQty = balance.getAvailableQty();
        if (availableQty.compareTo(quantity) < 0) {
            throw RestException.badRequest("Insufficient available stock: available="
                    + availableQty + ", requested=" + quantity);
        }

        balance.setQtyReserved(zero(balance.getQtyReserved()).add(quantity));
        balance.prepareForSave();
        balanceRepository.save(balance);

        WarehouseReservationLedger ledger = reservationLedger(
                warehouseId,
                sparePartId,
                binId,
                null,
                null,
                StockLedgerMovementType.RESERVE,
                quantity,
                referenceType,
                referenceId,
                referenceDocNo,
                "reserved stock",
                idempotencyKey
        );
        return reservationLedgerRepository.save(ledger);
    }

    @Transactional
    public WarehouseReservationLedger releaseReservation(UUID warehouseId,
                                                        UUID sparePartId,
                                                        UUID binId,
                                                        BigDecimal quantity,
                                                        String referenceType,
                                                        UUID referenceId,
                                                        String referenceDocNo,
                                                        String idempotencyKey) {
        validateRequiredIds(warehouseId, sparePartId);
        validatePositiveQuantity(quantity);

        Optional<WarehouseReservationLedger> existingLedger = existingReservationLedger(idempotencyKey);
        if (existingLedger.isPresent()) {
            return existingLedger.get();
        }

        WarehouseStockBalance balance = lockedBalance(warehouseId, sparePartId, binId, null, null);
        assertReservedCanCover(balance, quantity);

        balance.setQtyReserved(zero(balance.getQtyReserved()).subtract(quantity));
        balance.prepareForSave();
        balanceRepository.save(balance);

        WarehouseReservationLedger ledger = reservationLedger(
                warehouseId,
                sparePartId,
                binId,
                null,
                null,
                StockLedgerMovementType.RELEASE,
                quantity.negate(),
                referenceType,
                referenceId,
                referenceDocNo,
                "released reservation",
                idempotencyKey
        );
        return reservationLedgerRepository.save(ledger);
    }

    @Transactional
    public WarehouseStockLedger fulfillReservation(UUID warehouseId,
                                                  UUID sparePartId,
                                                  UUID binId,
                                                  BigDecimal quantity,
                                                  String referenceType,
                                                  UUID referenceId,
                                                  String referenceDocNo,
                                                  String idempotencyKey) {
        validateRequiredIds(warehouseId, sparePartId);
        validatePositiveQuantity(quantity);

        Optional<WarehouseStockLedger> existingLedger = existingLedger(idempotencyKey);
        if (existingLedger.isPresent()) {
            return existingLedger.get();
        }

        WarehouseStockBalance balance = lockedBalance(warehouseId, sparePartId, binId, null, null);
        assertReservedCanCover(balance, quantity);
        if (zero(balance.getQtyOnHand()).compareTo(quantity) < 0) {
            throw RestException.badRequest("Cannot fulfill more than stock quantity: available="
                    + zero(balance.getQtyOnHand()) + ", requested=" + quantity);
        }

        balance.setQtyOnHand(zero(balance.getQtyOnHand()).subtract(quantity));
        balance.setQtyReserved(zero(balance.getQtyReserved()).subtract(quantity));
        balance.prepareForSave();
        balanceRepository.save(balance);

        reservationLedgerRepository.save(reservationLedger(
                warehouseId,
                sparePartId,
                binId,
                null,
                null,
                StockLedgerMovementType.RELEASE,
                quantity.negate(),
                referenceType,
                referenceId,
                referenceDocNo,
                "fulfilled reservation",
                idempotencyKey == null ? null : idempotencyKey + ":reservation"
        ));

        WarehouseStockLedger ledger = ledger(
                warehouseId,
                sparePartId,
                binId,
                null,
                null,
                StockLedgerMovementType.ISSUE,
                quantity.negate(),
                balance.getAvgCost(),
                referenceType,
                referenceId,
                referenceDocNo,
                "fulfilled reservation",
                idempotencyKey
        );
        return ledgerRepository.save(ledger);
    }

    @Transactional
    public WarehouseStockLedger postDecrease(StockIssueCommand command, StockLedgerMovementType movementType) {
        validateDecreaseMovementType(movementType);
        validateRequiredIds(command.warehouseId(), command.sparePartId());
        validatePositiveQuantity(command.quantity());

        Optional<WarehouseStockLedger> existingLedger = existingLedger(command.idempotencyKey());
        if (existingLedger.isPresent()) {
            return existingLedger.get();
        }

        String identityKey = identityKey(
                command.warehouseId(),
                command.sparePartId(),
                command.binId(),
                command.lotNumber(),
                command.serialNumber()
        );
        WarehouseStockBalance balance = balanceRepository.lockByIdentityKey(identityKey)
                .orElseThrow(() -> RestException.badRequest("No stock balance exists for requested item"));

        BigDecimal requestedQty = command.quantity();
        BigDecimal availableQty = balance.getAvailableQty();
        if (availableQty.compareTo(requestedQty) < 0) {
            throw RestException.badRequest("Insufficient available stock: available="
                    + availableQty + ", requested=" + requestedQty);
        }

        balance.setQtyOnHand(zero(balance.getQtyOnHand()).subtract(requestedQty));
        balance.prepareForSave();
        balanceRepository.save(balance);

        WarehouseStockLedger ledger = ledger(
                command.warehouseId(),
                command.sparePartId(),
                command.binId(),
                command.lotNumber(),
                command.serialNumber(),
                movementType,
                requestedQty.negate(),
                balance.getAvgCost(),
                command.referenceType(),
                command.referenceId(),
                command.referenceDocNo(),
                command.notes(),
                command.idempotencyKey()
        );
        return ledgerRepository.save(ledger);
    }

    private Optional<WarehouseStockLedger> existingLedger(String idempotencyKey) {
        String normalized = trimToNull(idempotencyKey);
        if (normalized == null) {
            return Optional.empty();
        }
        return ledgerRepository.findByIdempotencyKeyAndIsDeletedFalse(normalized);
    }

    private Optional<WarehouseReservationLedger> existingReservationLedger(String idempotencyKey) {
        String normalized = trimToNull(idempotencyKey);
        if (normalized == null) {
            return Optional.empty();
        }
        return reservationLedgerRepository.findByIdempotencyKeyAndIsDeletedFalse(normalized);
    }

    private WarehouseStockBalance lockedBalance(UUID warehouseId,
                                                UUID sparePartId,
                                                UUID binId,
                                                String lotNumber,
                                                String serialNumber) {
        String identityKey = identityKey(warehouseId, sparePartId, binId, lotNumber, serialNumber);
        return balanceRepository.lockByIdentityKey(identityKey)
                .orElseThrow(() -> RestException.badRequest("No stock balance exists for requested item"));
    }

    private WarehouseStockBalance newBalance(StockReceiptCommand command, String identityKey) {
        WarehouseStockBalance balance = new WarehouseStockBalance();
        balance.setWarehouseId(command.warehouseId());
        balance.setSparePartId(command.sparePartId());
        balance.setBinId(command.binId());
        balance.setLotNumber(trimToNull(command.lotNumber()));
        balance.setSerialNumber(trimToNull(command.serialNumber()));
        balance.setExpiryDate(command.expiryDate());
        balance.setQtyOnHand(BigDecimal.ZERO);
        balance.setQtyReserved(BigDecimal.ZERO);
        balance.setIdentityKey(identityKey);
        return balance;
    }

    private WarehouseStockLedger ledger(UUID warehouseId,
                                        UUID sparePartId,
                                        UUID binId,
                                        String lotNumber,
                                        String serialNumber,
                                        StockLedgerMovementType movementType,
                                        BigDecimal quantity,
                                        BigDecimal unitCost,
                                        String referenceType,
                                        UUID referenceId,
                                        String referenceDocNo,
                                        String notes,
                                        String idempotencyKey) {
        WarehouseStockLedger ledger = new WarehouseStockLedger();
        ledger.setWarehouseId(warehouseId);
        ledger.setSparePartId(sparePartId);
        ledger.setBinId(binId);
        ledger.setLotNumber(trimToNull(lotNumber));
        ledger.setSerialNumber(trimToNull(serialNumber));
        ledger.setMovementType(movementType);
        ledger.setQuantity(quantity);
        ledger.setUnitCost(unitCost);
        ledger.setTotalCost(unitCost == null ? null : quantity.multiply(unitCost));
        ledger.setReferenceType(trimToNull(referenceType));
        ledger.setReferenceId(referenceId);
        ledger.setReferenceDocNo(trimToNull(referenceDocNo));
        ledger.setNotes(trimToNull(notes));
        ledger.setIdempotencyKey(trimToNull(idempotencyKey));
        ledger.setPostedAt(Instant.now());
        return ledger;
    }

    private WarehouseReservationLedger reservationLedger(UUID warehouseId,
                                                        UUID sparePartId,
                                                        UUID binId,
                                                        String lotNumber,
                                                        String serialNumber,
                                                        StockLedgerMovementType movementType,
                                                        BigDecimal quantity,
                                                        String referenceType,
                                                        UUID referenceId,
                                                        String referenceDocNo,
                                                        String notes,
                                                        String idempotencyKey) {
        WarehouseReservationLedger ledger = new WarehouseReservationLedger();
        ledger.setWarehouseId(warehouseId);
        ledger.setSparePartId(sparePartId);
        ledger.setBinId(binId);
        ledger.setLotNumber(trimToNull(lotNumber));
        ledger.setSerialNumber(trimToNull(serialNumber));
        ledger.setMovementType(movementType);
        ledger.setQuantity(quantity);
        ledger.setReferenceType(trimToNull(referenceType));
        ledger.setReferenceId(referenceId);
        ledger.setReferenceDocNo(trimToNull(referenceDocNo));
        ledger.setNotes(trimToNull(notes));
        ledger.setIdempotencyKey(trimToNull(idempotencyKey));
        ledger.setPostedAt(Instant.now());
        return ledger;
    }

    private void assertReservedCanCover(WarehouseStockBalance balance, BigDecimal quantity) {
        BigDecimal reservedQty = zero(balance.getQtyReserved());
        if (reservedQty.compareTo(quantity) < 0) {
            throw RestException.badRequest("Stock reserved quantity is lower than reservation quantity: reserved="
                    + reservedQty + ", requested=" + quantity);
        }
    }

    private BigDecimal weightedAverageCost(BigDecimal previousQty,
                                           BigDecimal previousAvgCost,
                                           BigDecimal receiptQty,
                                           BigDecimal receiptUnitCost) {
        if (receiptUnitCost == null) {
            return previousAvgCost;
        }
        if (previousAvgCost == null || previousQty.signum() == 0) {
            return receiptUnitCost;
        }
        BigDecimal totalQty = previousQty.add(receiptQty);
        if (totalQty.signum() == 0) {
            return receiptUnitCost;
        }
        BigDecimal previousValue = previousQty.multiply(previousAvgCost);
        BigDecimal receiptValue = receiptQty.multiply(receiptUnitCost);
        return previousValue.add(receiptValue).divide(totalQty, COST_SCALE, RoundingMode.HALF_UP);
    }

    private void validateRequiredIds(UUID warehouseId, UUID sparePartId) {
        if (warehouseId == null) {
            throw RestException.badRequest("warehouseId is required");
        }
        if (sparePartId == null) {
            throw RestException.badRequest("sparePartId is required");
        }
    }

    private void validatePositiveQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw RestException.badRequest("Quantity must be greater than 0");
        }
    }

    private void validateNonNegativeCost(BigDecimal unitCost) {
        if (unitCost != null && unitCost.compareTo(BigDecimal.ZERO) < 0) {
            throw RestException.badRequest("unitCost must be greater than or equal to 0");
        }
    }

    private void validateIncreaseMovementType(StockLedgerMovementType movementType) {
        if (movementType == null) {
            throw RestException.badRequest("movementType is required");
        }
        if (!EnumSet.of(
                StockLedgerMovementType.RECEIPT,
                StockLedgerMovementType.RETURN,
                StockLedgerMovementType.TRANSFER_IN,
                StockLedgerMovementType.ADJUSTMENT_INC,
                StockLedgerMovementType.MOVE_IN
        ).contains(movementType)) {
            throw RestException.badRequest("Unsupported stock increase movement type: " + movementType);
        }
    }

    private void validateDecreaseMovementType(StockLedgerMovementType movementType) {
        if (movementType == null) {
            throw RestException.badRequest("movementType is required");
        }
        if (!EnumSet.of(
                StockLedgerMovementType.ISSUE,
                StockLedgerMovementType.TRANSFER_OUT,
                StockLedgerMovementType.ADJUSTMENT_DEC,
                StockLedgerMovementType.WRITEOFF,
                StockLedgerMovementType.MOVE_OUT
        ).contains(movementType)) {
            throw RestException.badRequest("Unsupported stock decrease movement type: " + movementType);
        }
    }

    private String identityKey(UUID warehouseId, UUID sparePartId, UUID binId, String lotNumber, String serialNumber) {
        return WarehouseStockBalance.buildIdentityKey(warehouseId, sparePartId, binId, lotNumber, serialNumber);
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
