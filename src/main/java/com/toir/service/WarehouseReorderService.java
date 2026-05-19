package com.toir.service;

import com.toir.dto.warehouse.ReorderSuggestionDto;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WarehouseReorderService {

    private final WarehouseStockRepository stockRepository;
    private final WarehouseRepository warehouseRepository;

    @Transactional(readOnly = true)
    public Page<ReorderSuggestionDto> suggestions(UUID warehouseId, int page, int size) {
        List<WarehouseStock> stocks = warehouseId != null
                ? stockRepository.findAllByWarehouseIdAndIsDeletedFalse(warehouseId)
                : stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();

        Set<UUID> warehouseIds = stocks.stream()
                .map(WarehouseStock::getWarehouseId)
                .collect(Collectors.toSet());

        Map<UUID, String> warehouseNames = warehouseRepository.findAllByIdInAndIsDeletedFalse(warehouseIds).stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));

        List<ReorderSuggestionDto> suggestions = new ArrayList<>();
        for (WarehouseStock stock : stocks) {
            buildSuggestion(stock, warehouseNames).ifPresent(suggestions::add);
        }
        return PaginationUtils.page(suggestions, page, size);
    }

    private Optional<ReorderSuggestionDto> buildSuggestion(WarehouseStock stock, Map<UUID, String> warehouseNames) {
        double available = stock.getAvailable();
        Double reorderPoint = stock.getReorderPoint();
        double minQty = stock.getMinQty();
        double trigger = reorderPoint != null ? reorderPoint : minQty;
        if (trigger <= 0 || available > trigger) {
            return Optional.empty();
        }

        double shortfall;
        String urgency;
        if (available <= minQty) {
            shortfall = Math.max((stock.getReorderQty() != null ? stock.getReorderQty() : minQty * 2) - available, 0);
            urgency = "CRITICAL";
        } else {
            shortfall = trigger - available;
            urgency = "WARNING";
        }

        String warehouseName = warehouseNames.getOrDefault(stock.getWarehouseId(), "");

        return Optional.of(new ReorderSuggestionDto(
                stock.getId(),
                stock.getWarehouseId(),
                warehouseName,
                stock.getSparePartId(),
                stock.getQuantity(),
                available,
                minQty,
                reorderPoint,
                stock.getReorderQty(),
                shortfall,
                urgency
        ));
    }
}
