package com.toir.service.equipment;

import com.toir.dto.equipment.EquipmentDto;
import com.toir.dto.equipment.EquipmentCreateRequest;
import com.toir.dto.equipment.EquipmentPlacementRequest;
import com.toir.dto.equipment.EquipmentUpdateRequest;
import com.toir.dto.warehouse.WarehouseEquipmentAssignRequest;
import com.toir.dto.warehouse.WarehouseEquipmentItemDto;
import com.toir.entity.Department;
import com.toir.entity.Location;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseEquipmentItem;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.PlacementTargetType;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkType;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseEquipmentItemRepository;
import com.toir.repository.LocationRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentPassportRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.service.WarehouseEquipmentItemService;
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
import java.time.Instant;
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
import static org.mockito.Mockito.verifyNoInteractions;
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
    WarehouseEquipmentItemService warehouseEquipmentItemService;

    @Mock
    WarehouseEquipmentItemRepository warehouseEquipmentItemRepository;

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
    void enrichPopulatesLocationRef() {
        UUID locationId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-LOC-1");
        equipment.setLocationId(locationId);

        Location location = new Location();
        location.setId(locationId);
        location.setCode("LOC-001");
        location.setName("Main Workshop");

        stubEnrichment();
        when(repository.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1));
        when(locationRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of(location));

        Page<EquipmentDto> result = service.search(null, null, null, null, null, false, null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        EquipmentDto dto = result.getContent().getFirst();
        assertThat(dto.locationId()).isEqualTo(locationId);
        assertThat(dto.location()).isNotNull();
        assertThat(dto.location().id()).isEqualTo(locationId);
        assertThat(dto.location().code()).isEqualTo("LOC-001");
        assertThat(dto.location().name()).isEqualTo("Main Workshop");
    }

    @Test
    void enrichAvoidsFailureWhenLocationMissing() {
        UUID locationId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-LOC-2");
        equipment.setLocationId(locationId);

        stubEnrichment();
        when(repository.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1));
        when(locationRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        stubWarehouseLocationFallback(List.of());

        Page<EquipmentDto> result = service.search(null, null, null, null, null, false, null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().locationId()).isEqualTo(locationId);
        assertThat(result.getContent().getFirst().location()).isNull();
    }

    @Test
    void enrichFallsBackToWarehouseWhenLocationIdMatchesWarehouse() {
        UUID legacyWarehouseId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-LOC-3");
        equipment.setLocationId(legacyWarehouseId);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(legacyWarehouseId);
        warehouse.setCode("WH-001");
        warehouse.setName("Spare Parts Warehouse");

        stubEnrichment();
        when(repository.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1));
        when(locationRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        stubWarehouseLocationFallback(List.of(warehouse));

        Page<EquipmentDto> result = service.search(null, null, null, null, null, false, null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        EquipmentDto dto = result.getContent().getFirst();
        assertThat(dto.locationId()).isEqualTo(legacyWarehouseId);
        assertThat(dto.location()).isNotNull();
        assertThat(dto.location().id()).isEqualTo(legacyWarehouseId);
        assertThat(dto.location().code()).isEqualTo("WH-001");
        assertThat(dto.location().name()).isEqualTo("Spare Parts Warehouse");
    }

    @Test
    void findByIdPopulatesLocationRef() {
        UUID equipmentId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        Equipment equipment = equipment("EQ-LOC-4");
        equipment.setId(equipmentId);
        equipment.setLocationId(locationId);

        Location location = new Location();
        location.setId(locationId);
        location.setCode("LOC-010");
        location.setName("Compressor Zone");

        stubEnrichment();
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(locationRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of(location));

        EquipmentDto dto = service.findById(equipmentId);

        assertThat(dto.id()).isEqualTo(equipmentId);
        assertThat(dto.locationId()).isEqualTo(locationId);
        assertThat(dto.location()).isNotNull();
        assertThat(dto.location().id()).isEqualTo(locationId);
        assertThat(dto.location().code()).isEqualTo("LOC-010");
        assertThat(dto.location().name()).isEqualTo("Compressor Zone");
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
        UUID locationId = UUID.randomUUID();
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        Equipment equipment = equipment("EQ-2");
        equipment.setLocationId(locationId);
        Location location = new Location();
        location.setId(locationId);
        location.setCode("LOC-200");
        location.setName("Replacement Yard");
        Page<Equipment> page = new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1);

        stubEnrichment();
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(locationRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of(location));
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
        assertThat(result.getContent().getFirst().location()).isNotNull();
        assertThat(result.getContent().getFirst().location().id()).isEqualTo(locationId);
        assertThat(result.getContent().getFirst().location().name()).isEqualTo("Replacement Yard");
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
    void createWithDepartmentIdOnlyCreatesEquipmentWithoutWarehouseAssignment() {
        UUID departmentId = UUID.randomUUID();
        EquipmentCreateRequest request = createRequest(null, "INV-NEW-1", departmentId, null);
        String expectedCode = stubCreateFlow("INV-NEW-1");
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId))
                .thenReturn(Optional.of(department(departmentId)));

        EquipmentDto created = service.create(request);

        assertThat(created.code()).isEqualTo(expectedCode);
        ArgumentCaptor<Equipment> entityCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(repository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getDepartmentId()).isEqualTo(departmentId);
        verifyNoInteractions(warehouseEquipmentItemService);
    }

    @Test
    void createWithWarehouseIdOnlyCreatesEquipmentAndAssignsAvailableWarehouseItem() {
        UUID warehouseId = UUID.randomUUID();
        EquipmentCreateRequest request = createRequest(null, "INV-NEW-2", null, warehouseId);
        String expectedCode = stubCreateFlow("INV-NEW-2");
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(warehouseEquipmentItemService.assign(eq(warehouseId), any(WarehouseEquipmentAssignRequest.class)))
                .thenReturn(new WarehouseEquipmentItemDto(
                        UUID.randomUUID(),
                        warehouseId,
                        UUID.randomUUID(),
                        WarehouseEquipmentStatus.AVAILABLE,
                        true,
                        Instant.now()
                ));

        EquipmentDto created = service.create(request);

        assertThat(created.code()).isEqualTo(expectedCode);
        ArgumentCaptor<Equipment> entityCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(repository).save(entityCaptor.capture());
        Equipment saved = entityCaptor.getValue();
        assertThat(saved.getDepartmentId()).isNull();

        ArgumentCaptor<WarehouseEquipmentAssignRequest> assignCaptor =
                ArgumentCaptor.forClass(WarehouseEquipmentAssignRequest.class);
        verify(warehouseEquipmentItemService).assign(eq(warehouseId), assignCaptor.capture());
        assertThat(assignCaptor.getValue().equipmentId()).isEqualTo(saved.getId());
        assertThat(assignCaptor.getValue().status()).isNull();
    }

    @Test
    void createWithBothCreatesEquipmentAndWarehouseAssignment() {
        UUID departmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        EquipmentCreateRequest request = createRequest(null, "INV-NEW-3", departmentId, warehouseId);
        stubCreateFlow("INV-NEW-3");
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId))
                .thenReturn(Optional.of(department(departmentId)));
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(warehouseEquipmentItemService.assign(eq(warehouseId), any(WarehouseEquipmentAssignRequest.class)))
                .thenReturn(new WarehouseEquipmentItemDto(
                        UUID.randomUUID(),
                        warehouseId,
                        UUID.randomUUID(),
                        WarehouseEquipmentStatus.AVAILABLE,
                        true,
                        Instant.now()
                ));

        service.create(request);

        ArgumentCaptor<Equipment> entityCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(repository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getDepartmentId()).isEqualTo(departmentId);
        verify(warehouseEquipmentItemService).assign(eq(warehouseId), any(WarehouseEquipmentAssignRequest.class));
    }

    @Test
    void createWithNeitherThrowsBadRequest() {
        EquipmentCreateRequest request = createRequest(null, "INV-NEW-4", null, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("departmentId or warehouseId is required");

        verify(repository, never()).save(any());
    }

    @Test
    void createWithInvalidDepartmentThrowsNotFound() {
        UUID departmentId = UUID.randomUUID();
        EquipmentCreateRequest request = createRequest(null, "INV-NEW-5", departmentId, null);
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Department not found: " + departmentId);

        verify(repository, never()).save(any());
    }

    @Test
    void createWithInvalidWarehouseThrowsNotFound() {
        UUID warehouseId = UUID.randomUUID();
        EquipmentCreateRequest request = createRequest(null, "INV-NEW-6", null, warehouseId);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Warehouse not found: " + warehouseId);

        verify(repository, never()).save(any());
    }

    @Test
    void createWithClientProvidedCodeShouldFailBadRequest() {
        EquipmentCreateRequest request = createRequest("EQ-2026-0017", "INV-NEW-7", UUID.randomUUID(), null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Equipment code is generated by system and must not be provided");

        verify(repository, never()).save(any());
    }

    @Test
    void assignmentFailureShouldPropagateAndSkipAuditLogging() {
        UUID warehouseId = UUID.randomUUID();
        EquipmentCreateRequest request = createRequest(null, "INV-NEW-8", null, warehouseId);
        stubCreateFlowWithoutEnrichment("INV-NEW-8");
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(warehouseEquipmentItemService.assign(eq(warehouseId), any(WarehouseEquipmentAssignRequest.class)))
                .thenThrow(RestException.conflict("Equipment is already assigned to another warehouse"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("already assigned to another warehouse");

        verify(repository).save(any(Equipment.class));
        verify(auditBuilderService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateWithClientProvidedCodeShouldFailBadRequest() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipment("EQ-2026-0001")));
        EquipmentUpdateRequest request = updateRequestWithCode("EQ-2026-0002");

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Equipment code is generated by system and must not be provided");

        verify(repository, never()).save(any());
    }

    @Test
    void updateWithoutDepartmentIdPreservesExistingNullableDepartment() {
        UUID id = UUID.randomUUID();
        Equipment existing = equipment("EQ-2026-0003");
        existing.setId(id);
        existing.setDepartmentId(null);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubEnrichment();
        EquipmentUpdateRequest request = new EquipmentUpdateRequest(
                null,
                "Updated Name",
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

        EquipmentDto updated = service.update(id, request);

        assertThat(updated.name()).isEqualTo("Updated Name");
        ArgumentCaptor<Equipment> entityCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(repository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getDepartmentId()).isNull();
        verify(departmentRepository, never()).findByIdAndIsDeletedFalse(any());
    }

    @Test
    void updateWithDepartmentIdValidatesAndApplies() {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Equipment existing = equipment("EQ-2026-0004");
        existing.setId(id);
        existing.setDepartmentId(null);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(existing));
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(department(departmentId)));
        when(repository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubEnrichment();
        EquipmentUpdateRequest request = new EquipmentUpdateRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                departmentId,
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

        EquipmentDto updated = service.update(id, request);

        assertThat(updated.departmentId()).isEqualTo(departmentId);
        ArgumentCaptor<Equipment> entityCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(repository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getDepartmentId()).isEqualTo(departmentId);
        verify(departmentRepository).findByIdAndIsDeletedFalse(departmentId);
    }

    @Test
    void updateWithInvalidDepartmentIdThrowsNotFound() {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Equipment existing = equipment("EQ-2026-0005");
        existing.setId(id);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(existing));
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.empty());
        EquipmentUpdateRequest request = new EquipmentUpdateRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                departmentId,
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

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Department not found: " + departmentId);

        verify(repository, never()).save(any());
    }

    @Test
    void moveWithNullTargetTypeRejected400() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-PLACEMENT-0");
        equipment.setId(equipmentId);
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));

        assertThatThrownBy(() -> service.updatePlacement(
                equipmentId,
                new EquipmentPlacementRequest(null, null, null, null)
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("targetType is required");
    }

    @Test
    void moveDepartmentEquipmentToWarehouseDefaultAvailable() {
        UUID equipmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-PLACEMENT-1");
        equipment.setId(equipmentId);
        equipment.setDepartmentId(UUID.randomUUID());
        equipment.setLocationId(null);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setCode("WH-001");
        warehouse.setName("Main Warehouse");

        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(repository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubEnrichment();
        stubWarehouseLocationFallback(List.of(warehouse));

        EquipmentDto updated = service.updatePlacement(
                equipmentId,
                new EquipmentPlacementRequest(PlacementTargetType.WAREHOUSE, warehouseId, null, null)
        );

        assertThat(updated.departmentId()).isNull();
        assertThat(updated.locationId()).isEqualTo(warehouseId);
        verify(warehouseEquipmentItemService).transferEquipmentToWarehouse(
                equipmentId,
                warehouseId,
                WarehouseEquipmentStatus.AVAILABLE
        );
    }

    @Test
    void moveDepartmentEquipmentToWarehouseOutOfService() {
        UUID equipmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-PLACEMENT-2");
        equipment.setId(equipmentId);
        equipment.setDepartmentId(UUID.randomUUID());

        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setCode("WH-002");
        warehouse.setName("Reserve Warehouse");

        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(repository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubEnrichment();
        stubWarehouseLocationFallback(List.of(warehouse));

        EquipmentDto updated = service.updatePlacement(
                equipmentId,
                new EquipmentPlacementRequest(
                        PlacementTargetType.WAREHOUSE,
                        warehouseId,
                        null,
                        WarehouseEquipmentStatus.OUT_OF_SERVICE
                )
        );

        assertThat(updated.departmentId()).isNull();
        assertThat(updated.locationId()).isEqualTo(warehouseId);
        verify(warehouseEquipmentItemService).transferEquipmentToWarehouse(
                equipmentId,
                warehouseId,
                WarehouseEquipmentStatus.OUT_OF_SERVICE
        );
    }

    @Test
    void moveWarehouseEquipmentToDepartment() {
        UUID equipmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-PLACEMENT-3");
        equipment.setId(equipmentId);
        equipment.setDepartmentId(null);
        equipment.setLocationId(warehouseId);

        WarehouseEquipmentItem activeItem = new WarehouseEquipmentItem();
        activeItem.setWarehouseId(warehouseId);
        activeItem.setEquipmentId(equipmentId);
        activeItem.setStatus(WarehouseEquipmentStatus.AVAILABLE);
        activeItem.setActive(true);
        activeItem.setDeleted(false);

        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(department(departmentId)));
        when(warehouseEquipmentItemRepository.findActiveByEquipmentId(equipmentId)).thenReturn(Optional.of(activeItem));
        when(repository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubEnrichment();

        EquipmentDto updated = service.updatePlacement(
                equipmentId,
                new EquipmentPlacementRequest(PlacementTargetType.DEPARTMENT, null, departmentId, null)
        );

        assertThat(updated.departmentId()).isEqualTo(departmentId);
        assertThat(updated.locationId()).isNull();
        verify(warehouseEquipmentItemService).updateStatus(
                warehouseId,
                equipmentId,
                WarehouseEquipmentStatus.INSTALLED,
                departmentId
        );
    }

    @Test
    void moveWarehouseOutOfServiceEquipmentToDepartmentRejected() {
        UUID equipmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-PLACEMENT-4");
        equipment.setId(equipmentId);

        WarehouseEquipmentItem activeItem = new WarehouseEquipmentItem();
        activeItem.setWarehouseId(warehouseId);
        activeItem.setEquipmentId(equipmentId);
        activeItem.setStatus(WarehouseEquipmentStatus.OUT_OF_SERVICE);
        activeItem.setActive(true);
        activeItem.setDeleted(false);

        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(department(departmentId)));
        when(warehouseEquipmentItemRepository.findActiveByEquipmentId(equipmentId)).thenReturn(Optional.of(activeItem));

        assertThatThrownBy(() -> service.updatePlacement(
                equipmentId,
                new EquipmentPlacementRequest(PlacementTargetType.DEPARTMENT, null, departmentId, null)
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("OUT_OF_SERVICE equipment cannot be installed directly");
    }

    @Test
    void moveToWarehouseWithInvalidWarehouseReturns404() {
        UUID equipmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-PLACEMENT-5");
        equipment.setId(equipmentId);
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePlacement(
                equipmentId,
                new EquipmentPlacementRequest(PlacementTargetType.WAREHOUSE, warehouseId, null, null)
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Warehouse not found: " + warehouseId);
    }

    @Test
    void moveToDepartmentWithInvalidDepartmentReturns404() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-PLACEMENT-6");
        equipment.setId(equipmentId);
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePlacement(
                equipmentId,
                new EquipmentPlacementRequest(PlacementTargetType.DEPARTMENT, null, departmentId, null)
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Department not found: " + departmentId);
    }

    @Test
    void moveToWarehouseWithDepartmentIdRejected400() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-PLACEMENT-7");
        equipment.setId(equipmentId);
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));

        assertThatThrownBy(() -> service.updatePlacement(
                equipmentId,
                new EquipmentPlacementRequest(
                        PlacementTargetType.WAREHOUSE,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null
                )
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("warehouseId and departmentId cannot both be provided");
    }

    @Test
    void moveToDepartmentWithWarehouseIdRejected400() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-PLACEMENT-8");
        equipment.setId(equipmentId);
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));

        assertThatThrownBy(() -> service.updatePlacement(
                equipmentId,
                new EquipmentPlacementRequest(
                        PlacementTargetType.DEPARTMENT,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null
                )
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("warehouseId and departmentId cannot both be provided");
    }

    @Test
    void moveToDepartmentWithWarehouseStatusRejected400() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-PLACEMENT-9");
        equipment.setId(equipmentId);
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));

        assertThatThrownBy(() -> service.updatePlacement(
                equipmentId,
                new EquipmentPlacementRequest(
                        PlacementTargetType.DEPARTMENT,
                        null,
                        departmentId,
                        WarehouseEquipmentStatus.AVAILABLE
                )
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("warehouseStatus must be null when targetType is DEPARTMENT");
    }

    @Test
    void moveToWarehouseWithUnsupportedWarehouseStatusRejected400() {
        UUID equipmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-PLACEMENT-10");
        equipment.setId(equipmentId);
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));

        assertThatThrownBy(() -> service.updatePlacement(
                equipmentId,
                new EquipmentPlacementRequest(
                        PlacementTargetType.WAREHOUSE,
                        warehouseId,
                        null,
                        WarehouseEquipmentStatus.RESERVED
                )
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("warehouseStatus for WAREHOUSE target must be AVAILABLE or OUT_OF_SERVICE");
    }

    @Test
    void moveToWarehouseWithInstalledWarehouseStatusRejected400() {
        UUID equipmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-PLACEMENT-11");
        equipment.setId(equipmentId);
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));

        assertThatThrownBy(() -> service.updatePlacement(
                equipmentId,
                new EquipmentPlacementRequest(
                        PlacementTargetType.WAREHOUSE,
                        warehouseId,
                        null,
                        WarehouseEquipmentStatus.INSTALLED
                )
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("warehouseStatus for WAREHOUSE target must be AVAILABLE or OUT_OF_SERVICE");
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

    private EquipmentCreateRequest createRequest(String code, String inventoryNumber, UUID departmentId, UUID warehouseId) {
        return new EquipmentCreateRequest(
                code,
                "Compressor",
                inventoryNumber,
                "TN-1",
                "SN-1",
                "Model X",
                UUID.randomUUID(),
                departmentId,
                warehouseId,
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

    private String stubCreateFlow(String inventoryNumber) {
        int year = Year.now().getValue();
        String expectedCode = "EQ-" + year + "-0020";
        stubEnrichment();
        when(repository.existsByInventoryNumberAndIsDeletedFalse(inventoryNumber)).thenReturn(false);
        when(repository.maxSequenceByCodePrefix("EQ-" + year + "-")).thenReturn(19L);
        when(repository.existsByCodeAndIsDeletedFalse(expectedCode)).thenReturn(false);
        when(repository.save(any(Equipment.class))).thenAnswer(invocation -> {
            Equipment entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });
        return expectedCode;
    }

    private void stubCreateFlowWithoutEnrichment(String inventoryNumber) {
        int year = Year.now().getValue();
        String expectedCode = "EQ-" + year + "-0020";
        when(repository.existsByInventoryNumberAndIsDeletedFalse(inventoryNumber)).thenReturn(false);
        when(repository.maxSequenceByCodePrefix("EQ-" + year + "-")).thenReturn(19L);
        when(repository.existsByCodeAndIsDeletedFalse(expectedCode)).thenReturn(false);
        when(repository.save(any(Equipment.class))).thenAnswer(invocation -> {
            Equipment entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });
    }

    private Department department(UUID id) {
        Department department = new Department();
        department.setId(id);
        department.setCode("DEP-001");
        department.setName("Main department");
        return department;
    }

    private EquipmentUpdateRequest updateRequestWithCode(String code) {
        return new EquipmentUpdateRequest(
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

    private void stubWarehouseLocationFallback(List<Warehouse> warehouses) {
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(warehouses);
    }
}
