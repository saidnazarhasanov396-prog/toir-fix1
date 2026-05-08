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
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseEquipmentItemService {

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

        if (warehouseEquipmentItemRepository.existsByEquipmentIdAndActiveTrueAndIsDeletedFalse(request.equipmentId())) {
            throw RestException.conflict("Equipment is already assigned to another warehouse");
        }

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
