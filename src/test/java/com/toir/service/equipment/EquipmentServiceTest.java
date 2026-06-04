package com.toir.service.equipment;

import com.toir.dto.equipment.EquipmentDto;
import com.toir.dto.equipment.EquipmentCreateRequest;
import com.toir.dto.equipment.EquipmentDetailDto;
import com.toir.dto.equipment.EquipmentLocationRequest;
import com.toir.dto.equipment.EquipmentPlacementRequest;
import com.toir.dto.equipment.EquipmentUpdateRequest;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueRequest;
import com.toir.dto.equipmentmanualattribute.EquipmentManualAttributeRequest;
import com.toir.dto.warehouse.WarehouseEquipmentAssignRequest;
import com.toir.dto.warehouse.WarehouseEquipmentItemDto;
import com.toir.entity.Department;
import com.toir.entity.DowntimeEvent;
import com.toir.entity.FileAsset;
import com.toir.entity.Location;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentLocationHistory;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseEquipmentItem;
import com.toir.enums.DefectStatus;
import com.toir.enums.DowntimeType;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentAttributeDataType;
import com.toir.enums.EquipmentLocationType;
import com.toir.enums.EquipmentOutsideReason;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.PlacementType;
import com.toir.enums.PlacementTargetType;
import com.toir.enums.RequestStatus;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseEquipmentItemRepository;
import com.toir.repository.DowntimeEventRepository;
import com.toir.repository.FileAssetRepository;
import com.toir.repository.LocationRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentLocationHistoryRepository;
import com.toir.repository.equipment.EquipmentPassportRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.WarehouseEquipmentItemService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import com.toir.dto.equipment.EquipmentStatsResponse;
import com.toir.repository.equipment.EquipmentStatsProjection;

