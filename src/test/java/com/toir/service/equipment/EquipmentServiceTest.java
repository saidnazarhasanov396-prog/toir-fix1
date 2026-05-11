package com.toir.service.equipment;

import com.toir.dto.equipment.EquipmentDto;
import com.toir.dto.equipment.EquipmentRequest;
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

import java.time.Year;
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
    void searchWithNullSearchShouldPassNullPattern() {
        Equipment equipment = equipment("EQ-NO-SEARCH");
        Page<Equipment> page = new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1);
        stubEnrichment();
        when(repository.search(any(), any(), any(), any(), any(), any())).thenReturn(page);

        service.search(null, null, null, null, null, false, null, 0, 20);

        ArgumentCaptor<String> searchPatternCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).search(any(), any(), any(), any(), searchPatternCaptor.capture(), any());
        assertThat(searchPatternCaptor.getValue()).isNull();
    }

    @Test
    void searchWithBlankSearchShouldPassNullPattern() {
        Equipment equipment = equipment("EQ-BLANK-SEARCH");
        Page<Equipment> page = new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1);
        stubEnrichment();
        when(repository.search(any(), any(), any(), any(), any(), any())).thenReturn(page);

        service.search(null, null, null, null, null, false, "   ", 0, 20);

        ArgumentCaptor<String> searchPatternCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).search(any(), any(), any(), any(), searchPatternCaptor.capture(), any());
        assertThat(searchPatternCaptor.getValue()).isNull();
    }

    @Test
    void searchWithTextShouldPassNormalizedLikePattern() {
        Equipment equipment = equipment("EQ-TEXT-SEARCH");
        Page<Equipment> page = new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1);
        stubEnrichment();
        when(repository.search(any(), any(), any(), any(), any(), any())).thenReturn(page);

        service.search(null, null, null, null, null, false, "  PuMp-42  ", 0, 20);

        ArgumentCaptor<String> searchPatternCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).search(any(), any(), any(), any(), searchPatternCaptor.capture(), any());
        assertThat(searchPatternCaptor.getValue()).isEqualTo("%pump-42%");
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
    void availableForReplacementWithNullSearchShouldPassNullPattern() {
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        Equipment equipment = equipment("EQ-NULL-SEARCH-REPL");
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

        service.search(null, null, null, null, warehouseId, true, null, 0, 20);

        ArgumentCaptor<String> searchPatternCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).searchAvailableForReplacement(
                eq(warehouseId),
                eq(WarehouseEquipmentStatus.AVAILABLE),
                eq(WorkType.REPLACEMENT),
                anyCollection(),
                any(), any(), any(), any(),
                searchPatternCaptor.capture(),
                any()
        );
        assertThat(searchPatternCaptor.getValue()).isNull();
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

    @Test
    void createWithoutCodeGeneratesBackendCodeAndReturnsIt() {
        int year = Year.now().getValue();
        String expectedCode = "EQ-" + year + "-0020";

        EquipmentRequest request = createRequest(null, "INV-NEW-1");
        stubEnrichment();
        when(repository.existsByInventoryNumberAndIsDeletedFalse("INV-NEW-1")).thenReturn(false);
        when(repository.maxSequenceByCodePrefix("EQ-" + year + "-")).thenReturn(19L);
        when(repository.existsByCodeAndIsDeletedFalse(expectedCode)).thenReturn(false);
        when(repository.save(any(Equipment.class))).thenAnswer(invocation -> {
            Equipment entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        EquipmentDto created = service.create(request);

        assertThat(created.code()).isEqualTo(expectedCode);
        ArgumentCaptor<Equipment> entityCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(repository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getCode()).isEqualTo(expectedCode);
    }

    @Test
    void createWithClientProvidedCodeShouldFailBadRequest() {
        EquipmentRequest request = createRequest("EQ-2026-0017", "INV-NEW-2");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Equipment code is generated by system and must not be provided");

        verify(repository, never()).save(any());
    }

    @Test
    void updateWithClientProvidedCodeShouldFailBadRequest() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipment("EQ-2026-0001")));
        EquipmentRequest request = updateRequestWithCode("EQ-2026-0002");

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Equipment code is generated by system and must not be provided");

        verify(repository, never()).save(any());
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

    private EquipmentRequest createRequest(String code, String inventoryNumber) {
        return new EquipmentRequest(
                code,
                "Compressor",
                inventoryNumber,
                "TN-1",
                "SN-1",
                "Model X",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                "ACME",
                EquipmentStatus.ACTIVE,
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                null,
                null,
                "test"
        );
    }

    private EquipmentRequest updateRequestWithCode(String code) {
        return new EquipmentRequest(
                code,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private void stubEnrichment() {
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        when(locationRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        when(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        when(repository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        when(passportRepository.findAllByEquipmentIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
    }
}
