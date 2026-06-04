package com.toir.service;

import com.toir.dto.warehouse.WarehouseEquipmentAssignRequest;
import com.toir.dto.warehouse.WarehouseEquipmentItemDto;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseEquipmentItem;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseEquipmentItemRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseEquipmentItemService {
    private static final Logger log = LoggerFactory.getLogger(WarehouseEquipmentItemService.class);

    private final WarehouseRepository warehouseRepository;
    private final EquipmentRepository equipmentRepository;
    private final DepartmentRepository departmentRepository;
    private final WarehouseEquipmentItemRepository warehouseEquipmentItemRepository;
    private final ScopeAccessService scopeAccessService;

    @Transactional
    public WarehouseEquipmentItemDto assign(UUID warehouseId, WarehouseEquipmentAssignRequest request) {
        Warehouse warehouse = getWarehouseOrThrow(warehouseId);
        assertCanAccessWarehouse(warehouse);
        if (!warehouse.isActive()) {
            throw RestException.badRequest("Warehouse is not active");
        }

        Equipment equipment = getEquipmentOrThrow(request.equipmentId());
        assertCanAccessEquipment(equipment);

        WarehouseEquipmentItem existingAssignment = findActiveWarehouseItemOrNull(request.equipmentId());
        if (existingAssignment != null) {
            log.warn(
                    "Warehouse equipment assignment conflict: itemId={}, equipmentId={}, warehouseId={}, status={}, active={}, isDeleted={}",
                    existingAssignment.getId(),
                    existingAssignment.getEquipmentId(),
                    existingAssignment.getWarehouseId(),
                    existingAssignment.getStatus(),
                    existingAssignment.isActive(),
                    existingAssignment.isDeleted()
            );
            throw RestException.conflict("Equipment is already assigned to warehouse " + existingAssignment.getWarehouseId());
        }

        equipment.setDepartmentId(null);
        equipmentRepository.save(equipment);

        WarehouseEquipmentItem entity = new WarehouseEquipmentItem();
        entity.setWarehouseId(warehouseId);
        entity.setEquipmentId(request.equipmentId());
        entity.setStatus(request.status() != null ? request.status() : WarehouseEquipmentStatus.AVAILABLE);
        entity.setActive(true);
        entity.setDeleted(false);

        WarehouseEquipmentItem saved = warehouseEquipmentItemRepository.save(entity);
        return WarehouseEquipmentItemDto.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<WarehouseEquipmentItemDto> list(UUID warehouseId,
                                                WarehouseEquipmentStatus status,
                                                int page,
                                                int size) {
        assertCanAccessWarehouse(getWarehouseOrThrow(warehouseId));
        Page<WarehouseEquipmentItem> items = status == null
                ? warehouseEquipmentItemRepository.findByWarehouseIdAndActiveTrueAndIsDeletedFalse(
                        warehouseId,
                        PaginationUtils.pageRequest(page, size)
                )
                : warehouseEquipmentItemRepository.findByWarehouseIdAndStatusAndActiveTrueAndIsDeletedFalse(
                        warehouseId,
                        status,
                        PaginationUtils.pageRequest(page, size)
                );
        return items.map(WarehouseEquipmentItemDto::from);
    }

    @Transactional
    public WarehouseEquipmentItemDto updateStatus(UUID warehouseId,
                                                  UUID equipmentId,
                                                  WarehouseEquipmentStatus status,
                                                  UUID departmentId) {
        assertCanAccessWarehouse(getWarehouseOrThrow(warehouseId));
        WarehouseEquipmentItem item = getWarehouseEquipmentItemOrThrow(warehouseId, equipmentId);
        if (status == WarehouseEquipmentStatus.INSTALLED) {
            if (departmentId == null) {
                throw RestException.badRequest("departmentId is required when status is INSTALLED");
            }
            if (!scopeAccessService.isScopeAdmin() && !scopeAccessService.canAccessDepartment(departmentId)) {
                throw new AccessDeniedException("Access denied by target department scope");
            }
            if (item.getStatus() == WarehouseEquipmentStatus.OUT_OF_SERVICE) {
                throw RestException.badRequest("OUT_OF_SERVICE equipment cannot be installed directly");
            }
            departmentRepository.findByIdAndIsDeletedFalse(departmentId)
                    .orElseThrow(() -> RestException.notFound("Department not found: " + departmentId));
            Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                    .orElseThrow(() -> RestException.notFound("Equipment not found"));
            equipment.setDepartmentId(departmentId);
            equipmentRepository.save(equipment);
        } else if (departmentId != null) {
            throw RestException.badRequest("departmentId must be null when status is not INSTALLED");
        }
        item.setStatus(status);
        WarehouseEquipmentItem saved = warehouseEquipmentItemRepository.save(item);
        return WarehouseEquipmentItemDto.from(saved);
    }

    @Transactional
    public void remove(UUID warehouseId, UUID equipmentId) {
        assertCanAccessWarehouse(getWarehouseOrThrow(warehouseId));
        WarehouseEquipmentItem item = getWarehouseEquipmentItemOrThrow(warehouseId, equipmentId);
        item.setActive(false);
        item.setDeleted(true);
        warehouseEquipmentItemRepository.save(item);
    }

    @Transactional
    public WarehouseEquipmentItemDto transferEquipmentToWarehouse(UUID equipmentId,
                                                                  UUID targetWarehouseId,
                                                                  WarehouseEquipmentStatus targetStatus) {
        Warehouse targetWarehouse = getWarehouseOrThrow(targetWarehouseId);
        assertCanAccessWarehouse(targetWarehouse);
        if (!targetWarehouse.isActive()) {
            throw RestException.badRequest("Warehouse is not active");
        }

        Equipment equipment = getEquipmentOrThrow(equipmentId);
        assertCanAccessEquipment(equipment);

        WarehouseEquipmentItem existing = findActiveWarehouseItemOrNull(equipmentId);
        if (existing != null) {
            assertCanAccessWarehouse(getWarehouseOrThrow(existing.getWarehouseId()));
        }

        equipment.setDepartmentId(null);
        equipmentRepository.save(equipment);

        if (existing != null) {
            if (Objects.equals(existing.getWarehouseId(), targetWarehouseId)) {
                existing.setStatus(targetStatus);
                WarehouseEquipmentItem saved = warehouseEquipmentItemRepository.save(existing);
                return WarehouseEquipmentItemDto.from(saved);
            }

            existing.setActive(false);
            existing.setDeleted(true);
            warehouseEquipmentItemRepository.save(existing);
            // Force UPDATE before INSERT to satisfy uq_warehouse_equipment_items_active_equipment.
            warehouseEquipmentItemRepository.flush();
        }

        WarehouseEquipmentItem entity = new WarehouseEquipmentItem();
        entity.setWarehouseId(targetWarehouseId);
        entity.setEquipmentId(equipmentId);
        entity.setStatus(targetStatus);
        entity.setActive(true);
        entity.setDeleted(false);

        WarehouseEquipmentItem saved = warehouseEquipmentItemRepository.save(entity);
        return WarehouseEquipmentItemDto.from(saved);
    }

    private Warehouse getWarehouseOrThrow(UUID warehouseId) {
        Optional<Warehouse> warehouse = warehouseRepository.findByIdAndIsDeletedFalse(warehouseId);
        if (warehouse == null) {
            throw RestException.notFound("Warehouse not found");
        }
        return warehouse
                .orElseThrow(() -> RestException.notFound("Warehouse not found"));
    }

    private WarehouseEquipmentItem getWarehouseEquipmentItemOrThrow(UUID warehouseId, UUID equipmentId) {
        Optional<WarehouseEquipmentItem> item = warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(
                        warehouseId,
                        equipmentId
                );
        if (item == null) {
            throw RestException.notFound("Warehouse equipment item not found");
        }
        return item
                .orElseThrow(() -> RestException.notFound("Warehouse equipment item not found"));
    }

    private Equipment getEquipmentOrThrow(UUID equipmentId) {
        Optional<Equipment> equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId);
        if (equipment == null) {
            throw RestException.notFound("Equipment not found");
        }
        return equipment.orElseThrow(() -> RestException.notFound("Equipment not found"));
    }

    private WarehouseEquipmentItem findActiveWarehouseItemOrNull(UUID equipmentId) {
        Optional<WarehouseEquipmentItem> item = warehouseEquipmentItemRepository.findActiveByEquipmentId(equipmentId);
        if (item == null || item.isEmpty()) {
            return null;
        }
        return item.get();
    }

    private void assertCanAccessEquipment(Equipment equipment) {
        scopeAccessService.assertCanAccessEquipmentScope(
                equipment.getResponsibleDepartmentId(),
                equipment.getDepartmentId()
        );
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (!scopeAccessService.canAccessEquipmentScope(
                equipment.getResponsibleDepartmentId(),
                equipment.getDepartmentId()
        )) {
            throw new AccessDeniedException("Access denied by data scope");
        }
    }

    private void assertCanAccessWarehouse(Warehouse warehouse) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        boolean departmentAllowed = warehouse.getDepartmentId() != null
                && scopeAccessService.canAccessDepartment(warehouse.getDepartmentId());
        boolean responsibleAllowed = warehouse.getResponsibleId() != null
                && scopeAccessService.canAccessEmployee(warehouse.getResponsibleId());
        if (!departmentAllowed && !responsibleAllowed) {
            throw new AccessDeniedException("Access denied by warehouse scope");
        }
    }
}