import java.time.Year;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
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
    FileAssetRepository fileAssetRepository;

    @Mock
    EquipmentLocationHistoryRepository equipmentLocationHistoryRepository;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    WarehouseEquipmentItemService warehouseEquipmentItemService;

    @Mock
    WarehouseEquipmentItemRepository warehouseEquipmentItemRepository;

    @Mock
    RepairRequestRepository repairRequestRepository;

    @Mock
    DefectRepository defectRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    DowntimeEventRepository downtimeEventRepository;

    @Mock
    EquipmentAttributeService equipmentAttributeService;

    @Mock
    EquipmentManualAttributeService equipmentManualAttributeService;

    @Mock
    EquipmentStatusLifecycleService equipmentStatusLifecycleService;

    @Spy
    EquipmentLocationValidator equipmentLocationValidator;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    UserRepository userRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    @BeforeEach
    void setUp() {
        lenient().when(departmentRepository.findByIdAndIsDeletedFalse(any()))
                .thenAnswer(invocation -> Optional.of(department(invocation.getArgument(0))));
    }

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
    void enrichPopulatesWarrantyAttachmentDownloadLinkFromFileAsset() {
        UUID warrantyAttachmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-WARRANTY");
        equipment.setHasWarranty(true);
        equipment.setWarrantyAttachmentId(warrantyAttachmentId);
        equipment.setWarrantyStartDate(LocalDate.of(2026, 6, 1));
        equipment.setWarrantyEndDate(LocalDate.of(2027, 6, 1));

        FileAsset warrantyFile = fileAsset(
                warrantyAttachmentId,
                "warranty-stored.pdf",
                "Warranty.pdf",
                "application/pdf",
                123_456L
        );

        stubEnrichment();
        when(repository.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1));
        when(fileAssetRepository.findAllByIdInAndIsDeletedFalse(anyCollection()))
                .thenReturn(List.of(warrantyFile));

        Page<EquipmentDto> result = service.search(null, null, null, null, null, false, null, 0, 20);

        EquipmentDto dto = result.getContent().getFirst();
        assertThat(dto.hasWarranty()).isTrue();
        assertThat(dto.warrantyAttachmentId()).isEqualTo(warrantyAttachmentId);
        assertThat(dto.warrantyAttachment()).isNotNull();
        assertThat(dto.warrantyAttachment().id()).isEqualTo(warrantyAttachmentId);
        assertThat(dto.warrantyAttachment().originalName()).isEqualTo("Warranty.pdf");
        assertThat(dto.warrantyAttachment().mimeType()).isEqualTo("application/pdf");
        assertThat(dto.warrantyAttachment().sizeBytes()).isEqualTo(123_456L);
        assertThat(dto.warrantyAttachment().downloadUrl())
                .isEqualTo("/api/v1/files/assets/" + warrantyAttachmentId + "/download");
        assertThat(dto.warrantyStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(dto.warrantyEndDate()).isEqualTo(LocalDate.of(2027, 6, 1));
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
    void findByIdDetailReturnsRelatedRepairRequests() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-DETAIL-RR");
        equipment.setId(equipmentId);
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        stubEnrichment();

        RepairRequest request = RepairRequest.builder()
                .number("RR-001")
                .title("Seal leak")
                .description("Detected leak on pump")
                .equipmentId(equipmentId)
                .departmentId(UUID.randomUUID())
                .reporterId(UUID.randomUUID())
                .status(RequestStatus.OPEN)
                .build();
        request.setId(UUID.randomUUID());
        request.setDetectedAt(Instant.parse("2026-05-01T10:00:00Z"));

        when(repairRequestRepository.search(null, null, equipmentId)).thenReturn(List.of(request));
        when(defectRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of());
        when(workOrderRepository.search(null, null, equipmentId)).thenReturn(List.of());
        when(downtimeEventRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(equipmentId)).thenReturn(List.of());

        EquipmentDetailDto detail = service.findDetailById(equipmentId);

        assertThat(detail.repairRequests()).hasSize(1);
        assertThat(detail.repairRequests().getFirst().number()).isEqualTo("RR-001");
        assertThat(detail.repairRequests().getFirst().title()).isEqualTo("Seal leak");
    }

    @Test
    void findByIdDetailReturnsRelatedDefects() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-DETAIL-DEF");
        equipment.setId(equipmentId);
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        stubEnrichment();

        Defect defect = Defect.builder()
                .code("DEF-001")
                .title("Bearing overheating")
                .description("Temperature threshold exceeded")
                .equipmentId(equipmentId)
                .status(DefectStatus.OPEN)
                .build();
        defect.setId(UUID.randomUUID());
        defect.setDetectedAt(Instant.parse("2026-05-02T09:00:00Z"));

        when(repairRequestRepository.search(null, null, equipmentId)).thenReturn(List.of());
        when(defectRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of(defect));
        when(workOrderRepository.search(null, null, equipmentId)).thenReturn(List.of());
        when(downtimeEventRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(equipmentId)).thenReturn(List.of());

        EquipmentDetailDto detail = service.findDetailById(equipmentId);

        assertThat(detail.defects()).hasSize(1);
        assertThat(detail.defects().getFirst().code()).isEqualTo("DEF-001");
        assertThat(detail.defects().getFirst().title()).isEqualTo("Bearing overheating");
    }

    @Test
    void findByIdDetailReturnsRelatedWorkOrders() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-DETAIL-WO");
        equipment.setId(equipmentId);
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        stubEnrichment();

        WorkOrder workOrder = WorkOrder.builder()
                .number("WO-001")
                .title("Replace bearing")
                .equipmentId(equipmentId)
                .departmentId(UUID.randomUUID())
                .createdById(UUID.randomUUID())
                .type(WorkOrderType.DEFECT)
                .status(WorkOrderStatus.IN_PROGRESS)
                .summary("Bearing replacement in progress")
                .build();
        workOrder.setId(UUID.randomUUID());

        when(repairRequestRepository.search(null, null, equipmentId)).thenReturn(List.of());
        when(defectRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of());
        when(workOrderRepository.search(null, null, equipmentId)).thenReturn(List.of(workOrder));
        when(downtimeEventRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(equipmentId)).thenReturn(List.of());

        EquipmentDetailDto detail = service.findDetailById(equipmentId);

        assertThat(detail.workOrders()).hasSize(1);
        assertThat(detail.workOrders().getFirst().number()).isEqualTo("WO-001");
        assertThat(detail.workOrders().getFirst().title()).isEqualTo("Replace bearing");
    }

    @Test
    void findByIdDetailReturnsRelatedDowntimeEvents() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-DETAIL-DT");
        equipment.setId(equipmentId);
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        stubEnrichment();

        DowntimeEvent downtime = DowntimeEvent.builder()
                .equipmentId(equipmentId)
                .departmentId(UUID.randomUUID())
                .startAt(Instant.parse("2026-05-03T08:00:00Z"))
                .endAt(Instant.parse("2026-05-03T09:00:00Z"))
                .durationMinutes(60)
                .type(DowntimeType.EMERGENCY)
                .description("Unexpected stop")
                .build();
        downtime.setId(UUID.randomUUID());

        when(repairRequestRepository.search(null, null, equipmentId)).thenReturn(List.of());
        when(defectRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of());
        when(workOrderRepository.search(null, null, equipmentId)).thenReturn(List.of());
        when(downtimeEventRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(equipmentId)).thenReturn(List.of(downtime));

        EquipmentDetailDto detail = service.findDetailById(equipmentId);

        assertThat(detail.downtimeEvents()).hasSize(1);
        assertThat(detail.downtimeEvents().getFirst().durationMinutes()).isEqualTo(60);
        assertThat(detail.downtimeEvents().getFirst().description()).isEqualTo("Unexpected stop");
    }

    @Test
    void findByIdDetailWithNoRelatedDataReturnsEmptyArrays() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-DETAIL-EMPTY");
        equipment.setId(equipmentId);
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        stubEnrichment();

        when(repairRequestRepository.search(null, null, equipmentId)).thenReturn(List.of());
        when(defectRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of());
        when(workOrderRepository.search(null, null, equipmentId)).thenReturn(List.of());
        when(downtimeEventRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(equipmentId)).thenReturn(List.of());

        EquipmentDetailDto detail = service.findDetailById(equipmentId);

        assertThat(detail.repairRequests()).isNotNull().isEmpty();
        assertThat(detail.defects()).isNotNull().isEmpty();
        assertThat(detail.workOrders()).isNotNull().isEmpty();
        assertThat(detail.downtimeEvents()).isNotNull().isEmpty();
    }

    @Test
    void findByIdDetailIncludesDynamicAttributes() {
        UUID equipmentId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-DETAIL-ATTR");
        equipment.setId(equipmentId);
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        stubEnrichment();

        when(repairRequestRepository.search(null, null, equipmentId)).thenReturn(List.of());
        when(defectRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of());
        when(workOrderRepository.search(null, null, equipmentId)).thenReturn(List.of());
        when(downtimeEventRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(equipmentId)).thenReturn(List.of());
        when(equipmentAttributeService.findValues(equipmentId)).thenReturn(List.of(new EquipmentAttributeValueDto(
                UUID.randomUUID(),
                equipmentId,
                definitionId,
                "motor_power",
                "Motor Power",
                null,
                null,
                EquipmentAttributeDataType.NUMBER,
                "kW",
                true,
                null,
                List.of(),
                "Motor",
                10,
                null,
                75.0,
                null,
                null,
                null,
                null
        )));

        EquipmentDetailDto detail = service.findDetailById(equipmentId);

        assertThat(detail.attributes()).hasSize(1);
        assertThat(detail.attributes().getFirst().key()).isEqualTo("motor_power");
        assertThat(detail.attributes().getFirst().valueNumber()).isEqualTo(75.0);
    }

    @Test
    void genericEquipmentUpdate_doesNotSilentlyOverwriteStatus() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-STATUS-UPDATE");
        equipment.setId(equipmentId);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));

        EquipmentUpdateRequest request = new EquipmentUpdateRequest(
                null,
                "Equipment updated",
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
                EquipmentStatus.OUT_OF_SERVICE,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.update(equipmentId, request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("dedicated status endpoint");
                });

        assertThat(equipment.getStatus()).isEqualTo(EquipmentStatus.ACTIVE);
        verify(repository, never()).save(any(Equipment.class));
    }

    @Test
    void findByIdDetailUnknownEquipmentReturns404() {
        UUID equipmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findDetailById(equipmentId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Equipment not found");
    }

    @Test
    void equipmentWithDepartmentOnlyReturnsPlacementDepartment() {
        UUID departmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-PLACEMENT-DEP-ONLY");
        equipment.setDepartmentId(departmentId);
        Page<Equipment> page = new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1);

        Department department = department(departmentId);

        stubEnrichment();
        when(repository.search(any(), any(), any(), any(), any(), any())).thenReturn(page);
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of(department));

        EquipmentDto dto = service.search(null, null, null, null, null, false, null, 0, 20)
                .getContent()
                .getFirst();

        assertThat(dto.placement()).isNotNull();
        assertThat(dto.placement().type()).isEqualTo(PlacementType.DEPARTMENT);
        assertThat(dto.placement().department()).isNotNull();
        assertThat(dto.placement().department().id()).isEqualTo(departmentId);
        assertThat(dto.placement().warehouse()).isNull();
        assertThat(dto.placement().warehouseStatus()).isNull();
    }

    @Test
    void equipmentWithWarehouseOnlyReturnsPlacementWarehouse() {
        UUID equipmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-PLACEMENT-WH-ONLY");
        equipment.setId(equipmentId);
        equipment.setDepartmentId(null);
        equipment.setLocationId(warehouseId);
        Page<Equipment> page = new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setCode("WH-001");
        warehouse.setName("Main Warehouse");
        WarehouseEquipmentItem activeItem = activeWarehouseItem(equipmentId, warehouseId, WarehouseEquipmentStatus.RESERVED);

        stubEnrichment();
        when(repository.search(any(), any(), any(), any(), any(), any())).thenReturn(page);
        when(warehouseEquipmentItemRepository.findActiveByEquipmentIds(anyCollection())).thenReturn(List.of(activeItem));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of(warehouse));

        EquipmentDto dto = service.search(null, null, null, null, null, false, null, 0, 20)
                .getContent()
                .getFirst();

        assertThat(dto.placement()).isNotNull();
        assertThat(dto.placement().type()).isEqualTo(PlacementType.WAREHOUSE);
        assertThat(dto.placement().department()).isNull();
        assertThat(dto.placement().warehouse()).isNotNull();
        assertThat(dto.placement().warehouse().id()).isEqualTo(warehouseId);
        assertThat(dto.placement().warehouseStatus()).isEqualTo(WarehouseEquipmentStatus.RESERVED);
    }

    @Test
    void equipmentWithNoDepartmentAndNoWarehouseReturnsPlacementUnknown() {
        Equipment equipment = equipment("EQ-PLACEMENT-UNKNOWN");
        equipment.setDepartmentId(null);
        equipment.setLocationId(null);
        Page<Equipment> page = new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1);

        stubEnrichment();
        when(repository.search(any(), any(), any(), any(), any(), any())).thenReturn(page);

        EquipmentDto dto = service.search(null, null, null, null, null, false, null, 0, 20)
                .getContent()
                .getFirst();

        assertThat(dto.placement()).isNotNull();
        assertThat(dto.placement().type()).isEqualTo(PlacementType.UNKNOWN);
        assertThat(dto.placement().department()).isNull();
        assertThat(dto.placement().warehouse()).isNull();
        assertThat(dto.placement().warehouseStatus()).isNull();
    }

    @Test
    void equipmentWithDepartmentAndInstalledWarehouseItemReturnsPlacementDepartmentWithWarehouseMetadata() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-PLACEMENT-DEP-INSTALLED");
        equipment.setId(equipmentId);
        equipment.setDepartmentId(departmentId);
        Page<Equipment> page = new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setCode("WH-DEP");
        warehouse.setName("Install Warehouse");
        WarehouseEquipmentItem activeItem = activeWarehouseItem(equipmentId, warehouseId, WarehouseEquipmentStatus.INSTALLED);

        stubEnrichment();
        when(repository.search(any(), any(), any(), any(), any(), any())).thenReturn(page);
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of(department(departmentId)));
        when(warehouseEquipmentItemRepository.findActiveByEquipmentIds(anyCollection())).thenReturn(List.of(activeItem));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of(warehouse));

        EquipmentDto dto = service.search(null, null, null, null, null, false, null, 0, 20)
                .getContent()
                .getFirst();

        assertThat(dto.placement()).isNotNull();
        assertThat(dto.placement().type()).isEqualTo(PlacementType.DEPARTMENT);
        assertThat(dto.placement().department()).isNotNull();
        assertThat(dto.placement().warehouse()).isNotNull();
        assertThat(dto.placement().warehouse().id()).isEqualTo(warehouseId);
        assertThat(dto.placement().warehouseStatus()).isEqualTo(WarehouseEquipmentStatus.INSTALLED);
    }

    @Test
    void equipmentWithDepartmentAndActiveNonInstalledWarehouseItemDoesNot500() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-PLACEMENT-DEP-DRIFT");
        equipment.setId(equipmentId);
        equipment.setDepartmentId(departmentId);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setCode("WH-DRIFT");
        warehouse.setName("Data Drift Warehouse");
        WarehouseEquipmentItem activeItem = activeWarehouseItem(equipmentId, warehouseId, WarehouseEquipmentStatus.AVAILABLE);

        stubEnrichment();
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of(department(departmentId)));
        when(warehouseEquipmentItemRepository.findActiveByEquipmentIds(anyCollection())).thenReturn(List.of(activeItem));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of(warehouse));

        EquipmentDto dto = service.findById(equipmentId);

        assertThat(dto.placement()).isNotNull();
        assertThat(dto.placement().type()).isEqualTo(PlacementType.DEPARTMENT);
        assertThat(dto.placement().department()).isNotNull();
        assertThat(dto.placement().warehouse()).isNotNull();
        assertThat(dto.placement().warehouseStatus()).isEqualTo(WarehouseEquipmentStatus.AVAILABLE);
    }

    @Test
    void listEnrichmentBatchLoadsActiveWarehouseItemsAndWarehouses() {
        UUID equipmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-PLACEMENT-BATCH");
        equipment.setId(equipmentId);
        equipment.setDepartmentId(null);
        equipment.setLocationId(warehouseId);
        Page<Equipment> page = new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setCode("WH-BATCH");
        warehouse.setName("Batch Warehouse");
        WarehouseEquipmentItem activeItem = activeWarehouseItem(equipmentId, warehouseId, WarehouseEquipmentStatus.AVAILABLE);

        stubEnrichment();
        when(repository.search(any(), any(), any(), any(), any(), any())).thenReturn(page);
        when(warehouseEquipmentItemRepository.findActiveByEquipmentIds(anyCollection())).thenReturn(List.of(activeItem));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of(warehouse));

        Page<EquipmentDto> result = service.search(null, null, null, null, null, false, null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        verify(warehouseEquipmentItemRepository).findActiveByEquipmentIds(anyCollection());
        verify(warehouseRepository).findAllByIdInAndIsDeletedFalse(anyCollection());
    }

    @Test
    void detailFindByIdIncludesPlacement() {
        UUID equipmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-PLACEMENT-DETAIL");
        equipment.setId(equipmentId);
        equipment.setDepartmentId(null);
        equipment.setLocationId(warehouseId);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setCode("WH-DETAIL");
        warehouse.setName("Detail Warehouse");
        WarehouseEquipmentItem activeItem = activeWarehouseItem(equipmentId, warehouseId, WarehouseEquipmentStatus.OUT_OF_SERVICE);

        stubEnrichment();
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(warehouseEquipmentItemRepository.findActiveByEquipmentIds(anyCollection())).thenReturn(List.of(activeItem));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of(warehouse));

        EquipmentDto dto = service.findById(equipmentId);

        assertThat(dto.placement()).isNotNull();
        assertThat(dto.placement().type()).isEqualTo(PlacementType.WAREHOUSE);
        assertThat(dto.placement().warehouse()).isNotNull();
        assertThat(dto.placement().warehouse().id()).isEqualTo(warehouseId);
        assertThat(dto.placement().warehouseStatus()).isEqualTo(WarehouseEquipmentStatus.OUT_OF_SERVICE);
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
    void createPersistsAverageOperatingLifeHours() {
        UUID departmentId = UUID.randomUUID();
        EquipmentCreateRequest request = createRequest(null, "INV-AVG-1", departmentId, null, 10_000L);
        stubCreateFlow("INV-AVG-1");
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId))
                .thenReturn(Optional.of(department(departmentId)));

        EquipmentDto created = service.create(request);

        assertThat(created.averageOperatingLifeHours()).isEqualTo(10_000L);
        ArgumentCaptor<Equipment> entityCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(repository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getAverageOperatingLifeHours()).isEqualTo(10_000L);
    }

    @Test
    void createWithRequiredDynamicAttributeAndAttributesOmittedReturnsBadRequest() {
        UUID departmentId = UUID.randomUUID();
        EquipmentCreateRequest request = createRequest(null, "INV-REQ-OMITTED", departmentId, null);
        stubCreateFlowWithoutEnrichment("INV-REQ-OMITTED");
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId))
                .thenReturn(Optional.of(department(departmentId)));
        doThrow(RestException.badRequest("Missing required equipment attributes: motor_power (required by equipment type)"))
                .when(equipmentAttributeService).upsertValues(any(Equipment.class), eq(List.of()));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getMessage()).contains("Missing required equipment attributes"));
    }

    @Test
    void createWithRequiredDynamicAttributeAndEmptyAttributesReturnsBadRequest() {
        UUID departmentId = UUID.randomUUID();
        EquipmentCreateRequest request = createRequest(
                null,
                "INV-REQ-EMPTY",
                departmentId,
                null,
                10_000L,
                List.of()
        );
        stubCreateFlowWithoutEnrichment("INV-REQ-EMPTY");
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId))
                .thenReturn(Optional.of(department(departmentId)));
        doThrow(RestException.badRequest("Missing required equipment attributes: motor_power (required by equipment type)"))
                .when(equipmentAttributeService).upsertValues(any(Equipment.class), eq(List.of()));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getMessage()).contains("Missing required equipment attributes"));
    }

    @Test
    void createWithWarehouseIdOnlyCreatesEquipmentAndAssignsAvailableWarehouseItem() {
        UUID warehouseId = UUID.randomUUID();
        UUID warehouseDepartmentId = UUID.randomUUID();
        EquipmentCreateRequest request = createRequest(null, "INV-NEW-2", null, warehouseId);
        String expectedCode = stubCreateFlow("INV-NEW-2");
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setDepartmentId(warehouseDepartmentId);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(departmentRepository.findByIdAndIsDeletedFalse(warehouseDepartmentId))
                .thenReturn(Optional.of(department(warehouseDepartmentId)));
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
        assertThat(saved.getCurrentLocationType()).isEqualTo(EquipmentLocationType.WAREHOUSE);
        assertThat(saved.getCurrentWarehouseId()).isEqualTo(warehouseId);
        assertThat(saved.getResponsibleDepartmentId()).isEqualTo(warehouseDepartmentId);

        ArgumentCaptor<WarehouseEquipmentAssignRequest> assignCaptor =
                ArgumentCaptor.forClass(WarehouseEquipmentAssignRequest.class);
        verify(warehouseEquipmentItemService).assign(eq(warehouseId), assignCaptor.capture());
        assertThat(assignCaptor.getValue().equipmentId()).isEqualTo(saved.getId());
        assertThat(assignCaptor.getValue().status()).isEqualTo(WarehouseEquipmentStatus.AVAILABLE);
    }

    @Test
    void createWithBothDepartmentAndWarehouseRejected() {
        UUID departmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        EquipmentCreateRequest request = createRequest(null, "INV-NEW-3", departmentId, warehouseId);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("departmentId and warehouseId cannot both be provided");

        verify(repository, never()).save(any());
    }

    @Test
    void createWithOldDepartmentPayloadSetsCurrentLocationAndWritesHistory() {
        UUID departmentId = UUID.randomUUID();
        EquipmentCreateRequest request = createRequest(null, "INV-LOC-DEP", departmentId, null);
        EquipmentLocationRequest resolved = new EquipmentLocationRequest(
                EquipmentLocationType.DEPARTMENT,
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
                null,
                null
        );
        stubCreateFlow("INV-LOC-DEP");
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(department(departmentId)));

        service.create(request);

        ArgumentCaptor<Equipment> entityCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(repository).save(entityCaptor.capture());
        Equipment saved = entityCaptor.getValue();
        assertThat(saved.getCurrentLocationType()).isEqualTo(EquipmentLocationType.DEPARTMENT);
        assertThat(saved.getDepartmentId()).isEqualTo(departmentId);
        assertThat(saved.getCurrentWarehouseId()).isNull();
        assertThat(saved.getResponsibleDepartmentId()).isEqualTo(departmentId);
        verify(equipmentLocationHistoryRepository).save(any(EquipmentLocationHistory.class));
    }

    @Test
    void createWithNestedOutsidePayloadSetsOutsideFields() {
        UUID responsibleDepartmentId = UUID.randomUUID();
        EquipmentLocationRequest requestLocation = new EquipmentLocationRequest(
                EquipmentLocationType.OUTSIDE_FACILITY,
                null,
                null,
                null,
                responsibleDepartmentId,
                EquipmentOutsideReason.SERVICE,
                "Technician",
                null,
                null,
                LocalDate.now().plusDays(10),
                "Service center",
                null,
                null
        );
        EquipmentLocationRequest normalized = new EquipmentLocationRequest(
                EquipmentLocationType.OUTSIDE_FACILITY,
                null,
                null,
                null,
                responsibleDepartmentId,
                EquipmentOutsideReason.SERVICE,
                "Technician",
                null,
                LocalDate.now(),
                LocalDate.now().plusDays(10),
                "Service center",
                null,
                null
        );
        EquipmentCreateRequest request = new EquipmentCreateRequest(
                null,
                "Compressor",
                "INV-LOC-OUT",
                "TN-1",
                "SN-1",
                "Model X",
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                null,
                "ACME",
                EquipmentStatus.ACTIVE,
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                null,
                null,
                "test",
                10_000L,
                null,
                null,
                requestLocation
        );
        stubCreateFlow("INV-LOC-OUT");
        when(departmentRepository.findByIdAndIsDeletedFalse(responsibleDepartmentId))
                .thenReturn(Optional.of(department(responsibleDepartmentId)));

        service.create(request);

        ArgumentCaptor<Equipment> entityCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(repository).save(entityCaptor.capture());
        Equipment saved = entityCaptor.getValue();
        assertThat(saved.getCurrentLocationType()).isEqualTo(EquipmentLocationType.OUTSIDE_FACILITY);
        assertThat(saved.getDepartmentId()).isNull();
        assertThat(saved.getCurrentWarehouseId()).isNull();
        assertThat(saved.getOutsideReason()).isEqualTo(EquipmentOutsideReason.SERVICE);
        assertThat(saved.getOutsideStartedDate()).isEqualTo(normalized.outsideStartedDate());
        assertThat(saved.getOutsideDestination()).isEqualTo("Service center");
        assertThat(saved.getResponsibleDepartmentId()).isEqualTo(responsibleDepartmentId);
        verify(equipmentLocationHistoryRepository).save(any(EquipmentLocationHistory.class));
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
    void createRejectsMissingWarrantyAttachmentIdWithClearError() {
        UUID warrantyAttachmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        EquipmentCreateRequest request = new EquipmentCreateRequest(
                null,
                "Pump P-102",
                "INV-P-102",
                "TN-P-102",
                "SN-P-102",
                "CPK 150-400",
                UUID.randomUUID(),
                departmentId,
                null,
                null,
                null,
                null,
                null,
                "KSB",
                EquipmentStatus.ACTIVE,
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                null,
                null,
                null,
                true,
                warrantyAttachmentId,
                "Pump",
                10_000L,
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(repository.existsByInventoryNumberAndIsDeletedFalse("INV-P-102")).thenReturn(false);
        when(fileAssetRepository.findByIdAndIsDeletedFalse(warrantyAttachmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("File not found: " + warrantyAttachmentId);
    }

    @Test
    void createPersistsWarrantyPeriodWhenWarrantyEnabled() {
        UUID departmentId = UUID.randomUUID();
        EquipmentCreateRequest request = new EquipmentCreateRequest(
                null,
                "Pump P-103",
                "INV-P-103",
                "TN-P-103",
                "SN-P-103",
                "CPK 150-400",
                UUID.randomUUID(),
                departmentId,
                null,
                null,
                null,
                null,
                null,
                "KSB",
                EquipmentStatus.ACTIVE,
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                null,
                null,
                null,
                true,
                null,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2027, 6, 1),
                "Pump",
                10_000L,
                null,
                null,
                null,
                null,
                null,
                null
        );
        stubCreateFlowWithoutEnrichment("INV-P-103");
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(department(departmentId)));

        EquipmentDto created = service.create(request);

        assertThat(created.hasWarranty()).isTrue();
        assertThat(created.warrantyStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(created.warrantyEndDate()).isEqualTo(LocalDate.of(2027, 6, 1));
        ArgumentCaptor<Equipment> entityCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(repository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getWarrantyStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(entityCaptor.getValue().getWarrantyEndDate()).isEqualTo(LocalDate.of(2027, 6, 1));
    }

    @Test
    void createRejectsWarrantyEndDateBeforeStartDate() {
        EquipmentCreateRequest request = new EquipmentCreateRequest(
                null,
                "Pump P-104",
                "INV-P-104",
                "TN-P-104",
                "SN-P-104",
                "CPK 150-400",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                "KSB",
                EquipmentStatus.ACTIVE,
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                null,
                null,
                null,
                true,
                null,
                LocalDate.of(2027, 6, 1),
                LocalDate.of(2026, 6, 1),
                "Pump",
                10_000L,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Warranty end date must be after or equal to warranty start date");

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
        UUID warehouseDepartmentId = UUID.randomUUID();
        EquipmentCreateRequest request = createRequest(null, "INV-NEW-8", null, warehouseId);
        stubCreateFlowWithoutEnrichment("INV-NEW-8");
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setDepartmentId(warehouseDepartmentId);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(departmentRepository.findByIdAndIsDeletedFalse(warehouseDepartmentId))
                .thenReturn(Optional.of(department(warehouseDepartmentId)));
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
    void updateClearsWarrantyDatesWhenWarrantyDisabled() {
        UUID id = UUID.randomUUID();
        Equipment existing = equipment("EQ-WARRANTY-CLEAR");
        existing.setId(id);
        existing.setHasWarranty(true);
        existing.setWarrantyAttachmentId(UUID.randomUUID());
        existing.setWarrantyStartDate(LocalDate.of(2026, 6, 1));
        existing.setWarrantyEndDate(LocalDate.of(2027, 6, 1));
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(existing));
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
                false,
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

        assertThat(updated.hasWarranty()).isFalse();
        assertThat(updated.warrantyAttachmentId()).isNull();
        assertThat(updated.warrantyStartDate()).isNull();
        assertThat(updated.warrantyEndDate()).isNull();
        ArgumentCaptor<Equipment> entityCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(repository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getWarrantyAttachmentId()).isNull();
        assertThat(entityCaptor.getValue().getWarrantyStartDate()).isNull();
        assertThat(entityCaptor.getValue().getWarrantyEndDate()).isNull();
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
    void updateChangesAverageOperatingLifeHoursWhenProvided() {
        UUID id = UUID.randomUUID();
        Equipment existing = equipment("EQ-AVG-UPDATE");
        existing.setId(id);
        existing.setAverageOperatingLifeHours(8_000L);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(existing));
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
                12_000L
        );

        EquipmentDto updated = service.update(id, request);

        assertThat(updated.averageOperatingLifeHours()).isEqualTo(12_000L);
        ArgumentCaptor<Equipment> entityCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(repository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getAverageOperatingLifeHours()).isEqualTo(12_000L);
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
        assertThat(updated.locationId()).isNull();
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
        assertThat(updated.locationId()).isNull();
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

    @Test
    void moveDepartmentEquipmentToOutsideFacilitySetsOutsideFieldsAndWritesHistory() {
        UUID equipmentId = UUID.randomUUID();
        UUID responsibleDepartmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-PLACEMENT-OUTSIDE");
        equipment.setId(equipmentId);
        equipment.setDepartmentId(responsibleDepartmentId);

        EquipmentLocationRequest targetLocation = new EquipmentLocationRequest(
                EquipmentLocationType.OUTSIDE_FACILITY,
                null,
                null,
                null,
                responsibleDepartmentId,
                EquipmentOutsideReason.BUSINESS_TRIP,
                "Toshmat",
                null,
                LocalDate.of(2026, 6, 2),
                LocalDate.of(2026, 6, 10),
                "Toshkent",
                null,
                null
        );

        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(repository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubEnrichment();

        EquipmentDto updated = service.updatePlacement(
                equipmentId,
                new EquipmentPlacementRequest(null, null, null, null, targetLocation, "Vaqtincha berildi")
        );

        assertThat(updated.departmentId()).isNull();
        ArgumentCaptor<Equipment> entityCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(repository).save(entityCaptor.capture());
        Equipment saved = entityCaptor.getValue();
        assertThat(saved.getCurrentLocationType()).isEqualTo(EquipmentLocationType.OUTSIDE_FACILITY);
        assertThat(saved.getDepartmentId()).isNull();
        assertThat(saved.getCurrentWarehouseId()).isNull();
        assertThat(saved.getOutsideReason()).isEqualTo(EquipmentOutsideReason.BUSINESS_TRIP);
        assertThat(saved.getOutsideTakenBy()).isEqualTo("Toshmat");
        assertThat(saved.getOutsideDestination()).isEqualTo("Toshkent");
        verify(equipmentLocationHistoryRepository).save(any(EquipmentLocationHistory.class));
        verify(auditBuilderService).log(
                eq("equipment"),
                eq(equipmentId.toString()),
                eq(com.toir.enums.AuditAction.UPDATE),
                eq(com.toir.enums.AuditModule.EQUIPMENT),
                eq("Equipment location changed: null -> OUTSIDE_FACILITY"),
                any(),
                any()
        );
    }

    @Test
    void createPersistsDynamicAttributesAfterEquipmentSave() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        EquipmentCreateRequest request = new EquipmentCreateRequest(
                null,
                "Pump P-101",
                "INV-P-101",
                "TN-P-101",
                "SN-P-101",
                "CPK 150-400",
                equipmentTypeId,
                departmentId,
                null,
                null,
                null,
                null,
                null,
                "KSB",
                EquipmentStatus.ACTIVE,
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                null,
                null,
                "Pump",
                10_000L,
                List.of(new EquipmentAttributeValueRequest(null, "motor_power", null, 75.0, null, null, null, null))
        );
        stubCreateFlow("INV-P-101");
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(department(departmentId)));

        EquipmentDto created = service.create(request);

        assertThat(created.name()).isEqualTo("Pump P-101");
        verify(equipmentAttributeService).upsertValues(any(Equipment.class), eq(request.attributes()));
    }

    @Test
    void equipmentCreateStillAcceptsOfficialAttributes() {
        UUID departmentId = UUID.randomUUID();
        EquipmentCreateRequest request = createRequest(
                null,
                "INV-OFFICIAL-ATTR",
                departmentId,
                null,
                10_000L,
                List.of(new EquipmentAttributeValueRequest(null, "motor_power", null, 75.0, null, null, null, null))
        );
        stubCreateFlow("INV-OFFICIAL-ATTR");
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(department(departmentId)));

        service.create(request);

        verify(equipmentAttributeService).upsertValues(any(Equipment.class), eq(request.attributes()));
        verify(equipmentManualAttributeService, never()).replaceAll(any(), any());
    }

    @Test
    void equipmentCreatePersistsManualAttributes() {
        EquipmentCreateRequest request = createRequestWithManualAttributes(List.of(
                new EquipmentManualAttributeRequest("legacy_key", "legacy value")
        ));

        stubCreateFlow("INV-MANUAL-DISABLED");
        when(departmentRepository.findByIdAndIsDeletedFalse(request.departmentId())).thenReturn(Optional.of(department(request.departmentId())));

        service.create(request);

        verify(equipmentManualAttributeService).replaceAll(any(UUID.class), argThat(bulk ->
                bulk.attributes().size() == 1
                        && "legacy_key".equals(bulk.attributes().getFirst().key())
                        && "legacy value".equals(bulk.attributes().getFirst().value())));
    }

    @Test
    void updateWithAttributesPersistsDynamicAttributeChanges() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-UPDATE-ATTR");
        equipment.setId(equipmentId);
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(repository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubEnrichment();

        EquipmentUpdateRequest request = new EquipmentUpdateRequest(
                null,
                "Pump P-101 Updated",
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
                List.of(new EquipmentAttributeValueRequest(null, "motor_power", null, 90.0, null, null, null, null))
        );

        service.update(equipmentId, request);

        verify(equipmentAttributeService).upsertValues(any(Equipment.class), eq(request.attributes()));
    }

    @Test
    void equipmentUpdateStillAcceptsOfficialAttributes() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-UPDATE-OFFICIAL-ATTR");
        equipment.setId(equipmentId);
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(repository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubEnrichment();

        EquipmentUpdateRequest request = new EquipmentUpdateRequest(
                null,
                "Pump P-101 Updated",
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
                List.of(new EquipmentAttributeValueRequest(null, "motor_power", null, 90.0, null, null, null, null))
        );

        service.update(equipmentId, request);

        verify(equipmentAttributeService).upsertValues(any(Equipment.class), eq(request.attributes()));
        verify(equipmentManualAttributeService, never()).replaceAll(any(), any());
    }

    @Test
    void equipmentUpdatePersistsManualAttributes() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-UPDATE-MANUAL-ATTR");
        equipment.setId(equipmentId);
        EquipmentUpdateRequest request = updateRequestWithManualAttributes(List.of(
                new EquipmentManualAttributeRequest("legacy_key", "legacy value")
        ));

        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(repository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubEnrichment();

        service.update(equipmentId, request);

        verify(equipmentManualAttributeService).replaceAll(eq(equipmentId), argThat(bulk ->
                bulk.attributes().size() == 1
                        && "legacy_key".equals(bulk.attributes().getFirst().key())
                        && "legacy value".equals(bulk.attributes().getFirst().value())));
    }

    @Test
    void updateWithoutAttributesPreservesExistingDynamicAttributes() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-UPDATE-NO-ATTR");
        equipment.setId(equipmentId);
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(repository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubEnrichment();

        service.update(equipmentId, updateRequestWithCode(null));

        verify(equipmentAttributeService, never()).upsertValues(any(), any());
    }

    @Test
    void equipmentUpdateSameTypeWithoutAttributesKeepsExistingBehavior() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-SAME-TYPE-NO-ATTR");
        equipment.setId(equipmentId);
        equipment.setEquipmentTypeId(typeId);
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(repository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubEnrichment();

        service.update(equipmentId, updateRequestWithTypeAndAttributes(typeId, null));

        verify(equipmentAttributeService, never()).upsertValues(any(), any());
    }

    @Test
    void equipmentTypeChangeWithoutAttributesIsRejected() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-TYPE-CHANGE-NO-ATTR");
        equipment.setId(equipmentId);
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));

        assertThatThrownBy(() -> service.update(
                equipmentId,
                updateRequestWithTypeAndAttributes(UUID.randomUUID(), null)
        ))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getMessage()).contains("Attributes are required when equipment type changes."));

        verify(repository, never()).save(any());
        verify(equipmentAttributeService, never()).upsertValues(any(), any());
    }

    @Test
    void equipmentTypeChangeWithEmptyAttributesAndRequiredNewTypeIsRejected() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-TYPE-CHANGE-EMPTY-ATTR");
        equipment.setId(equipmentId);
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(repository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(RestException.badRequest("Missing required equipment attributes: payload_capacity (required by equipment type)"))
                .when(equipmentAttributeService).upsertValues(any(Equipment.class), eq(List.of()));

        assertThatThrownBy(() -> service.update(
                equipmentId,
                updateRequestWithTypeAndAttributes(UUID.randomUUID(), List.of())
        ))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getMessage()).contains("Missing required equipment attributes"));
    }

    @Test
    void equipmentTypeChangeWithRequiredNewTypeAttributesSucceeds() {
        UUID equipmentId = UUID.randomUUID();
        UUID newTypeId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-TYPE-CHANGE-ATTR");
        equipment.setId(equipmentId);
        List<EquipmentAttributeValueRequest> attributes = List.of(
                new EquipmentAttributeValueRequest(null, "payload_capacity", null, 12000.0, null, null, null, null)
        );
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(repository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubEnrichment();

        service.update(equipmentId, updateRequestWithTypeAndAttributes(newTypeId, attributes));

        ArgumentCaptor<Equipment> equipmentCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(equipmentAttributeService).upsertValues(equipmentCaptor.capture(), eq(attributes));
        assertThat(equipmentCaptor.getValue().getEquipmentTypeId()).isEqualTo(newTypeId);
    }

    @Test
    void equipmentTypeChangeRejectsOldTypeAttributeKey() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-TYPE-CHANGE-OLD-KEY");
        equipment.setId(equipmentId);
        List<EquipmentAttributeValueRequest> attributes = List.of(
                new EquipmentAttributeValueRequest(null, "old_type_key", "legacy", null, null, null, null, null)
        );
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(repository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(RestException.badRequest("Unknown equipment attribute key: old_type_key"))
                .when(equipmentAttributeService).upsertValues(any(Equipment.class), eq(attributes));

        assertThatThrownBy(() -> service.update(
                equipmentId,
                updateRequestWithTypeAndAttributes(UUID.randomUUID(), attributes)
        ))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getMessage()).contains("Unknown equipment attribute key"));
    }

    @Test
    void equipmentTypeChangeRejectsOldTypeAttributeDefinitionId() {
        UUID equipmentId = UUID.randomUUID();
        UUID oldDefinitionId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-TYPE-CHANGE-OLD-ID");
        equipment.setId(equipmentId);
        List<EquipmentAttributeValueRequest> attributes = List.of(
                new EquipmentAttributeValueRequest(oldDefinitionId, null, "legacy", null, null, null, null, null)
        );
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(repository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(RestException.badRequest("Attribute definition is not allowed for this equipment type: " + oldDefinitionId))
                .when(equipmentAttributeService).upsertValues(any(Equipment.class), eq(attributes));

        assertThatThrownBy(() -> service.update(
                equipmentId,
                updateRequestWithTypeAndAttributes(UUID.randomUUID(), attributes)
        ))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getMessage()).contains("not allowed for this equipment type"));
    }


    @Test
    void getEquipmentStatsWithoutFiltersReturnsStats() {
        EquipmentStatsProjection projection = statsProjection(50L, 9L, 3L, 1L);

        when(repository.getEquipmentStats(
                null,
                null,
                null,
                null,
                EquipmentStatus.ACTIVE,
                EquipmentStatus.IN_REPAIR,
                EquipmentStatus.DECOMMISSIONED
        )).thenReturn(projection);

        EquipmentStatsResponse result = service.getEquipmentStats(null, null, null, null);

        assertThat(result.totalInRegistry()).isEqualTo(50);
        assertThat(result.active()).isEqualTo(9);
        assertThat(result.inRepair()).isEqualTo(3);
        assertThat(result.decommissioned()).isEqualTo(1);

        verify(repository).getEquipmentStats(
                null,
                null,
                null,
                null,
                EquipmentStatus.ACTIVE,
                EquipmentStatus.IN_REPAIR,
                EquipmentStatus.DECOMMISSIONED
        );
    }

    @Test
    void getEquipmentStatsWithFiltersPassesNormalizedSearchPatternAndEnums() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();

        EquipmentStatsProjection projection = statsProjection(12L, 8L, 2L, 2L);

        when(repository.getEquipmentStats(
                "%pump-42%",
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                departmentId,
                equipmentTypeId,
                EquipmentStatus.ACTIVE,
                EquipmentStatus.IN_REPAIR,
                EquipmentStatus.DECOMMISSIONED
        )).thenReturn(projection);

        EquipmentStatsResponse result = service.getEquipmentStats(
                "  PuMp-42  ",
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                departmentId,
                equipmentTypeId
        );

        assertThat(result.totalInRegistry()).isEqualTo(12);
        assertThat(result.active()).isEqualTo(8);
        assertThat(result.inRepair()).isEqualTo(2);
        assertThat(result.decommissioned()).isEqualTo(2);

        verify(repository).getEquipmentStats(
                "%pump-42%",
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                departmentId,
                equipmentTypeId,
                EquipmentStatus.ACTIVE,
                EquipmentStatus.IN_REPAIR,
                EquipmentStatus.DECOMMISSIONED
        );
    }

    @Test
    void getEquipmentStatsWithBlankSearchPassesNullPattern() {
        EquipmentStatsProjection projection = statsProjection(10L, 7L, 2L, 1L);

        when(repository.getEquipmentStats(
                null,
                null,
                null,
                null,
                EquipmentStatus.ACTIVE,
                EquipmentStatus.IN_REPAIR,
                EquipmentStatus.DECOMMISSIONED
        )).thenReturn(projection);

        EquipmentStatsResponse result = service.getEquipmentStats("   ", null, null, null);

        assertThat(result.totalInRegistry()).isEqualTo(10);
        assertThat(result.active()).isEqualTo(7);
        assertThat(result.inRepair()).isEqualTo(2);
        assertThat(result.decommissioned()).isEqualTo(1);

        verify(repository).getEquipmentStats(
                null,
                null,
                null,
                null,
                EquipmentStatus.ACTIVE,
                EquipmentStatus.IN_REPAIR,
                EquipmentStatus.DECOMMISSIONED
        );
    }

    @Test
    void getEquipmentStatsMapsNullProjectionValuesToZero() {
        EquipmentStatsProjection projection = statsProjection(null, null, null, null);

        when(repository.getEquipmentStats(
                null,
                null,
                null,
                null,
                EquipmentStatus.ACTIVE,
                EquipmentStatus.IN_REPAIR,
                EquipmentStatus.DECOMMISSIONED
        )).thenReturn(projection);

        EquipmentStatsResponse result = service.getEquipmentStats(null, null, null, null);

        assertThat(result.totalInRegistry()).isZero();
        assertThat(result.active()).isZero();
        assertThat(result.inRepair()).isZero();
        assertThat(result.decommissioned()).isZero();
    }

    private EquipmentStatsProjection statsProjection(
            Long totalInRegistry,
            Long active,
            Long inRepair,
            Long decommissioned
    ) {
        return new EquipmentStatsProjection() {
            @Override
            public Long getTotalInRegistry() {
                return totalInRegistry;
            }

            @Override
            public Long getActive() {
                return active;
            }

            @Override
            public Long getInRepair() {
                return inRepair;
            }

            @Override
            public Long getDecommissioned() {
                return decommissioned;
            }
        };
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
        return createRequest(code, inventoryNumber, departmentId, warehouseId, 10_000L);
    }

    private EquipmentCreateRequest createRequest(
            String code,
            String inventoryNumber,
            UUID departmentId,
            UUID warehouseId,
            Long averageOperatingLifeHours
    ) {
        return createRequest(code, inventoryNumber, departmentId, warehouseId, averageOperatingLifeHours, null);
    }

    private EquipmentCreateRequest createRequest(
            String code,
            String inventoryNumber,
            UUID departmentId,
            UUID warehouseId,
            Long averageOperatingLifeHours,
            List<EquipmentAttributeValueRequest> attributes
    ) {
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
                "test",
                averageOperatingLifeHours,
                attributes
        );
    }

    private EquipmentCreateRequest createRequestWithManualAttributes(List<EquipmentManualAttributeRequest> manualAttributes) {
        return new EquipmentCreateRequest(
                null,
                "Compressor",
                "INV-MANUAL-DISABLED",
                "TN-1",
                "SN-1",
                "Model X",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                "ACME",
                EquipmentStatus.ACTIVE,
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                null,
                null,
                "test",
                10_000L,
                null,
                manualAttributes
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

    private FileAsset fileAsset(
            UUID id,
            String fileName,
            String originalName,
            String mimeType,
            long sizeBytes
    ) {
        FileAsset fileAsset = new FileAsset();
        fileAsset.setId(id);
        fileAsset.setFileName(fileName);
        fileAsset.setOriginalName(originalName);
        fileAsset.setMimeType(mimeType);
        fileAsset.setSizeBytes(sizeBytes);
        fileAsset.setStoragePath("/tmp/" + fileName);
        fileAsset.setDeleted(false);
        return fileAsset;
    }

    private WarehouseEquipmentItem activeWarehouseItem(UUID equipmentId, UUID warehouseId, WarehouseEquipmentStatus status) {
        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setEquipmentId(equipmentId);
        item.setWarehouseId(warehouseId);
        item.setStatus(status);
        item.setActive(true);
        item.setDeleted(false);
        return item;
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

    private EquipmentUpdateRequest updateRequestWithTypeAndAttributes(
            UUID equipmentTypeId,
            List<EquipmentAttributeValueRequest> attributes
    ) {
        return new EquipmentUpdateRequest(
                null,
                "Compressor Updated",
                null,
                null,
                null,
                null,
                equipmentTypeId,
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
                attributes
        );
    }

    private EquipmentUpdateRequest updateRequestWithManualAttributes(List<EquipmentManualAttributeRequest> manualAttributes) {
        return new EquipmentUpdateRequest(
                null,
                "Compressor Updated",
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
                null,
                null,
                manualAttributes
        );
    }

    private void stubEnrichment() {
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        when(locationRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        when(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        when(repository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        when(passportRepository.findAllByEquipmentIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        when(warehouseEquipmentItemRepository.findActiveByEquipmentIds(anyCollection())).thenReturn(List.of());
    }

    private void stubWarehouseLocationFallback(List<Warehouse> warehouses) {
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(warehouses);
    }
}
