package com.toir.service;
import com.toir.entity.SparePart;
import com.toir.repository.SparePartRepository;

import com.toir.exception.RestException;
import com.toir.dto.sparepart.SparePartDto;
import com.toir.dto.sparepart.SparePartRequest;
import com.toir.entity.WarehouseStock;
import com.toir.repository.WarehouseStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SparePartService {

    private final SparePartRepository repository;
    private final WarehouseStockRepository stockRepository;

    @Transactional(readOnly = true)
    public List<SparePartDto> findAll() {
        Map<UUID, List<WarehouseStock>> stocksByPart = stockRepository.findAllByIsDeletedFalse().stream()
                .filter(s -> s.getSparePartId() != null)
                .collect(Collectors.groupingBy(WarehouseStock::getSparePartId));

        return repository.findAllByIsDeletedFalse().stream()
                .map(part -> {
                    List<WarehouseStock> stocks = stocksByPart.getOrDefault(part.getId(), List.of());
                    double currentStock = stocks.stream().mapToDouble(WarehouseStock::getQuantity).sum();
                    double reservedStock = stocks.stream().mapToDouble(WarehouseStock::getReservedQty).sum();
                    return SparePartDto.from(part, currentStock, reservedStock, stocks.size());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public SparePartDto findById(UUID id) {
        SparePart part = getOrThrow(id);
        List<WarehouseStock> stocks = stockRepository.findAllBySparePartIdAndIsDeletedFalse(id);
        double currentStock = stocks.stream().mapToDouble(WarehouseStock::getQuantity).sum();
        double reservedStock = stocks.stream().mapToDouble(WarehouseStock::getReservedQty).sum();
        return SparePartDto.from(part, currentStock, reservedStock, stocks.size());
    }

    public SparePartDto create(SparePartRequest request) {
        if (repository.existsByCodeAndIsDeletedFalse(request.code())) {
            throw RestException.conflict("Spare part code already exists: " + request.code());
        }
        SparePart entity = new SparePart();
        apply(entity, request);
        return SparePartDto.from(repository.save(entity));
    }

    public SparePartDto update(UUID id, SparePartRequest request) {
        SparePart entity = getOrThrow(id);
        apply(entity, request);
        return SparePartDto.from(entity);
    }

    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity);
    }

    SparePart getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Spare part not found: " + id));
    }

    private void apply(SparePart entity, SparePartRequest request) {
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setSku(request.sku());
        if (request.kind() != null) entity.setKind(request.kind());
        entity.setUnit(request.unit());
        entity.setSpecification(request.specification());
        entity.setManufacturer(request.manufacturer());
        entity.setMinStock(request.minStock());
    }
}
