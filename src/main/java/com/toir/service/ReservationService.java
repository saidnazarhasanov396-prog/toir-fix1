package com.toir.service;
import com.toir.entity.Reservation;
import com.toir.repository.ReservationRepository;
import com.toir.enums.ReservationStatus;

import com.toir.exception.RestException;
import com.toir.dto.reservation.ReservationDto;
import com.toir.dto.reservation.ReservationRequest;
import com.toir.entity.WarehouseStock;
import com.toir.repository.WarehouseStockRepository;
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


    @Transactional(readOnly = true)
    public List<ReservationDto> findByWorkOrder(UUID workOrderId) {
        return repository.findAllByWorkOrderIdAndIsDeletedFalse(workOrderId).stream().map(ReservationDto::from).toList();
    }

    public ReservationDto reserve(ReservationRequest r) {
        WarehouseStock stock = stockRepository.findByIdAndIsDeletedFalse(r.warehouseStockId())
                .orElseThrow(() -> RestException.notFound("Stock not found: " + r.warehouseStockId()));
        if (stock.getAvailable() < r.quantity()) {
            throw RestException.badRequest("Cannot reserve more than available: available="
                    + stock.getAvailable() + ", requested=" + r.quantity());
        }
        stock.setReservedQty(stock.getReservedQty() + r.quantity());

        Reservation reservation = new Reservation();
        reservation.setWarehouseStockId(r.warehouseStockId());
        reservation.setWorkOrderId(r.workOrderId());
        reservation.setRepairRequestId(r.repairRequestId());
        reservation.setReservedById(r.reservedById());
        reservation.setQuantity(r.quantity());
        return ReservationDto.from(repository.save(reservation));
    }

    public ReservationDto cancel(UUID id) {
        Reservation reservation = getOrThrow(id);
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw RestException.badRequest("Only active reservations can be cancelled");
        }
        WarehouseStock stock = stockRepository.findByIdAndIsDeletedFalse(reservation.getWarehouseStockId()).orElseThrow();
        stock.setReservedQty(Math.max(0, stock.getReservedQty() - reservation.getQuantity()));
        reservation.setStatus(ReservationStatus.CANCELLED);
        return ReservationDto.from(reservation);
    }

    public ReservationDto fulfill(UUID id) {
        Reservation reservation = getOrThrow(id);
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw RestException.badRequest("Only active reservations can be fulfilled");
        }
        WarehouseStock stock = stockRepository.findByIdAndIsDeletedFalse(reservation.getWarehouseStockId()).orElseThrow();
        stock.setQuantity(stock.getQuantity() - reservation.getQuantity());
        stock.setReservedQty(Math.max(0, stock.getReservedQty() - reservation.getQuantity()));
        reservation.setStatus(ReservationStatus.FULFILLED);
        return ReservationDto.from(reservation);
    }

    private Reservation getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Reservation not found: " + id));
    }
}
