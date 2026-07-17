package com.toir.service;

import com.toir.dto.warehouse.IssuedToWorkRowDto;
import com.toir.dto.warehouse.SparePartsWarehouseStatsResponse;
import com.toir.entity.UnitOfMeasurement;
import com.toir.entity.warehouse.Warehouse;
import com.toir.exception.RestException;
import com.toir.repository.SparePartsWarehouseStatsProjection;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.UnitOfMeasurementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.security.ScopeAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.toir.util.PaginationUtils.pageRequest;

@Service
@RequiredArgsConstructor
public class WarehouseSparePartsStatsService {

    private final WarehouseStockRepository stockRepository;
    private final WarehouseRepository warehouseRepository;
    private final ScopeAccessService scopeAccessService;
    private final UnitOfMeasurementRepository unitOfMeasurementRepository;
    private final StockMovementRepository movementRepository;

    @Transactional(readOnly = true)
    public SparePartsWarehouseStatsResponse getStats(
            UUID warehouseId,
            String search,
            UUID typeId,
            String itemType,
            String unit
    ) {
        ResolvedFilters filters = resolveFilters(search, itemType, unit);
        WarehouseScope warehouseScope = resolveWarehouseScope(warehouseId);

        if (warehouseScope.global()) {
            return toResponse(stockRepository.getSparePartsWarehouseStats(
                    filters.search(), typeId, filters.itemType(), filters.unitId()));
        }

        if (warehouseScope.warehouseIds().isEmpty()) {
            return new SparePartsWarehouseStatsResponse(0, 0, 0, 0);
        }

        return toResponse(stockRepository.getSparePartsWarehouseStatsByWarehouseIds(
                warehouseScope.warehouseIds(), filters.search(), typeId, filters.itemType(), filters.unitId()));
    }

    @Transactional(readOnly = true)
    public Page<IssuedToWorkRowDto> getIssuedToWork(
            UUID warehouseId,
            String search,
            UUID typeId,
            String itemType,
            String unit,
            int page,
            int size
    ) {
        ResolvedFilters filters = resolveFilters(search, itemType, unit);
        WarehouseScope warehouseScope = resolveWarehouseScope(warehouseId);
        Pageable pageable = pageRequest(page, size);

        if (warehouseScope.global()) {
            return movementRepository.findIssuedToWork(
                    filters.search(), typeId, filters.itemType(), filters.unitId(), pageable)
                    .map(IssuedToWorkRowDto::from);
        }

        if (warehouseScope.warehouseIds().isEmpty()) {
            return Page.empty(pageable);
        }

        return movementRepository.findIssuedToWorkByWarehouseIds(
                warehouseScope.warehouseIds(),
                filters.search(),
                typeId,
                filters.itemType(),
                filters.unitId(),
                pageable
        ).map(IssuedToWorkRowDto::from);
    }

    private ResolvedFilters resolveFilters(String search, String itemType, String unit) {
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        String normalizedItemType = (itemType == null || itemType.isBlank()) ? null : itemType.trim();
        return new ResolvedFilters(normalizedSearch, normalizedItemType, resolveUnitFilter(unit));
    }

    private WarehouseScope resolveWarehouseScope(UUID warehouseId) {
        if (warehouseId != null) {
            assertCanAccessWarehouseId(warehouseId);
            return new WarehouseScope(false, List.of(warehouseId));
        }

        if (scopeAccessService.isScopeAdmin()) {
            return new WarehouseScope(true, List.of());
        }

        List<UUID> accessibleWarehouseIds = warehouseRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(this::canAccessWarehouse)
                .map(Warehouse::getId)
                .toList();
        return new WarehouseScope(false, accessibleWarehouseIds);
    }

    private record ResolvedFilters(String search, String itemType, UUID unitId) {
    }

    private record WarehouseScope(boolean global, List<UUID> warehouseIds) {
    }

    /*
     * UOM resolution intentionally remains shared between the aggregate and detail
     * paths so legacy codes/names and UUID tokens cannot drift.
     */
    private UUID resolveUnitFilter(String unit) {
        if (unit == null || unit.isBlank()) {
            return null;
        }
        String token = unit.trim();
        Optional<UUID> parsedId = parseUuid(token);
        if (parsedId.isPresent()) {
            return unitOfMeasurementRepository.existsByIdAndIsDeletedFalse(parsedId.get())
                    ? parsedId.get()
                    : null;
        }
        return unitOfMeasurementRepository.findByTokenIgnoreCase(token).stream()
                .findFirst()
                .map(UnitOfMeasurement::getId)
                .orElse(null);
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
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
