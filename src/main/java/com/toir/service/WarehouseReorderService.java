package com.toir.service;

import com.toir.dto.warehouse.ReorderSuggestionDto;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseReorderService {

    private final WarehouseStockRepository stockRepository;
    private final WarehouseRepository warehouseRepository;
    private final ScopeAccessService scopeAccessService;

    @Transactional(readOnly = true)
    public Page<ReorderSuggestionDto> suggestions(UUID warehouseId, int page, int size) {
        List<WarehouseStock> stocks = warehouseId != null
                ? stocksForWarehouse(warehouseId)
                : stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(stock -> canAccessWarehouseId(stock.getWarehouseId()))
                .toList();

        List<ReorderSuggestionDto> suggestions = new ArrayList<>();
        for (WarehouseStock stock : stocks) {
            buildSuggestion(stock).ifPresent(suggestions::add);
        }
        return PaginationUtils.page(suggestions, page, size);
    }

    private List<WarehouseStock> stocksForWarehouse(UUID warehouseId) {
        assertCanAccessWarehouseId(warehouseId);
        return stockRepository.findAllByWarehouseIdAndIsDeletedFalse(warehouseId);
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

    private void assertCanAccessWarehouseId(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)
                .orElseThrow(() -> RestException.notFound("Warehouse not found: " + warehouseId));
        if (!canAccessWarehouse(warehouse)) {
            throw new AccessDeniedException("Access denied by warehouse scope");
        }
    }

    private boolean canAccessWarehouseId(UUID warehouseId) {
        return warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)
                .map(this::canAccessWarehouse)
                .orElse(false);
    }

    private boolean canAccessWarehouse(Warehouse warehouse) {
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        return (warehouse.getDepartmentId() != null && scopeAccessService.canAccessDepartment(warehouse.getDepartmentId()))
                || (warehouse.getResponsibleId() != null && scopeAccessService.canAccessEmployee(warehouse.getResponsibleId()));
    }
}
