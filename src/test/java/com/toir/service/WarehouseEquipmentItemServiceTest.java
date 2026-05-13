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
import com.toir.repository.equipment.EquipmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseEquipmentItemServiceTest {

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    WarehouseEquipmentItemRepository warehouseEquipmentItemRepository;

    @InjectMocks
    WarehouseEquipmentItemService service;

    @Test
    void assignEquipmentToWarehouseSucceeds() {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Warehouse warehouse = activeWarehouse(warehouseId);
        Equipment equipment = equipment(equipmentId);

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(warehouseEquipmentItemRepository.existsByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId)).thenReturn(false);
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class)))
                .thenAnswer(invocation -> {
                    WarehouseEquipmentItem item = invocation.getArgument(0);
                    ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
                    return item;
                });

        WarehouseEquipmentItemDto result = service.assign(
                warehouseId,
                new WarehouseEquipmentAssignRequest(equipmentId, WarehouseEquipmentStatus.RESERVED)
        );

        ArgumentCaptor<WarehouseEquipmentItem> captor = ArgumentCaptor.forClass(WarehouseEquipmentItem.class);
        verify(warehouseEquipmentItemRepository).save(captor.capture());
        WarehouseEquipmentItem saved = captor.getValue();

        assertThat(saved.getWarehouseId()).isEqualTo(warehouseId);
        assertThat(saved.getEquipmentId()).isEqualTo(equipmentId);
        assertThat(saved.getStatus()).isEqualTo(WarehouseEquipmentStatus.RESERVED);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.isDeleted()).isFalse();
        assertThat(result.status()).isEqualTo(WarehouseEquipmentStatus.RESERVED);
    }

    @Test
    void assignDefaultsStatusToAvailableWhenNull() {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(activeWarehouse(warehouseId)));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));
        when(warehouseEquipmentItemRepository.existsByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId)).thenReturn(false);
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseEquipmentItemDto result = service.assign(
                warehouseId,
                new WarehouseEquipmentAssignRequest(equipmentId, null)
        );

        assertThat(result.status()).isEqualTo(WarehouseEquipmentStatus.AVAILABLE);
    }

    @Test
    void assignSucceedsWhenOnlyInactiveHistoricalRowExists() {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(activeWarehouse(warehouseId)));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));
        // Historical row is inactive, so active+non-deleted existence check must be false.
        when(warehouseEquipmentItemRepository.existsByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId)).thenReturn(false);
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseEquipmentItemDto result = service.assign(
                warehouseId,
                new WarehouseEquipmentAssignRequest(equipmentId, null)
        );

        assertThat(result.equipmentId()).isEqualTo(equipmentId);
        assertThat(result.status()).isEqualTo(WarehouseEquipmentStatus.AVAILABLE);
    }

    @Test
    void assignSucceedsWhenOnlyDeletedHistoricalRowExists() {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(activeWarehouse(warehouseId)));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));
        // Historical row is soft-deleted, so active+non-deleted existence check must be false.
        when(warehouseEquipmentItemRepository.existsByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId)).thenReturn(false);
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseEquipmentItemDto result = service.assign(
                warehouseId,
                new WarehouseEquipmentAssignRequest(equipmentId, null)
        );

        assertThat(result.equipmentId()).isEqualTo(equipmentId);
        assertThat(result.status()).isEqualTo(WarehouseEquipmentStatus.AVAILABLE);
    }

    @Test
    void assignExplicitOutOfServiceStatusIsRespected() {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(activeWarehouse(warehouseId)));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));
        when(warehouseEquipmentItemRepository.existsByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId)).thenReturn(false);
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseEquipmentItemDto result = service.assign(
                warehouseId,
                new WarehouseEquipmentAssignRequest(equipmentId, WarehouseEquipmentStatus.OUT_OF_SERVICE)
        );

        assertThat(result.status()).isEqualTo(WarehouseEquipmentStatus.OUT_OF_SERVICE);
    }

    @Test
    void assignFailsIfWarehouseNotFound() {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(
                warehouseId,
                new WarehouseEquipmentAssignRequest(equipmentId, WarehouseEquipmentStatus.AVAILABLE)
        ))
                .isInstanceOf(RestException.class)
                .hasMessage("Warehouse not found");
    }

    @Test
    void assignFailsIfEquipmentNotFound() {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(activeWarehouse(warehouseId)));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(
                warehouseId,
                new WarehouseEquipmentAssignRequest(equipmentId, WarehouseEquipmentStatus.AVAILABLE)
        ))
                .isInstanceOf(RestException.class)
                .hasMessage("Equipment not found");
    }

    @Test
    void assignFailsIfWarehouseIsNotActive() {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        Warehouse warehouse = activeWarehouse(warehouseId);
        warehouse.setActive(false);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> service.assign(
                warehouseId,
                new WarehouseEquipmentAssignRequest(equipmentId, WarehouseEquipmentStatus.AVAILABLE)
        ))
                .isInstanceOf(RestException.class)
                .hasMessage("Warehouse is not active");
    }

    @Test
    void assignFailsIfEquipmentAlreadyAssignedToAnotherWarehouse() {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(activeWarehouse(warehouseId)));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));
        when(warehouseEquipmentItemRepository.existsByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId)).thenReturn(true);

        assertThatThrownBy(() -> service.assign(
                warehouseId,
                new WarehouseEquipmentAssignRequest(equipmentId, WarehouseEquipmentStatus.AVAILABLE)
        ))
                .isInstanceOf(RestException.class)
                .hasMessage("Equipment is already assigned to another warehouse");
    }

    @Test
    void listWarehouseEquipmentReturnsOnlyActiveNonDeletedItems() {
        UUID warehouseId = UUID.randomUUID();
        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setWarehouseId(warehouseId);
        item.setEquipmentId(UUID.randomUUID());
        item.setStatus(WarehouseEquipmentStatus.AVAILABLE);
        item.setActive(true);
        item.setDeleted(false);

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(activeWarehouse(warehouseId)));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndActiveTrueAndIsDeletedFalse(eq(warehouseId), any()))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        Page<WarehouseEquipmentItemDto> result = service.list(warehouseId, null, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().equipmentId()).isEqualTo(item.getEquipmentId());
        verify(warehouseEquipmentItemRepository, never())
                .findByWarehouseIdAndStatusAndActiveTrueAndIsDeletedFalse(any(), any(), any());
    }

    @Test
    void statusUpdateSucceeds() {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setWarehouseId(warehouseId);
        item.setEquipmentId(equipmentId);
        item.setStatus(WarehouseEquipmentStatus.RESERVED);
        item.setActive(true);
        item.setDeleted(false);

        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, equipmentId))
                .thenReturn(Optional.of(item));
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseEquipmentItemDto result = service.updateStatus(
                warehouseId,
                equipmentId,
                WarehouseEquipmentStatus.AVAILABLE
        );

        assertThat(result.status()).isEqualTo(WarehouseEquipmentStatus.AVAILABLE);
        verify(warehouseEquipmentItemRepository).save(item);
    }

    @Test
    void removeUnassignSoftDeletesAllocation() {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setWarehouseId(warehouseId);
        item.setEquipmentId(equipmentId);
        item.setStatus(WarehouseEquipmentStatus.AVAILABLE);
        item.setActive(true);
        item.setDeleted(false);

        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, equipmentId))
                .thenReturn(Optional.of(item));
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.remove(warehouseId, equipmentId);

        assertThat(item.isActive()).isFalse();
        assertThat(item.isDeleted()).isTrue();
        verify(warehouseEquipmentItemRepository).save(item);
    }

    @Test
    void removedEquipmentNoLongerAppearsInAvailableReplacementSearch() {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setWarehouseId(warehouseId);
        item.setEquipmentId(equipmentId);
        item.setStatus(WarehouseEquipmentStatus.AVAILABLE);
        item.setActive(true);
        item.setDeleted(false);

        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, equipmentId))
                .thenReturn(Optional.of(item));
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.remove(warehouseId, equipmentId);

        assertThat(item.isActive()).isFalse();
        assertThat(item.isDeleted()).isTrue();
    }

    @Test
    void transferDeactivatesPreviousAssignmentAndCreatesTargetOutOfServiceAssignment() {
        UUID equipmentId = UUID.randomUUID();
        UUID sourceWarehouseId = UUID.randomUUID();
        UUID targetWarehouseId = UUID.randomUUID();

        WarehouseEquipmentItem existing = new WarehouseEquipmentItem();
        existing.setWarehouseId(sourceWarehouseId);
        existing.setEquipmentId(equipmentId);
        existing.setStatus(WarehouseEquipmentStatus.INSTALLED);
        existing.setActive(true);
        existing.setDeleted(false);

        when(warehouseRepository.findByIdAndIsDeletedFalse(targetWarehouseId))
                .thenReturn(Optional.of(activeWarehouse(targetWarehouseId)));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(equipment(equipmentId)));
        when(warehouseEquipmentItemRepository.findByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(existing));
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseEquipmentItemDto result = service.transferEquipmentToWarehouse(
                equipmentId,
                targetWarehouseId,
                WarehouseEquipmentStatus.OUT_OF_SERVICE
        );

        ArgumentCaptor<WarehouseEquipmentItem> captor = ArgumentCaptor.forClass(WarehouseEquipmentItem.class);
        verify(warehouseEquipmentItemRepository, times(2)).save(captor.capture());
        WarehouseEquipmentItem firstSave = captor.getAllValues().get(0);
        WarehouseEquipmentItem secondSave = captor.getAllValues().get(1);

        assertThat(firstSave.getEquipmentId()).isEqualTo(equipmentId);
        assertThat(firstSave.isActive()).isFalse();
        assertThat(firstSave.isDeleted()).isTrue();

        assertThat(secondSave.getEquipmentId()).isEqualTo(equipmentId);
        assertThat(secondSave.getWarehouseId()).isEqualTo(targetWarehouseId);
        assertThat(secondSave.getStatus()).isEqualTo(WarehouseEquipmentStatus.OUT_OF_SERVICE);
        assertThat(secondSave.isActive()).isTrue();
        assertThat(secondSave.isDeleted()).isFalse();
        assertThat(result.status()).isEqualTo(WarehouseEquipmentStatus.OUT_OF_SERVICE);
    }

    @Test
    void transferCreatesOutOfServiceAssignmentWhenNoPreviousActiveAssignmentExists() {
        UUID equipmentId = UUID.randomUUID();
        UUID targetWarehouseId = UUID.randomUUID();

        when(warehouseRepository.findByIdAndIsDeletedFalse(targetWarehouseId))
                .thenReturn(Optional.of(activeWarehouse(targetWarehouseId)));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(equipment(equipmentId)));
        when(warehouseEquipmentItemRepository.findByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.empty());
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseEquipmentItemDto result = service.transferEquipmentToWarehouse(
                equipmentId,
                targetWarehouseId,
                WarehouseEquipmentStatus.OUT_OF_SERVICE
        );

        ArgumentCaptor<WarehouseEquipmentItem> captor = ArgumentCaptor.forClass(WarehouseEquipmentItem.class);
        verify(warehouseEquipmentItemRepository).save(captor.capture());
        WarehouseEquipmentItem saved = captor.getValue();
        assertThat(saved.getEquipmentId()).isEqualTo(equipmentId);
        assertThat(saved.getWarehouseId()).isEqualTo(targetWarehouseId);
        assertThat(saved.getStatus()).isEqualTo(WarehouseEquipmentStatus.OUT_OF_SERVICE);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.isDeleted()).isFalse();
        assertThat(result.status()).isEqualTo(WarehouseEquipmentStatus.OUT_OF_SERVICE);
    }

    private Warehouse activeWarehouse(UUID warehouseId) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setActive(true);
        return warehouse;
    }

    private Equipment equipment(UUID equipmentId) {
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        return equipment;
    }
}
