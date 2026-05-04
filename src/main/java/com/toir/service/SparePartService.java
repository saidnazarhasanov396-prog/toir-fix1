package com.toir.service;
import com.toir.entity.SparePart;
import com.toir.enums.InventoryItemKind;
import com.toir.repository.SparePartRepository;

import com.toir.exception.RestException;
import com.toir.dto.sparepart.SparePartDto;
import com.toir.dto.sparepart.SparePartRequest;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.repository.WarehouseStockRepository;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class SparePartService {

    private final SparePartRepository repository;
    private final WarehouseStockRepository stockRepository;

    @Transactional(readOnly = true)
    public Page<SparePartDto> findAll(Integer pageSize, Integer page, String itemType, String search) {
        int safePage = Math.max(page != null ? page : 0, 0);
        int safePageSize = Math.max(pageSize != null ? pageSize : 20, 1);
        InventoryItemKind inventoryItemKind = map(itemType);
        Page<SparePart> parts = repository.findAllByFilter(
                inventoryItemKind,
                toSearchPattern(search),
                PaginationUtils.pageRequest(safePage, safePageSize)
        );
        if (parts.isEmpty()) {
            return parts.map(SparePartDto::from);
        }
        Map<UUID, List<WarehouseStock>> stocksByPart = stockRepository
                .findAllBySparePartIdInAndIsDeletedFalseOrderByUpdatedAtDesc(parts.getContent().stream().map(SparePart::getId).toList())
                .stream()
                .filter(s -> s.getSparePartId() != null)
                .collect(Collectors.groupingBy(WarehouseStock::getSparePartId));

        return parts
                .map(part -> {
                    List<WarehouseStock> stocks = stocksByPart.getOrDefault(part.getId(), List.of());
                    double currentStock = stocks.stream().mapToDouble(WarehouseStock::getQuantity).sum();
                    double reservedStock = stocks.stream().mapToDouble(WarehouseStock::getReservedQty).sum();
                    return SparePartDto.from(part, currentStock, reservedStock, stocks.size());
                });
    }

    private InventoryItemKind map(String itemType) {
        if (itemType == null || itemType.equalsIgnoreCase("ALL")) return null;
        return InventoryItemKind.valueOf(itemType.toUpperCase());
    }

    @Transactional(readOnly = true)
    public SparePartDto findById(UUID id) {
        SparePart part = getOrThrow(id);
        List<WarehouseStock> stocks = stockRepository.findAllBySparePartIdAndIsDeletedFalse(id);
        double currentStock = stocks.stream().mapToDouble(WarehouseStock::getQuantity).sum();
        double reservedStock = stocks.stream().mapToDouble(WarehouseStock::getReservedQty).sum();
        return SparePartDto.from(part, currentStock, reservedStock, stocks.size());
    }

    @Transactional
    public SparePartDto create(SparePartRequest request) {
        if (repository.existsByCodeAndIsDeletedFalse(request.code())) {
            throw RestException.conflict("Spare part code already exists: " + request.code());
        }
        SparePart entity = new SparePart();
        apply(entity, request);
        return SparePartDto.from(repository.save(entity));
    }

    @Transactional
    public SparePartDto update(UUID id, SparePartRequest request) {
        SparePart entity = getOrThrow(id);
        apply(entity, request);
        return SparePartDto.from(entity);
    }

    @Transactional
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

    public String toSearchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return "%" + search.trim().toLowerCase() + "%";
    }
}
