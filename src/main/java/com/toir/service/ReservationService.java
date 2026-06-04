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
import com.toir.exception.RestException;
import com.toir.repository.ReservationRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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


    @Transactional(readOnly = true)
    public List<ReservationDto> findByWorkOrder(UUID workOrderId) {
        return repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId).stream().map(ReservationDto::from).toList();
    }

    public ReservationDto reserve(ReservationRequest r) {
        validatePositiveQuantity(r.quantity());

        WarehouseStock stock = stockRepository.findByIdAndIsDeletedFalse(r.warehouseStockId())
                .orElseThrow(() -> RestException.notFound("Stock not found: " + r.warehouseStockId()));
        if (stock.getAvailable() < r.quantity()) {
            throw RestException.badRequest("Cannot reserve more than available: available="
                    + stock.getAvailable() + ", requested=" + r.quantity());
        }
        stock.setReservedQty(stock.getReservedQty() + r.quantity());
        stockRepository.save(stock);

        Reservation reservation = new Reservation();
        reservation.setWarehouseStockId(r.warehouseStockId());
        reservation.setWorkOrderId(r.workOrderId());
        reservation.setRepairRequestId(r.repairRequestId());
        reservation.setReservedById(r.reservedById());
        reservation.setQuantity(r.quantity());
        Reservation saved = repository.save(reservation);
        stockMovementRepository.save(buildMovement(stock, saved, StockMovementType.RESERVATION));

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

        WarehouseStock stock = stockRepository.findByIdAndIsDeletedFalse(reservation.getWarehouseStockId()).orElseThrow();
        stock.setReservedQty(Math.max(0, stock.getReservedQty() - reservation.getQuantity()));
        stockRepository.save(stock);
        reservation.setStatus(ReservationStatus.CANCELLED);

        Reservation saved = repository.save(reservation);
        stockMovementRepository.save(buildMovement(stock, saved, StockMovementType.RELEASE));

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

        WarehouseStock stock = stockRepository.findByIdAndIsDeletedFalse(reservation.getWarehouseStockId()).orElseThrow();
        if (stock.getQuantity() < reservation.getQuantity()) {
            throw RestException.badRequest("Cannot fulfill more than stock quantity: available="
                    + stock.getQuantity() + ", requested=" + reservation.getQuantity());
        }
        stock.setQuantity(stock.getQuantity() - reservation.getQuantity());
        stock.setReservedQty(Math.max(0, stock.getReservedQty() - reservation.getQuantity()));
        stockRepository.save(stock);
        reservation.setStatus(ReservationStatus.FULFILLED);

        Reservation saved = repository.save(reservation);
        stockMovementRepository.save(buildMovement(stock, saved, StockMovementType.ISSUE));
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

    private void validatePositiveQuantity(double quantity) {
        if (quantity <= 0) {
            throw RestException.badRequest("Quantity must be greater than 0");
        }
    }

    private StockMovement buildMovement(WarehouseStock stock, Reservation reservation, StockMovementType type) {
        StockMovement movement = new StockMovement();
        movement.setWarehouseId(stock.getWarehouseId());
        movement.setSparePartId(stock.getSparePartId());
        movement.setWorkOrderId(reservation.getWorkOrderId());
        movement.setCreatedById(reservation.getReservedById());
        movement.setType(type);
        movement.setQuantity(reservation.getQuantity());
        return movement;
    }
}
