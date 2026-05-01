package com.toir.service;
import com.toir.entity.StockMovement;
import com.toir.repository.StockMovementRepository;

import com.toir.exception.RestException;
import com.toir.dto.stockmovement.StockMovementDto;
import com.toir.dto.stockmovement.StockMovementRequest;
import com.toir.entity.WarehouseStock;
import com.toir.entity.SparePart;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.SparePartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class StockMovementService {

    private final StockMovementRepository repository;
    private final WarehouseStockRepository stockRepository;
    private final SparePartRepository sparePartRepository;


    @Transactional(readOnly = true)
    public List<StockMovementDto> findAll() {
        return com.toir.util.UpdatedAtSorter.descending(repository.findAllByIsDeletedFalse()).stream().map(StockMovementDto::from).toList();
    }

    public StockMovementDto create(StockMovementRequest request) {
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
            case ADJUSTMENT -> stock.setQuantity(request.quantity());
            case TRANSFER -> {
                if (stock.getAvailable() < request.quantity()) {
                    throw RestException.badRequest("Cannot transfer more than available");
                }
                stock.setQuantity(stock.getQuantity() - request.quantity());
            }
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
        return StockMovementDto.from(repository.save(movement));
    }
}
