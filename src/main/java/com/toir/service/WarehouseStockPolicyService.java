package com.toir.service;

import com.toir.dto.warehouse.WarehouseStockPolicyDto;
import com.toir.dto.warehouse.WarehouseStockPolicyRequest;
import com.toir.entity.SparePart;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStockPolicy;
import com.toir.exception.RestException;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockPolicyRepository;
import com.toir.security.ScopeAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseStockPolicyService {

    private final WarehouseStockPolicyRepository policyRepository;
    private final WarehouseRepository warehouseRepository;
    private final SparePartRepository sparePartRepository;
    private final ScopeAccessService scopeAccessService;

    @Transactional(readOnly = true)
    public List<WarehouseStockPolicyDto> findByWarehouse(UUID warehouseId) {
        Warehouse warehouse = warehouseOrThrow(warehouseId);
        assertCanAccessWarehouse(warehouse);
        return policyRepository.findAllByWarehouseIdAndIsDeletedFalse(warehouseId).stream()
                .map(policy -> toDto(policy, warehouse))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WarehouseStockPolicyDto> findBySparePart(UUID sparePartId) {
        sparePartOrThrow(sparePartId);
        return policyRepository.findAllBySparePartIdAndIsDeletedFalse(sparePartId).stream()
                .map(policy -> {
                    Warehouse warehouse = warehouseRepository.findByIdAndIsDeletedFalse(policy.getWarehouseId())
                            .orElse(null);
                    if (!canAccessWarehouse(warehouse)) {
                        return null;
                    }
                    return toDto(policy, warehouse);
                })
                .filter(dto -> dto != null)
                .toList();
    }

    @Transactional
    public WarehouseStockPolicyDto upsert(UUID warehouseId,
                                          UUID sparePartId,
                                          WarehouseStockPolicyRequest request) {
        Warehouse warehouse = warehouseOrThrow(warehouseId);
        assertCanAccessWarehouse(warehouse);
        SparePart sparePart = sparePartOrThrow(sparePartId);

        WarehouseStockPolicy policy = policyRepository
                .findByWarehouseIdAndSparePartId(warehouseId, sparePartId)
                .orElseGet(WarehouseStockPolicy::new);
        policy.setDeleted(false);
        policy.setWarehouseId(warehouseId);
        policy.setSparePartId(sparePartId);
        apply(policy, request, sparePart);
        WarehouseStockPolicy saved = policyRepository.save(policy);
        return toDto(saved, warehouse, sparePart);
    }

    @Transactional
    public List<WarehouseStockPolicyDto> replaceForSparePart(UUID sparePartId,
                                                             List<WarehouseStockPolicyRequest> requests) {
        if (requests == null) {
            return findBySparePart(sparePartId);
        }
        sparePartOrThrow(sparePartId);
        Set<UUID> requestedWarehouseIds = new HashSet<>();
        for (WarehouseStockPolicyRequest request : requests) {
            if (request.warehouseId() == null) {
                throw RestException.badRequest("warehouseId is required for spare part warehouse policy");
            }
            if (!requestedWarehouseIds.add(request.warehouseId())) {
                throw RestException.badRequest("Duplicate warehouse policy for warehouseId: " + request.warehouseId());
            }
        }

        for (WarehouseStockPolicy policy : policyRepository.findAllBySparePartIdAndIsDeletedFalse(sparePartId)) {
            if (!requestedWarehouseIds.contains(policy.getWarehouseId())) {
                policy.setDeleted(true);
                policyRepository.save(policy);
            }
        }

        return requests.stream()
                .map(request -> upsert(request.warehouseId(), sparePartId, request))
                .toList();
    }

    @Transactional
    public void delete(UUID warehouseId, UUID sparePartId) {
        Warehouse warehouse = warehouseOrThrow(warehouseId);
        assertCanAccessWarehouse(warehouse);
        WarehouseStockPolicy policy = policyRepository
                .findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId)
                .orElseThrow(() -> RestException.notFound("Warehouse stock policy not found"));
        policy.setDeleted(true);
        policyRepository.save(policy);
    }

    private void apply(WarehouseStockPolicy policy,
                       WarehouseStockPolicyRequest request,
                       SparePart sparePart) {
        double minQty = request.minQty() != null ? request.minQty() : sparePart.getMinStock();
        if (minQty < 0) {
            throw RestException.badRequest("minQty must be greater than or equal to 0");
        }
        policy.setMinQty(minQty);
        policy.setMaxQty(positiveOrNull(request.maxQty(), "maxQty"));
        policy.setReorderPoint(positiveOrNull(request.reorderPoint(), "reorderPoint"));
        policy.setReorderQty(positiveOrNull(request.reorderQty(), "reorderQty"));
        policy.setAvgDailyUsage(positiveOrNull(request.avgDailyUsage(), "avgDailyUsage"));
    }

    private Double positiveOrNull(Double value, String field) {
        if (value == null) {
            return null;
        }
        if (value < 0) {
            throw RestException.badRequest(field + " must be greater than or equal to 0");
        }
        return value > 0 ? value : null;
    }

    private Warehouse warehouseOrThrow(UUID warehouseId) {
        return warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)
                .orElseThrow(() -> RestException.notFound("Warehouse not found: " + warehouseId));
    }

    private SparePart sparePartOrThrow(UUID sparePartId) {
        return sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)
                .orElseThrow(() -> RestException.notFound("Spare part not found: " + sparePartId));
    }

    private WarehouseStockPolicyDto toDto(WarehouseStockPolicy policy, Warehouse warehouse) {
        SparePart sparePart = sparePartRepository.findByIdAndIsDeletedFalse(policy.getSparePartId()).orElse(null);
        return toDto(policy, warehouse, sparePart);
    }

    private WarehouseStockPolicyDto toDto(WarehouseStockPolicy policy, Warehouse warehouse, SparePart sparePart) {
        return WarehouseStockPolicyDto.from(
                policy,
                warehouse == null ? null : warehouse.getName(),
                sparePart == null ? null : sparePart.getName(),
                sparePart == null ? null : sparePart.getCode()
        );
    }

    private void assertCanAccessWarehouse(Warehouse warehouse) {
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
