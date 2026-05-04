package com.toir.service;

import com.toir.dto.warehouse.ReorderSuggestionDto;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.repository.WarehouseStockRepository;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseReorderService {

    private final WarehouseStockRepository stockRepository;

    @Transactional(readOnly = true)
    public Page<ReorderSuggestionDto> suggestions(UUID warehouseId, int page, int size) {
        List<WarehouseStock> stocks = warehouseId != null
                ? stockRepository.findAllByWarehouseIdAndIsDeletedFalse(warehouseId)
                : stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();

        List<ReorderSuggestionDto> suggestions = new ArrayList<>();
        for (WarehouseStock stock : stocks) {
            buildSuggestion(stock).ifPresent(suggestions::add);
        }
        return PaginationUtils.page(suggestions, page, size);
    }

    private java.util.Optional<ReorderSuggestionDto> buildSuggestion(WarehouseStock stock) {
        double available = stock.getAvailable();
        Double reorderPoint = stock.getReorderPoint();
        double minQty = stock.getMinQty();
        double trigger = reorderPoint != null ? reorderPoint : minQty;
        if (trigger <= 0 || available > trigger) {
            return java.util.Optional.empty();
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

        return java.util.Optional.of(new ReorderSuggestionDto(
                stock.getId(),
                stock.getWarehouseId(),
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
