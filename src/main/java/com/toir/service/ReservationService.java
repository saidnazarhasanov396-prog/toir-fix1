package com.toir.service;

import com.toir.dto.reservation.ReservationDto;
import com.toir.dto.reservation.ReservationRequest;
import com.toir.entity.Reservation;
import com.toir.entity.StockMovement;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.ReservationStatus;
import com.toir.enums.StockMovementType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.exception.RestException;
import com.toir.repository.ReservationRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.service.warehouse.ToirStockService;
import com.toir.service.warehouse.LegacyStockProjectionService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository repository;
    private final WarehouseStockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AuditBuilderService auditBuilderService;
    private final LowStockRecommendationService lowStockRecommendationService;
    private final ToirStockService toirStockService;
    private final LegacyStockProjectionService legacyStockProjectionService;


    @Transactional(readOnly = true)
    public List<ReservationDto> findByWorkOrder(UUID workOrderId) {
        return repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId).stream().map(ReservationDto::from).toList();
    }

    public ReservationDto reserve(ReservationRequest r) {
        validatePositiveQuantity(r.quantity());

        Reservation reservation = new Reservation();
        reservation.setWarehouseStockId(r.warehouseStockId());
        if (r.warehouseStockId() != null) {
            WarehouseStock stock = stockRepository.findByIdAndIsDeletedFalse(r.warehouseStockId())
                    .orElseThrow(() -> RestException.notFound("Stock not found: " + r.warehouseStockId()));
            reservation.setWarehouseId(stock.getWarehouseId());
            reservation.setSparePartId(stock.getSparePartId());
        } else {
            if (r.warehouseId() == null || r.sparePartId() == null) {
                throw RestException.badRequest("warehouseId and sparePartId are required for direct WMS reservation");
            }
            reservation.setWarehouseId(r.warehouseId());
            reservation.setSparePartId(r.sparePartId());
        }
        reservation.setBinId(r.binId());
        reservation.setRequirementId(r.requirementId());
        reservation.setLotNumber(trimToNull(r.lotNumber()));
        reservation.setSerialNumber(trimToNull(r.serialNumber()));
        reservation.setExpiryDate(r.expiryDate());
        reservation.setStockStatus(r.effectiveStatus());
        reservation.setWorkOrderId(r.workOrderId());
        reservation.setRepairRequestId(r.repairRequestId());
        reservation.setReservedById(r.reservedById());
        reservation.setQuantity(r.quantity());
        Reservation saved = repository.save(reservation);
        postCoreReserve(saved);
        WarehouseStock stock = legacyStockProjectionService.sync(saved.getWarehouseId(), saved.getSparePartId());
        stockMovementRepository.save(buildMovement(saved, StockMovementType.RESERVATION));
        lowStockRecommendationService.evaluateStockSafely(stock);

        auditBuilderService.log(
                "reservation",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.RESERVATION,
                "Резерв создан",
                null,
                saved
        );

        return ReservationDto.from(saved);
    }

    public ReservationDto cancel(UUID id) {
        Reservation reservation = getOrThrow(id);
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw RestException.badRequest("Only active reservations can be cancelled");
        }
        validatePositiveQuantity(reservation.getQuantity());
        hydrateCoordinates(reservation);

        reservation.setStatus(ReservationStatus.CANCELLED);

        Reservation saved = repository.save(reservation);
        postCoreRelease(saved);
        WarehouseStock stock = legacyStockProjectionService.sync(saved.getWarehouseId(), saved.getSparePartId());
        stockMovementRepository.save(buildMovement(saved, StockMovementType.RELEASE));
        lowStockRecommendationService.evaluateStockSafely(stock);

        auditBuilderService.log(
                "reservation",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.RESERVATION,
                "Резерв обновлен",
                reservation,
                saved
        );

        return ReservationDto.from(saved);
    }

    @Transactional
    public ReservationDto fulfill(UUID id) {
        Reservation reservation = getOrThrow(id);
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw RestException.badRequest("Only active reservations can be fulfilled");
        }
        validatePositiveQuantity(reservation.getQuantity());
        hydrateCoordinates(reservation);

        reservation.setStatus(ReservationStatus.FULFILLED);

        Reservation saved = repository.save(reservation);
        postCoreFulfill(saved);
        WarehouseStock stock = legacyStockProjectionService.sync(saved.getWarehouseId(), saved.getSparePartId());
        stockMovementRepository.save(buildMovement(saved, StockMovementType.ISSUE));
        lowStockRecommendationService.evaluateStockSafely(stock);

        auditBuilderService.log(
                "reservation",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.RESERVATION,
                "Резерв обновлен",
                reservation,
                saved
        );

        return ReservationDto.from(reservation);
    }

    private Reservation getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Reservation not found: " + id));
    }

    private void hydrateCoordinates(Reservation reservation) {
        if (reservation.getWarehouseId() != null && reservation.getSparePartId() != null) {
            if (reservation.getStockStatus() == null) {
                reservation.setStockStatus(WarehouseStockStatus.AVAILABLE);
            }
            return;
        }
        if (reservation.getWarehouseStockId() == null) {
            throw RestException.badRequest("Reservation warehouseId and sparePartId are required");
        }
        WarehouseStock stock = stockRepository.findByIdAndIsDeletedFalse(reservation.getWarehouseStockId())
                .orElseThrow(() -> RestException.notFound(
                        "Stock not found: " + reservation.getWarehouseStockId()));
        reservation.setWarehouseId(stock.getWarehouseId());
        reservation.setSparePartId(stock.getSparePartId());
    }

    private void validatePositiveQuantity(double quantity) {
        if (quantity <= 0) {
            throw RestException.badRequest("Quantity must be greater than 0");
        }
    }

    private StockMovement buildMovement(Reservation reservation, StockMovementType type) {
        StockMovement movement = new StockMovement();
        movement.setWarehouseId(reservation.getWarehouseId());
        movement.setSparePartId(reservation.getSparePartId());
        movement.setBinId(reservation.getBinId());
        movement.setWorkOrderId(reservation.getWorkOrderId());
        movement.setCreatedById(reservation.getReservedById());
        movement.setType(type);
        movement.setQuantity(reservation.getQuantity());
        movement.setLotNumber(reservation.getLotNumber());
        movement.setSerialNumber(reservation.getSerialNumber());
        movement.setExpiryDate(reservation.getExpiryDate());
        movement.setStockStatus(effectiveStatus(reservation.getStockStatus()));
        return movement;
    }

    private void postCoreReserve(Reservation reservation) {
        if (usesDetailedIdentity(reservation)) {
            toirStockService.reserve(
                    reservation.getWarehouseId(),
                    reservation.getSparePartId(),
                    reservation.getBinId(),
                    reservation.getLotNumber(),
                    reservation.getSerialNumber(),
                    reservation.getExpiryDate(),
                    effectiveStatus(reservation.getStockStatus()),
                    quantity(reservation.getQuantity()),
                    "RESERVATION",
                    reservation.getId(),
                    null,
                    "reservation-reserve:" + reservation.getId()
            );
            return;
        }
        toirStockService.reserve(
                reservation.getWarehouseId(),
                reservation.getSparePartId(),
                reservation.getBinId(),
                quantity(reservation.getQuantity()),
                "RESERVATION",
                reservation.getId(),
                null,
                "reservation-reserve:" + reservation.getId()
        );
    }

    private void postCoreRelease(Reservation reservation) {
        if (usesDetailedIdentity(reservation)) {
            toirStockService.releaseReservation(
                    reservation.getWarehouseId(),
                    reservation.getSparePartId(),
                    reservation.getBinId(),
                    reservation.getLotNumber(),
                    reservation.getSerialNumber(),
                    reservation.getExpiryDate(),
                    effectiveStatus(reservation.getStockStatus()),
                    quantity(reservation.getQuantity()),
                    "RESERVATION",
                    reservation.getId(),
                    null,
                    "reservation-cancel:" + reservation.getId()
            );
            return;
        }
        toirStockService.releaseReservation(
                reservation.getWarehouseId(),
                reservation.getSparePartId(),
                reservation.getBinId(),
                quantity(reservation.getQuantity()),
                "RESERVATION",
                reservation.getId(),
                null,
                "reservation-cancel:" + reservation.getId()
        );
    }

    private void postCoreFulfill(Reservation reservation) {
        if (usesDetailedIdentity(reservation)) {
            toirStockService.fulfillReservation(
                    reservation.getWarehouseId(),
                    reservation.getSparePartId(),
                    reservation.getBinId(),
                    reservation.getLotNumber(),
                    reservation.getSerialNumber(),
                    reservation.getExpiryDate(),
                    effectiveStatus(reservation.getStockStatus()),
                    quantity(reservation.getQuantity()),
                    "RESERVATION",
                    reservation.getId(),
                    null,
                    "reservation-fulfill:" + reservation.getId()
            );
            return;
        }
        toirStockService.fulfillReservation(
                reservation.getWarehouseId(),
                reservation.getSparePartId(),
                reservation.getBinId(),
                quantity(reservation.getQuantity()),
                "RESERVATION",
                reservation.getId(),
                null,
                "reservation-fulfill:" + reservation.getId()
        );
    }

    private boolean usesDetailedIdentity(Reservation reservation) {
        return reservation.getBinId() != null
                || trimToNull(reservation.getLotNumber()) != null
                || trimToNull(reservation.getSerialNumber()) != null
                || reservation.getExpiryDate() != null
                || (reservation.getStockStatus() != null && reservation.getStockStatus() != WarehouseStockStatus.AVAILABLE);
    }

    private WarehouseStockStatus effectiveStatus(WarehouseStockStatus status) {
        return status == null ? WarehouseStockStatus.AVAILABLE : status;
    }

    private BigDecimal quantity(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
