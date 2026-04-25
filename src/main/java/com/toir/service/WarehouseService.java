package com.toir.service;
import com.toir.entity.Warehouse;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;

import com.toir.exception.RestException;
import com.toir.dto.warehouse.WarehouseDto;
import com.toir.dto.warehouse.WarehouseRequest;
import com.toir.dto.warehouse.WarehouseStockDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository repository;
    private final WarehouseStockRepository stockRepository;


    @Transactional(readOnly = true)
    public List<WarehouseDto> findAll() {
        return repository.findAllByIsDeletedFalse().stream()
                .map(w -> WarehouseDto.fromWithStocks(w, stockRepository.findAllByWarehouseIdAndIsDeletedFalse(w.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public WarehouseDto findById(UUID id) {
        Warehouse w = getOrThrow(id);
        return WarehouseDto.fromWithStocks(w, stockRepository.findAllByWarehouseIdAndIsDeletedFalse(id));
    }

    @Transactional(readOnly = true)
    public List<WarehouseStockDto> findStocks(UUID warehouseId) {
        getOrThrow(warehouseId);
        return stockRepository.findAllByWarehouseIdAndIsDeletedFalse(warehouseId).stream().map(WarehouseStockDto::from).toList();
    }

    public WarehouseDto create(WarehouseRequest request) {
        if (repository.existsByCodeAndIsDeletedFalse(request.code())) {
            throw RestException.conflict("Warehouse code already exists: " + request.code());
        }
        Warehouse entity = new Warehouse();
        apply(entity, request);
        return WarehouseDto.from(repository.save(entity));
    }

    public WarehouseDto update(UUID id, WarehouseRequest request) {
        Warehouse entity = getOrThrow(id);
        apply(entity, request);
        return WarehouseDto.from(entity);
    }

    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity);
    }

    Warehouse getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Warehouse not found: " + id));
    }

    private void apply(Warehouse entity, WarehouseRequest request) {
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setDepartmentId(request.departmentId());
        entity.setLocationId(request.locationId());
        entity.setResponsibleId(request.responsibleId());
        if (request.active() != null) entity.setActive(request.active());
    }
}
