package com.toir.service;

import com.toir.dto.warehouse.WarehouseEquipmentAssignRequest;
import com.toir.dto.warehouse.WarehouseEquipmentItemDto;
import com.toir.entity.Department;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseEquipmentItem;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseEquipmentItemRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
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
    DepartmentRepository departmentRepository;

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
        equipment.setDepartmentId(UUID.randomUUID());

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(warehouseEquipmentItemRepository.findActiveByEquipmentId(equipmentId)).thenReturn(Optional.empty());
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
        assertThat(equipment.getDepartmentId()).isNull();
        verify(equipmentRepository).save(equipment);
    }

    @Test
    void assignDefaultsStatusToAvailableWhenNull() {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(activeWarehouse(warehouseId)));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));
        when(warehouseEquipmentItemRepository.findActiveByEquipmentId(equipmentId)).thenReturn(Optional.empty());
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
        when(warehouseEquipmentItemRepository.findActiveByEquipmentId(equipmentId)).thenReturn(Optional.empty());
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
        when(warehouseEquipmentItemRepository.findActiveByEquipmentId(equipmentId)).thenReturn(Optional.empty());
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
        Equipment equipment = equipment(equipmentId);
        equipment.setDepartmentId(UUID.randomUUID());

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(activeWarehouse(warehouseId)));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(warehouseEquipmentItemRepository.findActiveByEquipmentId(equipmentId)).thenReturn(Optional.empty());
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseEquipmentItemDto result = service.assign(
                warehouseId,
                new WarehouseEquipmentAssignRequest(equipmentId, WarehouseEquipmentStatus.OUT_OF_SERVICE)
        );

        assertThat(result.status()).isEqualTo(WarehouseEquipmentStatus.OUT_OF_SERVICE);
        assertThat(equipment.getDepartmentId()).isNull();
        verify(equipmentRepository).save(equipment);
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
        UUID assignedWarehouseId = UUID.randomUUID();
        WarehouseEquipmentItem existingAssignment = new WarehouseEquipmentItem();
        existingAssignment.setWarehouseId(assignedWarehouseId);
        existingAssignment.setEquipmentId(equipmentId);
        existingAssignment.setStatus(WarehouseEquipmentStatus.AVAILABLE);
        existingAssignment.setActive(true);
        existingAssignment.setDeleted(false);
        when(warehouseEquipmentItemRepository.findActiveByEquipmentId(equipmentId)).thenReturn(Optional.of(existingAssignment));

        assertThatThrownBy(() -> service.assign(
                warehouseId,
                new WarehouseEquipmentAssignRequest(equipmentId, WarehouseEquipmentStatus.AVAILABLE)
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Equipment is already assigned to warehouse")
                .hasMessageContaining(assignedWarehouseId.toString());
    }

    @Test
    void assignFailsIfEquipmentAlreadyAssignedEvenWhenAssignedWarehouseIsInactive() {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID inactiveAssignedWarehouseId = UUID.randomUUID();
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(activeWarehouse(warehouseId)));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));

        WarehouseEquipmentItem existingAssignment = new WarehouseEquipmentItem();
        existingAssignment.setWarehouseId(inactiveAssignedWarehouseId);
        existingAssignment.setEquipmentId(equipmentId);
        existingAssignment.setStatus(WarehouseEquipmentStatus.OUT_OF_SERVICE);
        existingAssignment.setActive(true);
        existingAssignment.setDeleted(false);
        when(warehouseEquipmentItemRepository.findActiveByEquipmentId(equipmentId)).thenReturn(Optional.of(existingAssignment));

        assertThatThrownBy(() -> service.assign(
                warehouseId,
                new WarehouseEquipmentAssignRequest(equipmentId, WarehouseEquipmentStatus.AVAILABLE)
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Equipment is already assigned to warehouse")
                .hasMessageContaining(inactiveAssignedWarehouseId.toString());
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
    void updateStatusToInstalledRequiresDepartmentId() {
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

        assertThatThrownBy(() -> service.updateStatus(
                warehouseId,
                equipmentId,
                WarehouseEquipmentStatus.INSTALLED,
                null
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("departmentId is required when status is INSTALLED");
    }

    @Test
    void updateStatusToInstalledWithValidDepartmentSetsEquipmentDepartmentAndStatusInstalled() {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setWarehouseId(warehouseId);
        item.setEquipmentId(equipmentId);
        item.setStatus(WarehouseEquipmentStatus.RESERVED);
        item.setActive(true);
        item.setDeleted(false);

        Equipment equipment = equipment(equipmentId);
        equipment.setDepartmentId(null);

        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, equipmentId))
                .thenReturn(Optional.of(item));
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId))
                .thenReturn(Optional.of(department(departmentId)));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(equipment));
        when(equipmentRepository.save(any(Equipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseEquipmentItemDto result = service.updateStatus(
                warehouseId,
                equipmentId,
                WarehouseEquipmentStatus.INSTALLED,
                departmentId
        );

        assertThat(result.status()).isEqualTo(WarehouseEquipmentStatus.INSTALLED);
        assertThat(item.getStatus()).isEqualTo(WarehouseEquipmentStatus.INSTALLED);
        assertThat(equipment.getDepartmentId()).isEqualTo(departmentId);
        verify(equipmentRepository).save(equipment);
        verify(warehouseEquipmentItemRepository).save(item);
    }

    @Test
    void updateStatusToInstalledWithUnknownDepartment() {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setWarehouseId(warehouseId);
        item.setEquipmentId(equipmentId);
        item.setStatus(WarehouseEquipmentStatus.AVAILABLE);
        item.setActive(true);
        item.setDeleted(false);

        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, equipmentId))
                .thenReturn(Optional.of(item));
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(
                warehouseId,
                equipmentId,
                WarehouseEquipmentStatus.INSTALLED,
                departmentId
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Department not found: " + departmentId);
    }

    @Test
    void updateStatusToInstalledFromOutOfServiceRejected() {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setWarehouseId(warehouseId);
        item.setEquipmentId(equipmentId);
        item.setStatus(WarehouseEquipmentStatus.OUT_OF_SERVICE);
        item.setActive(true);
        item.setDeleted(false);

        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, equipmentId))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.updateStatus(
                warehouseId,
                equipmentId,
                WarehouseEquipmentStatus.INSTALLED,
                UUID.randomUUID()
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("OUT_OF_SERVICE equipment cannot be installed directly");
    }

    @Test
    void updateStatusNonInstalledWithDepartmentIdRejected() {
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

        assertThatThrownBy(() -> service.updateStatus(
                warehouseId,
                equipmentId,
                WarehouseEquipmentStatus.AVAILABLE,
                UUID.randomUUID()
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("departmentId must be null when status is not INSTALLED");
    }

    @Test
    void updateStatusAvailableWithoutDepartmentStillWorks() {
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
                WarehouseEquipmentStatus.AVAILABLE,
                null
        );

        assertThat(result.status()).isEqualTo(WarehouseEquipmentStatus.AVAILABLE);
        verify(warehouseEquipmentItemRepository).save(item);
    }

    @Test
    void updateStatusOutOfServiceWithoutDepartmentStillWorks() {
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
                WarehouseEquipmentStatus.OUT_OF_SERVICE,
                null
        );

        assertThat(result.status()).isEqualTo(WarehouseEquipmentStatus.OUT_OF_SERVICE);
        verify(warehouseEquipmentItemRepository).save(item);
    }

    @Test
    void updateStatusWarehouseItemNotFound() {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, equipmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(
                warehouseId,
                equipmentId,
                WarehouseEquipmentStatus.AVAILABLE,
                null
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Warehouse equipment item not found");
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
        Equipment equipment = equipment(equipmentId);
        equipment.setDepartmentId(UUID.randomUUID());

        WarehouseEquipmentItem existing = new WarehouseEquipmentItem();
        existing.setWarehouseId(sourceWarehouseId);
        existing.setEquipmentId(equipmentId);
        existing.setStatus(WarehouseEquipmentStatus.INSTALLED);
        existing.setActive(true);
        existing.setDeleted(false);

        when(warehouseRepository.findByIdAndIsDeletedFalse(targetWarehouseId))
                .thenReturn(Optional.of(activeWarehouse(targetWarehouseId)));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(equipment));
        when(warehouseEquipmentItemRepository.findActiveByEquipmentId(equipmentId))
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
        InOrder inOrder = inOrder(warehouseEquipmentItemRepository);
        inOrder.verify(warehouseEquipmentItemRepository).save(existing);
        inOrder.verify(warehouseEquipmentItemRepository).flush();
        inOrder.verify(warehouseEquipmentItemRepository).save(any(WarehouseEquipmentItem.class));

        assertThat(firstSave.getEquipmentId()).isEqualTo(equipmentId);
        assertThat(firstSave.isActive()).isFalse();
        assertThat(firstSave.isDeleted()).isTrue();

        assertThat(secondSave.getEquipmentId()).isEqualTo(equipmentId);
        assertThat(secondSave.getWarehouseId()).isEqualTo(targetWarehouseId);
        assertThat(secondSave.getStatus()).isEqualTo(WarehouseEquipmentStatus.OUT_OF_SERVICE);
        assertThat(secondSave.isActive()).isTrue();
        assertThat(secondSave.isDeleted()).isFalse();
        assertThat(result.status()).isEqualTo(WarehouseEquipmentStatus.OUT_OF_SERVICE);
        assertThat(equipment.getDepartmentId()).isNull();
        assertThat(equipment.getLocationId()).isEqualTo(targetWarehouseId);
        verify(equipmentRepository).save(equipment);
    }

    @Test
    void transferSameWarehouseUpdatesStatusWithoutCreatingNewAssignment() {
        UUID equipmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId);
        equipment.setDepartmentId(UUID.randomUUID());

        WarehouseEquipmentItem existing = new WarehouseEquipmentItem();
        existing.setWarehouseId(warehouseId);
        existing.setEquipmentId(equipmentId);
        existing.setStatus(WarehouseEquipmentStatus.AVAILABLE);
        existing.setActive(true);
        existing.setDeleted(false);

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId))
                .thenReturn(Optional.of(activeWarehouse(warehouseId)));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(equipment));
        when(warehouseEquipmentItemRepository.findActiveByEquipmentId(equipmentId))
                .thenReturn(Optional.of(existing));
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseEquipmentItemDto result = service.transferEquipmentToWarehouse(
                equipmentId,
                warehouseId,
                WarehouseEquipmentStatus.OUT_OF_SERVICE
        );

        assertThat(existing.getStatus()).isEqualTo(WarehouseEquipmentStatus.OUT_OF_SERVICE);
        assertThat(result.warehouseId()).isEqualTo(warehouseId);
        assertThat(result.status()).isEqualTo(WarehouseEquipmentStatus.OUT_OF_SERVICE);
        assertThat(equipment.getDepartmentId()).isNull();
        assertThat(equipment.getLocationId()).isEqualTo(warehouseId);
        verify(warehouseEquipmentItemRepository).save(existing);
        verify(warehouseEquipmentItemRepository, never()).flush();
        verify(equipmentRepository).save(equipment);
    }

    @Test
    void transferCreatesOutOfServiceAssignmentWhenNoPreviousActiveAssignmentExists() {
        UUID equipmentId = UUID.randomUUID();
        UUID targetWarehouseId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId);
        equipment.setDepartmentId(UUID.randomUUID());

        when(warehouseRepository.findByIdAndIsDeletedFalse(targetWarehouseId))
                .thenReturn(Optional.of(activeWarehouse(targetWarehouseId)));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(equipment));
        when(warehouseEquipmentItemRepository.findActiveByEquipmentId(equipmentId))
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
        assertThat(equipment.getDepartmentId()).isNull();
        assertThat(equipment.getLocationId()).isEqualTo(targetWarehouseId);
        verify(warehouseEquipmentItemRepository, never()).flush();
        verify(equipmentRepository).save(equipment);
    }

    private Warehouse activeWarehouse(UUID warehouseId) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setActive(true);
        return warehouse;
    }

    private Department department(UUID departmentId) {
        Department department = new Department();
        department.setId(departmentId);
        return department;
    }

    private Equipment equipment(UUID equipmentId) {
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        return equipment;
    }
}
