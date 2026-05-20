package com.toir.service;

import com.toir.dto.warehouse.SparePartsWarehouseStatsResponse;
import com.toir.entity.warehouse.Warehouse;
import com.toir.exception.RestException;
import com.toir.repository.SparePartsWarehouseStatsProjection;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.security.ScopeAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseSparePartsStatsService {

    private final WarehouseStockRepository stockRepository;
    private final WarehouseRepository warehouseRepository;
    private final ScopeAccessService scopeAccessService;

    @Transactional(readOnly = true)
    public SparePartsWarehouseStatsResponse getStats(UUID warehouseId) {
        if (warehouseId != null) {
            assertCanAccessWarehouseId(warehouseId);
            return toResponse(stockRepository.getSparePartsWarehouseStatsByWarehouseIds(List.of(warehouseId)));
        }

        if (scopeAccessService.isScopeAdmin()) {
            return toResponse(stockRepository.getSparePartsWarehouseStats());
        }

        List<UUID> accessibleWarehouseIds = warehouseRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(this::canAccessWarehouse)
                .map(Warehouse::getId)
                .toList();
        if (accessibleWarehouseIds.isEmpty()) {
            return new SparePartsWarehouseStatsResponse(0, 0, 0, 0);
        }

        return toResponse(stockRepository.getSparePartsWarehouseStatsByWarehouseIds(accessibleWarehouseIds));
    }

    private SparePartsWarehouseStatsResponse toResponse(SparePartsWarehouseStatsProjection projection) {
        if (projection == null) {
            return new SparePartsWarehouseStatsResponse(0, 0, 0, 0);
        }
        return new SparePartsWarehouseStatsResponse(
                projection.getNomenclature() == null ? 0 : projection.getNomenclature(),
                projection.getActiveReservations() == null ? 0 : projection.getActiveReservations(),
                projection.getLowStockItems() == null ? 0 : projection.getLowStockItems(),
                projection.getIssuedToWork() == null ? 0 : projection.getIssuedToWork()
        );
    }

    private void assertCanAccessWarehouseId(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)
                .orElseThrow(() -> RestException.notFound("Warehouse not found: " + warehouseId));
        if (!canAccessWarehouse(warehouse)) {
            throw new AccessDeniedException("Access denied by warehouse scope");
        }
    }

    private boolean canAccessWarehouse(Warehouse warehouse) {
        if (warehouse == null) {
            return false;
        }
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        return (warehouse.getDepartmentId() != null && scopeAccessService.canAccessDepartment(warehouse.getDepartmentId()))
                || (warehouse.getResponsibleId() != null && scopeAccessService.canAccessEmployee(warehouse.getResponsibleId()));
    }
}
