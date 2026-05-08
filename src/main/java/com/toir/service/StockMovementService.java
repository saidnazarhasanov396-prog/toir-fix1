package com.toir.service;

import com.toir.dto.stockmovement.StockMovementDto;
import com.toir.dto.stockmovement.StockMovementRequest;
import com.toir.entity.SparePart;
import com.toir.entity.StockMovement;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockMovementService {

    private final StockMovementRepository repository;
    private final WarehouseStockRepository stockRepository;
    private final SparePartRepository sparePartRepository;
    private final AuditBuilderService auditBuilderService;


    @Transactional(readOnly = true)
    public List<StockMovementDto> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(StockMovementDto::from).toList();
    }

    @Transactional
    public StockMovementDto create(StockMovementRequest request) {
        validatePositiveQuantity(request.quantity());

        WarehouseStock stock = stockRepository
                .findByWarehouseIdAndSparePartIdAndIsDeletedFalse(request.warehouseId(), request.sparePartId())
                .orElseGet(() -> {
                    SparePart sparePart = sparePartRepository.findByIdAndIsDeletedFalse(request.sparePartId())
                            .orElseThrow(() -> RestException.notFound("SparePart not found: " + request.sparePartId()));
                    WarehouseStock s = new WarehouseStock();
                    s.setWarehouseId(request.warehouseId());
                    s.setSparePart(sparePart);
                    s.setQuantity(0);
                    s.setReservedQty(0);
                    s.setMinQty(0);
                    return stockRepository.save(s);
                });

        switch (request.type()) {
            case RECEIPT, RETURN -> stock.setQuantity(stock.getQuantity() + request.quantity());
            case ISSUE -> {
                if (stock.getAvailable() < request.quantity()) {
                    throw RestException.badRequest("Cannot issue more than available: available="
                            + stock.getAvailable() + ", requested=" + request.quantity());
                }
                stock.setQuantity(stock.getQuantity() - request.quantity());
            }
            case RESERVATION -> {
                if (stock.getAvailable() < request.quantity()) {
                    throw RestException.badRequest("Cannot reserve more than available");
                }
                stock.setReservedQty(stock.getReservedQty() + request.quantity());
            }
            case RELEASE -> stock.setReservedQty(Math.max(0, stock.getReservedQty() - request.quantity()));
            case ADJUSTMENT -> {
                if (request.quantity() < stock.getReservedQty()) {
                    throw RestException.badRequest("Cannot adjust quantity below reserved: reserved="
                            + stock.getReservedQty() + ", requested=" + request.quantity());
                }
                stock.setQuantity(request.quantity());
            }
            case TRANSFER -> {
                if (stock.getAvailable() < request.quantity()) {
                    throw RestException.badRequest("Cannot transfer more than available");
                }
                stock.setQuantity(stock.getQuantity() - request.quantity());
            }
        }
        if (stock.getQuantity() < 0) {
            throw RestException.badRequest("Stock quantity cannot be negative");
        }

        StockMovement movement = new StockMovement();
        movement.setWarehouseId(request.warehouseId());
        movement.setSparePartId(request.sparePartId());
        movement.setWorkOrderId(request.workOrderId());
        movement.setType(request.type());
        movement.setQuantity(request.quantity());
        movement.setUnitCost(request.unitCost());
        movement.setDocumentNumber(request.documentNumber());
        movement.setCreatedById(request.createdById());
        movement.setNotes(request.notes());
        StockMovement saved = repository.save(movement);

        auditBuilderService.log(
                "stock_movement",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.STOCK_MOVEMENT,
                "Движение склада создано",
                null,
                saved
        );

        return StockMovementDto.from(saved);
    }

    private void validatePositiveQuantity(double quantity) {
        if (quantity <= 0) {
            throw RestException.badRequest("Quantity must be greater than 0");
        }
    }
}
