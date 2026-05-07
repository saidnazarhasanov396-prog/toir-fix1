package com.toir.service.equipment;

import com.toir.dto.equipment.EquipmentDto;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.warehouse.Warehouse;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkType;
import com.toir.exception.RestException;
import com.toir.repository.LocationRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentPassportRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceTest {

    @Mock
    EquipmentRepository repository;

    @Mock
    DepartmentRepository departmentRepository;

    @Mock
    LocationRepository locationRepository;

    @Mock
    EquipmentTypeRepository equipmentTypeRepository;

    @Mock
    EquipmentPassportRepository passportRepository;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    EquipmentService service;

    @Test
    void searchWithoutAvailableForReplacementKeepsExistingBehavior() {
        Equipment equipment = equipment("EQ-1");
        Page<Equipment> page = new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1);
        stubEnrichment();
        when(repository.search(any(), any(), any(), any(), any(), any())).thenReturn(page);

        Page<EquipmentDto> result = service.search(
                null, null, null, null, UUID.randomUUID(), false, "eq", 0, 20
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        verify(repository).search(any(), any(), any(), any(), any(), any());
        verify(repository, never()).searchAvailableForReplacement(any(), any(), any(), anyCollection(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void availableForReplacementWithoutWarehouseIdFails() {
        assertThatThrownBy(() -> service.search(
                null, null, null, null, null, true, null, 0, 20
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("warehouseId is required when availableForReplacement is true");
    }

    @Test
    void availableForReplacementReturnsOnlyAvailableFromSelectedWarehouse() {
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        Equipment equipment = equipment("EQ-2");
        Page<Equipment> page = new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1);

        stubEnrichment();
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(repository.searchAvailableForReplacement(
                eq(warehouseId),
                eq(WarehouseEquipmentStatus.AVAILABLE),
                eq(WorkType.REPLACEMENT),
                anyCollection(),
                any(), any(), any(), any(), any(), any()
        )).thenReturn(page);

        Page<EquipmentDto> result = service.search(
                null, null, null, null, warehouseId, true, null, 0, 20
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().code()).isEqualTo("EQ-2");
    }

    @Test
    void availableForReplacementExcludesNonAvailableWarehouseEquipmentItem() {
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        Equipment equipment = equipment("EQ-3");
        Page<Equipment> page = new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1);

        stubEnrichment();
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(repository.searchAvailableForReplacement(
                eq(warehouseId),
                eq(WarehouseEquipmentStatus.AVAILABLE),
                eq(WorkType.REPLACEMENT),
                anyCollection(),
                any(), any(), any(), any(), any(), any()
        )).thenReturn(page);

        Page<EquipmentDto> result = service.search(
                null, null, null, null, warehouseId, true, null, 0, 20
        );

        assertThat(result.getContent()).extracting(EquipmentDto::code).containsExactly("EQ-3");
    }

    @Test
    void availableForReplacementExcludesEquipmentFromAnotherWarehouse() {
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        Equipment equipment = equipment("EQ-4");
        Page<Equipment> page = new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1);

        stubEnrichment();
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(repository.searchAvailableForReplacement(
                eq(warehouseId),
                eq(WarehouseEquipmentStatus.AVAILABLE),
                eq(WorkType.REPLACEMENT),
                anyCollection(),
                any(), any(), any(), any(), any(), any()
        )).thenReturn(page);

        Page<EquipmentDto> result = service.search(
                null, null, null, null, warehouseId, true, null, 0, 20
        );

        assertThat(result.getContent()).extracting(EquipmentDto::code).containsExactly("EQ-4");
    }

    @Test
    void availableForReplacementExcludesAlreadyUsedByActiveReplacementWorkOrder() {
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        Page<Equipment> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        stubEnrichment();
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(repository.searchAvailableForReplacement(
                eq(warehouseId),
                eq(WarehouseEquipmentStatus.AVAILABLE),
                eq(WorkType.REPLACEMENT),
                anyCollection(),
                any(), any(), any(), any(), any(), any()
        )).thenReturn(page);

        Page<EquipmentDto> result = service.search(
                null, null, null, null, warehouseId, true, null, 0, 20
        );

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void availableForReplacementDoesNotExcludeUsedByFinalOrClosedReplacementWorkOrder() {
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        Equipment equipment = equipment("EQ-5");
        Page<Equipment> page = new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1);

        stubEnrichment();
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(repository.searchAvailableForReplacement(
                eq(warehouseId),
                eq(WarehouseEquipmentStatus.AVAILABLE),
                eq(WorkType.REPLACEMENT),
                anyCollection(),
                any(), any(), any(), any(), any(), any()
        )).thenReturn(page);

        Page<EquipmentDto> result = service.search(
                null, null, null, null, warehouseId, true, null, 0, 20
        );

        assertThat(result.getContent()).extracting(EquipmentDto::code).containsExactly("EQ-5");
    }

    @Test
    void availableForReplacementPaginationStillWorks() {
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        Equipment equipment = equipment("EQ-6");
        Page<Equipment> page = new PageImpl<>(List.of(equipment), PageRequest.of(1, 1), 3);

        stubEnrichment();
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(repository.searchAvailableForReplacement(
                eq(warehouseId),
                eq(WarehouseEquipmentStatus.AVAILABLE),
                eq(WorkType.REPLACEMENT),
                anyCollection(),
                any(), any(), any(), any(), any(), any()
        )).thenReturn(page);

        Page<EquipmentDto> result = service.search(
                null, null, null, null, warehouseId, true, null, 1, 1
        );

        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(1);
    }

    @Test
    void availableForReplacementUsesExpectedFinalStatuses() {
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        Page<Equipment> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        stubEnrichment();
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(repository.searchAvailableForReplacement(
                eq(warehouseId),
                eq(WarehouseEquipmentStatus.AVAILABLE),
                eq(WorkType.REPLACEMENT),
                anyCollection(),
                any(), any(), any(), any(), any(), any()
        )).thenReturn(page);

        service.search(null, null, null, null, warehouseId, true, null, 0, 20);

        ArgumentCaptor<Collection<WorkOrderStatus>> finalStatusesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(repository).searchAvailableForReplacement(
                eq(warehouseId),
                eq(WarehouseEquipmentStatus.AVAILABLE),
                eq(WorkType.REPLACEMENT),
                finalStatusesCaptor.capture(),
                any(), any(), any(), any(), any(), any()
        );
        assertThat(finalStatusesCaptor.getValue())
                .containsExactlyInAnyOrder(WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED, WorkOrderStatus.CANCELLED);
    }

    private Equipment equipment(String code) {
        Equipment equipment = new Equipment();
        equipment.setId(UUID.randomUUID());
        equipment.setCode(code);
        equipment.setName("Equipment " + code);
        equipment.setInventoryNumber("INV-" + code);
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(UUID.randomUUID());
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        return equipment;
    }

    private void stubEnrichment() {
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        when(locationRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        when(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        when(repository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        when(passportRepository.findAllByEquipmentIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
    }
}
