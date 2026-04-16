package com.toir.sparepart;

import com.toir.common.exception.RestException;
import com.toir.sparepart.dto.SparePartDto;
import com.toir.sparepart.dto.SparePartRequest;
import com.toir.warehouse.WarehouseStock;
import com.toir.warehouse.WarehouseStockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class SparePartService {

    private final SparePartRepository repository;
    private final WarehouseStockRepository stockRepository;

    public SparePartService(SparePartRepository repository, WarehouseStockRepository stockRepository) {
        this.repository = repository;
        this.stockRepository = stockRepository;
    }

    @Transactional(readOnly = true)
    public List<SparePartDto> findAll() {
        Map<UUID, List<WarehouseStock>> stocksByPart = stockRepository.findAll().stream()
                .filter(s -> s.getSparePartId() != null)
                .collect(Collectors.groupingBy(WarehouseStock::getSparePartId));

        return repository.findAll().stream()
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
        List<WarehouseStock> stocks = stockRepository.findAllBySparePartId(id);
        double currentStock = stocks.stream().mapToDouble(WarehouseStock::getQuantity).sum();
        double reservedStock = stocks.stream().mapToDouble(WarehouseStock::getReservedQty).sum();
        return SparePartDto.from(part, currentStock, reservedStock, stocks.size());
    }

    public SparePartDto create(SparePartRequest request) {
        if (repository.existsByCode(request.code())) {
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
        repository.delete(getOrThrow(id));
    }

    SparePart getOrThrow(UUID id) {
        return repository.findById(id)
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
