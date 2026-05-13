package com.toir.service;

import com.toir.dto.warehouse.WarehouseEquipmentAssignRequest;
import com.toir.dto.warehouse.WarehouseEquipmentItemDto;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseEquipmentItem;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseEquipmentItemRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseEquipmentItemService {
    private static final Logger log = LoggerFactory.getLogger(WarehouseEquipmentItemService.class);

    private final WarehouseRepository warehouseRepository;
    private final EquipmentRepository equipmentRepository;
    private final WarehouseEquipmentItemRepository warehouseEquipmentItemRepository;

    @Transactional
    public WarehouseEquipmentItemDto assign(UUID warehouseId, WarehouseEquipmentAssignRequest request) {
        Warehouse warehouse = getWarehouseOrThrow(warehouseId);
        if (!warehouse.isActive()) {
            throw RestException.badRequest("Warehouse is not active");
        }

        equipmentRepository.findByIdAndIsDeletedFalse(request.equipmentId())
                .orElseThrow(() -> RestException.notFound("Equipment not found"));

        warehouseEquipmentItemRepository.findActiveByEquipmentId(request.equipmentId())
                .ifPresent(existing -> {
                    log.warn(
                            "Warehouse equipment assignment conflict: itemId={}, equipmentId={}, warehouseId={}, status={}, active={}, isDeleted={}",
                            existing.getId(),
                            existing.getEquipmentId(),
                            existing.getWarehouseId(),
                            existing.getStatus(),
                            existing.isActive(),
                            existing.isDeleted()
                    );
                    throw RestException.conflict("Equipment is already assigned to warehouse " + existing.getWarehouseId());
                });

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
        getWarehouseOrThrow(warehouseId);
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
                                                  WarehouseEquipmentStatus status) {
        WarehouseEquipmentItem item = getWarehouseEquipmentItemOrThrow(warehouseId, equipmentId);
        item.setStatus(status);
        WarehouseEquipmentItem saved = warehouseEquipmentItemRepository.save(item);
        return WarehouseEquipmentItemDto.from(saved);
    }

    @Transactional
    public void remove(UUID warehouseId, UUID equipmentId) {
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
        if (!targetWarehouse.isActive()) {
            throw RestException.badRequest("Warehouse is not active");
        }

        equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found"));

        warehouseEquipmentItemRepository.findActiveByEquipmentId(equipmentId)
                .ifPresent(existing -> {
                    existing.setActive(false);
                    existing.setDeleted(true);
                    warehouseEquipmentItemRepository.save(existing);
                });

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
        return warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)
                .orElseThrow(() -> RestException.notFound("Warehouse not found"));
    }

    private WarehouseEquipmentItem getWarehouseEquipmentItemOrThrow(UUID warehouseId, UUID equipmentId) {
        return warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(
                        warehouseId,
                        equipmentId
                )
                .orElseThrow(() -> RestException.notFound("Warehouse equipment item not found"));
    }
}
