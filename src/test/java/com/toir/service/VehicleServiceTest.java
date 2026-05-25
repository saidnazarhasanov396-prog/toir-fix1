package com.toir.service;

import com.toir.dto.equipment.EquipmentDto;
import com.toir.dto.file.PresignedUrlResponse;
import com.toir.dto.file.UploadFileResponse;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueRequest;
import com.toir.dto.vehicle.VehicleDetailDto;
import com.toir.dto.vehicle.VehicleRequest;
import com.toir.dto.vehicle.VehicleStatsResponse;
import com.toir.dto.vehicle.VehicleSummaryDto;
import com.toir.entity.UploadedFile;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.FileCategory;
import com.toir.enums.VehicleType;
import com.toir.exception.RestException;
import com.toir.repository.UploadedFileRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.repository.projection.VehicleStatsProjection;
import com.toir.security.AuthenticatedUser;
import com.toir.security.SecurityScope;
import com.toir.service.equipment.EquipmentService;
import com.toir.service.equipment.EquipmentAttributeService;
import com.toir.service.file_management.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    VehicleDetailsRepository vehicleDetailsRepository;

    @Mock
    EquipmentService equipmentService;

    @Mock
    EquipmentAttributeService equipmentAttributeService;

    @InjectMocks
    VehicleService service;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    FileService fileService;

    @Mock
    UploadedFileRepository uploadedFileRepository;

    @Mock
    SecurityScope securityScope;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "auditBuilderService", auditBuilderService);
    }

    @Test
    void createVehicleCreatesEquipmentWithVehicleCategoryAndDetails() {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        VehicleRequest request = new VehicleRequest(
                "VH-001",
                "Truck 001",
                "INV-VH-001",
                "TN-VH-001",
                "SER-VH-001",
                equipmentTypeId,
                departmentId,
                null,
                EquipmentStatus.ACTIVE,
                "01A123AA",
                "VIN123456789",
                "MAN",
                "TGS",
                2022,
                VehicleType.TRUCK,
                null,
                null,
                null,
                "DIESEL",
                400.0,
                12000.0,
                2,
                null,
                1000.0,
                25.0,
                "REG-001",
                "INS-001",
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 10, 31),
                "GPS-001"
        );

        when(equipmentRepository.existsByCodeAndIsDeletedFalse("VH-001")).thenReturn(false);
        when(equipmentRepository.existsByInventoryNumberAndIsDeletedFalse("INV-VH-001")).thenReturn(false);
        when(vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse("01A123AA")).thenReturn(false);
        when(vehicleDetailsRepository.existsByVinAndIsDeletedFalse("VIN123456789")).thenReturn(false);
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> {
            Equipment equipment = invocation.getArgument(0);
            equipment.setId(UUID.randomUUID());
            return equipment;
        });
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> {
            VehicleDetails details = invocation.getArgument(0);
            details.setId(UUID.randomUUID());
            return details;
        });
        when(equipmentService.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return EquipmentDto.from(equipment(id, "VH-001", "Truck 001", "INV-VH-001"));
        });

        VehicleDetailDto result = service.create(request);

        assertThat(result.equipment().category()).isEqualTo(EquipmentCategory.VEHICLE);
        assertThat(result.vehicleDetails().plateNumber()).isEqualTo("01A123AA");
    }

    @Test
    void createVehicleAcceptsDynamicMetricAttribute() {
        VehicleRequest request = fullRequest("VH-ATTR-001", "Truck Attr", "INV-VH-ATTR-001", "01A101AA", null)
                .withAttributes(List.of(new EquipmentAttributeValueRequest(null, "payload_capacity", null, 12000.0, null, null, null, null)));

        when(equipmentRepository.existsByCodeAndIsDeletedFalse("VH-ATTR-001")).thenReturn(false);
        when(equipmentRepository.existsByInventoryNumberAndIsDeletedFalse("INV-VH-ATTR-001")).thenReturn(false);
        when(vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse("01A101AA")).thenReturn(false);
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> {
            Equipment equipment = invocation.getArgument(0);
            equipment.setId(UUID.randomUUID());
            return equipment;
        });
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(any(UUID.class))).thenAnswer(invocation ->
                EquipmentDto.from(equipment(invocation.getArgument(0), "VH-ATTR-001", "Truck Attr", "INV-VH-ATTR-001")));

        service.create(request);

        verify(equipmentAttributeService).upsertValues(any(Equipment.class), eq(request.attributes()));
    }

    @Test
    void createVehicleWithMissingRequiredDynamicMetricFails() {
        VehicleRequest request = fullRequest("VH-ATTR-002", "Truck Attr Missing", "INV-VH-ATTR-002", "01A102AA", null);

        when(equipmentRepository.existsByCodeAndIsDeletedFalse("VH-ATTR-002")).thenReturn(false);
        when(equipmentRepository.existsByInventoryNumberAndIsDeletedFalse("INV-VH-ATTR-002")).thenReturn(false);
        when(vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse("01A102AA")).thenReturn(false);
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> {
            Equipment equipment = invocation.getArgument(0);
            equipment.setId(UUID.randomUUID());
            return equipment;
        });
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(RestException.badRequest("Missing required equipment attributes: payload_capacity (required by equipment type)"))
                .when(equipmentAttributeService).upsertValues(any(Equipment.class), eq(List.of()));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getMessage()).contains("Missing required equipment attributes"));
    }

    @Test
    void createVehicleRejectsDuplicatePlateNumber() {
        VehicleRequest request = VehicleRequest.minimal(
                "VH-002",
                "Truck 002",
                "INV-VH-002",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "01A999AA",
                VehicleType.TRUCK
        );

        when(equipmentRepository.existsByCodeAndIsDeletedFalse("VH-002")).thenReturn(false);
        when(equipmentRepository.existsByInventoryNumberAndIsDeletedFalse("INV-VH-002")).thenReturn(false);
        when(vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse("01A999AA")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Vehicle plate number already exists");
    }

    @Test
    void createVehicleAllowsSoftDeletedPlateAndVinReuseWhenActiveLookupsDoNotFindThem() {
        VehicleRequest request = fullRequest("VH-003", "Truck 003", "INV-VH-003", "01A777AA", "VIN-REUSED");

        when(equipmentRepository.existsByCodeAndIsDeletedFalse("VH-003")).thenReturn(false);
        when(equipmentRepository.existsByInventoryNumberAndIsDeletedFalse("INV-VH-003")).thenReturn(false);
        when(vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse("01A777AA")).thenReturn(false);
        when(vehicleDetailsRepository.existsByVinAndIsDeletedFalse("VIN-REUSED")).thenReturn(false);
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> {
            Equipment equipment = invocation.getArgument(0);
            equipment.setId(UUID.randomUUID());
            return equipment;
        });
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> {
            VehicleDetails details = invocation.getArgument(0);
            details.setId(UUID.randomUUID());
            return details;
        });
        when(equipmentService.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return EquipmentDto.from(equipment(id, "VH-003", "Truck 003", "INV-VH-003"));
        });

        VehicleDetailDto result = service.create(request);

        assertThat(result.vehicleDetails().plateNumber()).isEqualTo("01A777AA");
        assertThat(result.vehicleDetails().vin()).isEqualTo("VIN-REUSED");
    }

    @Test
    void createVehicleNormalizesBlankVinToNull() {
        VehicleRequest request = fullRequest("VH-004", "Truck 004", "INV-VH-004", "01A444AA", "   ");

        when(equipmentRepository.existsByCodeAndIsDeletedFalse("VH-004")).thenReturn(false);
        when(equipmentRepository.existsByInventoryNumberAndIsDeletedFalse("INV-VH-004")).thenReturn(false);
        when(vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse("01A444AA")).thenReturn(false);
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> {
            Equipment equipment = invocation.getArgument(0);
            equipment.setId(UUID.randomUUID());
            return equipment;
        });
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> {
            VehicleDetails details = invocation.getArgument(0);
            details.setId(UUID.randomUUID());
            return details;
        });
        when(equipmentService.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return EquipmentDto.from(equipment(id, "VH-004", "Truck 004", "INV-VH-004"));
        });

        VehicleDetailDto result = service.create(request);

        ArgumentCaptor<VehicleDetails> detailsCaptor = ArgumentCaptor.forClass(VehicleDetails.class);
        verify(vehicleDetailsRepository).save(detailsCaptor.capture());
        assertThat(detailsCaptor.getValue().getVin()).isNull();
        assertThat(result.vehicleDetails().vin()).isNull();
        verify(vehicleDetailsRepository, never()).existsByVinAndIsDeletedFalse(any());
    }

    @Test
    void updateVehicleRejectsDuplicateEquipmentCodeFromAnotherActiveRow() {
        UUID equipmentId = UUID.randomUUID();
        UUID otherEquipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-010", "Truck 010", "INV-VH-010");
        Equipment duplicateEquipment = equipment(otherEquipmentId, "VH-011", "Truck 011", "INV-VH-011");
        VehicleDetails details = details(equipmentId, "01A010AA", "VIN-010");
        VehicleRequest request = fullRequest("VH-011", "Truck 010", "INV-VH-010", "01A010AA", "VIN-010");

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(equipmentRepository.findByCodeAndIsDeletedFalse("VH-011")).thenReturn(Optional.of(duplicateEquipment));

        assertThatThrownBy(() -> service.update(equipmentId, request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Equipment code already exists");
    }

    @Test
    void updateVehicleRejectsDuplicateInventoryNumberFromAnotherActiveRow() {
        UUID equipmentId = UUID.randomUUID();
        UUID otherEquipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-010", "Truck 010", "INV-VH-010");
        Equipment duplicateEquipment = equipment(otherEquipmentId, "VH-011", "Truck 011", "INV-VH-011");
        VehicleDetails details = details(equipmentId, "01A010AA", "VIN-010");
        VehicleRequest request = fullRequest("VH-010", "Truck 010", "INV-VH-011", "01A010AA", "VIN-010");

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(equipmentRepository.findByInventoryNumberAndIsDeletedFalse("INV-VH-011")).thenReturn(Optional.of(duplicateEquipment));

        assertThatThrownBy(() -> service.update(equipmentId, request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Inventory number already exists");
    }

    @Test
    void updateVehicleRejectsDuplicatePlateNumberFromAnotherVehicle() {
        UUID equipmentId = UUID.randomUUID();
        UUID otherEquipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-010", "Truck 010", "INV-VH-010");
        VehicleDetails details = details(equipmentId, "01A010AA", "VIN-010");
        VehicleDetails duplicateDetails = details(otherEquipmentId, "01A011AA", "VIN-011");
        VehicleRequest request = fullRequest("VH-010", "Truck 010", "INV-VH-010", "01A011AA", "VIN-010");

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(vehicleDetailsRepository.findByPlateNumberAndIsDeletedFalse("01A011AA")).thenReturn(Optional.of(duplicateDetails));

        assertThatThrownBy(() -> service.update(equipmentId, request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Vehicle plate number already exists");
    }

    @Test
    void updateVehicleRejectsDuplicateVinFromAnotherVehicle() {
        UUID equipmentId = UUID.randomUUID();
        UUID otherEquipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-010", "Truck 010", "INV-VH-010");
        VehicleDetails details = details(equipmentId, "01A010AA", "VIN-010");
        VehicleDetails duplicateDetails = details(otherEquipmentId, "01A011AA", "VIN-011");
        VehicleRequest request = fullRequest("VH-010", "Truck 010", "INV-VH-010", "01A010AA", "VIN-011");

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(vehicleDetailsRepository.findByVinAndIsDeletedFalse("VIN-011")).thenReturn(Optional.of(duplicateDetails));

        assertThatThrownBy(() -> service.update(equipmentId, request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Vehicle VIN already exists");
    }

    @Test
    void updateVehicleAllowsSoftDeletedUniqueValuesWhenActiveLookupsDoNotFindThem() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-040", "Truck 040", "INV-VH-040");
        VehicleDetails details = details(equipmentId, "01A040AA", "VIN-040");
        VehicleRequest request = fullRequest("VH-041", "Truck 041", "INV-VH-041", "01A041AA", "VIN-041");
        Equipment updated = updatedEquipment(equipmentId, request);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(equipmentRepository.findByCodeAndIsDeletedFalse("VH-041")).thenReturn(Optional.empty());
        when(equipmentRepository.findByInventoryNumberAndIsDeletedFalse("INV-VH-041")).thenReturn(Optional.empty());
        when(vehicleDetailsRepository.findByPlateNumberAndIsDeletedFalse("01A041AA")).thenReturn(Optional.empty());
        when(vehicleDetailsRepository.findByVinAndIsDeletedFalse("VIN-041")).thenReturn(Optional.empty());
        when(equipmentRepository.save(any(Equipment.class))).thenReturn(updated);
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(equipmentId)).thenReturn(EquipmentDto.from(updated));

        VehicleDetailDto result = service.update(equipmentId, request);

        assertThat(result.equipment().code()).isEqualTo("VH-041");
        assertThat(result.equipment().inventoryNumber()).isEqualTo("INV-VH-041");
        assertThat(result.vehicleDetails().plateNumber()).isEqualTo("01A041AA");
        assertThat(result.vehicleDetails().vin()).isEqualTo("VIN-041");
    }

    @Test
    void updateVehicleNormalizesBlankVinToNull() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-050", "Truck 050", "INV-VH-050");
        VehicleDetails details = details(equipmentId, "01A050AA", "VIN-050");
        VehicleRequest request = fullRequest("VH-050", "Truck 050", "INV-VH-050", "01A050AA", " ");
        Equipment updated = updatedEquipment(equipmentId, request);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(equipmentRepository.save(any(Equipment.class))).thenReturn(updated);
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(equipmentId)).thenReturn(EquipmentDto.from(updated));

        VehicleDetailDto result = service.update(equipmentId, request);

        assertThat(details.getVin()).isNull();
        assertThat(result.vehicleDetails().vin()).isNull();
        verify(vehicleDetailsRepository, never()).findByVinAndIsDeletedFalse(any());
    }

    @Test
    void updateVehicleDoesNotSelfConflictOnUnchangedUniqueFields() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-020", "Truck 020", "INV-VH-020");
        VehicleDetails details = details(equipmentId, "01A020AA", "VIN-020");
        VehicleRequest request = fullRequest("VH-020", "Truck 020 Updated", "INV-VH-020", "01A020AA", "VIN-020");
        Equipment updated = updatedEquipment(equipmentId, request);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(equipmentRepository.save(any(Equipment.class))).thenReturn(updated);
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(equipmentId)).thenReturn(EquipmentDto.from(updated));

        VehicleDetailDto result = service.update(equipmentId, request);

        assertThat(result.equipment().name()).isEqualTo("Truck 020 Updated");
        assertThat(result.vehicleDetails().plateNumber()).isEqualTo("01A020AA");
        verify(equipmentRepository, never()).findByCodeAndIsDeletedFalse("VH-020");
        verify(equipmentRepository, never()).findByInventoryNumberAndIsDeletedFalse("INV-VH-020");
        verify(vehicleDetailsRepository, never()).findByPlateNumberAndIsDeletedFalse("01A020AA");
        verify(vehicleDetailsRepository, never()).findByVinAndIsDeletedFalse("VIN-020");
    }

    @Test
    void updateVehicleChangesDynamicMetricAttribute() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-ATTR-003", "Truck Attr Update", "INV-VH-ATTR-003");
        equipment.setCategory(EquipmentCategory.VEHICLE);
        VehicleDetails details = details(equipmentId, "01A103AA", null);
        VehicleRequest request = fullRequest("VH-ATTR-003", "Truck Attr Update", "INV-VH-ATTR-003", "01A103AA", null)
                .withAttributes(List.of(new EquipmentAttributeValueRequest(null, "payload_capacity", null, 14000.0, null, null, null, null)));

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(equipmentId)).thenReturn(EquipmentDto.from(equipment));

        service.update(equipmentId, request);

        verify(equipmentAttributeService).upsertValues(eq(equipment), eq(request.attributes()));
    }

    @Test
    void listSkipsVehicleEquipmentRowsWithoutDetails() {
        UUID completeId = UUID.randomUUID();
        UUID incompleteId = UUID.randomUUID();
        Equipment complete = equipment(completeId, "VH-030", "Truck 030", "INV-VH-030");
        Equipment incomplete = equipment(incompleteId, "VH-031", "Truck 031", "INV-VH-031");
        VehicleDetails completeDetails = details(completeId, "01A030AA", "VIN-030");
        PageRequest pageRequest = PageRequest.of(0, 20);

        Page<Equipment> equipmentPage = new PageImpl<>(List.of(complete, incomplete), pageRequest, 2);
        Page<EquipmentDto> enrichedEquipmentPage = new PageImpl<>(
                List.of(EquipmentDto.from(complete), EquipmentDto.from(incomplete)),
                pageRequest,
                2
        );

        when(vehicleDetailsRepository.searchVehicleEquipment(
                isNull(),
                isNull(),
                eq(EquipmentCategory.VEHICLE),
                isNull(),
                eq(pageRequest)
        )).thenReturn(equipmentPage);

        when(equipmentService.enrich(equipmentPage)).thenReturn(enrichedEquipmentPage);
        when(vehicleDetailsRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(completeId, incompleteId)))
                .thenReturn(List.of(completeDetails));

        Page<VehicleSummaryDto> result = service.list(null, null, null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().equipmentId()).isEqualTo(completeId);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void getStatsWithoutFiltersReturnsVehicleStats() {
        VehicleStatsProjection projection = statsProjection(11L, 9L, 1L, 1L);

        when(vehicleDetailsRepository.getVehicleStats(
                null,
                EquipmentCategory.VEHICLE,
                null,
                EquipmentStatus.ACTIVE,
                EquipmentStatus.IN_REPAIR,
                EquipmentStatus.OUT_OF_SERVICE
        )).thenReturn(projection);

        VehicleStatsResponse result = service.getStats(null, null);

        assertThat(result.total()).isEqualTo(11);
        assertThat(result.active()).isEqualTo(9);
        assertThat(result.inRepair()).isEqualTo(1);
        assertThat(result.outOfService()).isEqualTo(1);

        verify(vehicleDetailsRepository).getVehicleStats(
                null,
                EquipmentCategory.VEHICLE,
                null,
                EquipmentStatus.ACTIVE,
                EquipmentStatus.IN_REPAIR,
                EquipmentStatus.OUT_OF_SERVICE
        );
    }

    @Test
    void getStatsWithFiltersPassesDepartmentAndNormalizedSearchPattern() {
        UUID departmentId = UUID.randomUUID();

        VehicleStatsProjection projection = statsProjection(4L, 3L, 1L, 0L);

        when(vehicleDetailsRepository.getVehicleStats(
                departmentId,
                EquipmentCategory.VEHICLE,
                "%kamaz%",
                EquipmentStatus.ACTIVE,
                EquipmentStatus.IN_REPAIR,
                EquipmentStatus.OUT_OF_SERVICE
        )).thenReturn(projection);

        VehicleStatsResponse result = service.getStats(departmentId, "  KaMaZ  ");

        assertThat(result.total()).isEqualTo(4);
        assertThat(result.active()).isEqualTo(3);
        assertThat(result.inRepair()).isEqualTo(1);
        assertThat(result.outOfService()).isZero();

        verify(vehicleDetailsRepository).getVehicleStats(
                departmentId,
                EquipmentCategory.VEHICLE,
                "%kamaz%",
                EquipmentStatus.ACTIVE,
                EquipmentStatus.IN_REPAIR,
                EquipmentStatus.OUT_OF_SERVICE
        );
    }

    @Test
    void getStatsWithBlankSearchPassesNullSearchPattern() {
        VehicleStatsProjection projection = statsProjection(7L, 6L, 0L, 1L);

        when(vehicleDetailsRepository.getVehicleStats(
                null,
                EquipmentCategory.VEHICLE,
                null,
                EquipmentStatus.ACTIVE,
                EquipmentStatus.IN_REPAIR,
                EquipmentStatus.OUT_OF_SERVICE
        )).thenReturn(projection);

        VehicleStatsResponse result = service.getStats(null, "   ");

        assertThat(result.total()).isEqualTo(7);
        assertThat(result.active()).isEqualTo(6);
        assertThat(result.inRepair()).isZero();
        assertThat(result.outOfService()).isEqualTo(1);

        verify(vehicleDetailsRepository).getVehicleStats(
                null,
                EquipmentCategory.VEHICLE,
                null,
                EquipmentStatus.ACTIVE,
                EquipmentStatus.IN_REPAIR,
                EquipmentStatus.OUT_OF_SERVICE
        );
    }

    @Test
    void getStatsMapsNullProjectionValuesToZero() {
        VehicleStatsProjection projection = statsProjection(null, null, null, null);

        when(vehicleDetailsRepository.getVehicleStats(
                null,
                EquipmentCategory.VEHICLE,
                null,
                EquipmentStatus.ACTIVE,
                EquipmentStatus.IN_REPAIR,
                EquipmentStatus.OUT_OF_SERVICE
        )).thenReturn(projection);

        VehicleStatsResponse result = service.getStats(null, null);

        assertThat(result.total()).isZero();
        assertThat(result.active()).isZero();
        assertThat(result.inRepair()).isZero();
        assertThat(result.outOfService()).isZero();
    }

    @Test
    void attachDocumentUploadsFileAndSavesRelation() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        VehicleDetails details = details(equipmentId, "01A001AA", "VIN-DOC");
        UploadedFile uploadedFile = uploadedFile(fileId, currentUserId);
        MockMultipartFile document = document();

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(fileService.upload(document, FileCategory.VEHICLE_DOCUMENT, currentUserId)).thenReturn(uploadResponse(fileId));
        when(uploadedFileRepository.findByIdAndDeletedFalse(fileId)).thenReturn(Optional.of(uploadedFile));
        when(vehicleDetailsRepository.save(details)).thenReturn(details);
        when(equipmentService.findById(equipmentId)).thenReturn(EquipmentDto.from(equipment));

        VehicleDetailDto result = service.attachDocument(equipmentId, document, currentUserId);

        assertThat(details.getDocumentFile()).isEqualTo(uploadedFile);
        assertThat(result.vehicleDetails().document().id()).isEqualTo(fileId);
    }

    @Test
    void attachDocumentToMissingVehicleReturnsNotFound() {
        UUID equipmentId = UUID.randomUUID();

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.attachDocument(equipmentId, document(), UUID.randomUUID()))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Equipment not found");

        verify(fileService, never()).upload(any(), any(), any());
    }

    @Test
    void attachDocumentRejectsUnauthorizedVehicleDepartment() {
        UUID equipmentId = UUID.randomUUID();
        UUID userDepartmentId = UUID.randomUUID();
        UUID vehicleDepartmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        equipment.setDepartmentId(vehicleDepartmentId);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(securityScope.isAdmin()).thenReturn(false);
        when(securityScope.currentUser()).thenReturn(authenticatedUser(UUID.randomUUID(), userDepartmentId));

        assertThatThrownBy(() -> service.attachDocument(equipmentId, document(), UUID.randomUUID()))
                .isInstanceOf(RestException.class)
                .hasMessage("Vehicle access denied");

        verify(fileService, never()).upload(any(), any(), any());
    }

    @Test
    void attachDocumentRollsBackUploadedFileIfVehicleSaveFails() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        VehicleDetails details = details(equipmentId, "01A001AA", "VIN-DOC");
        MockMultipartFile document = document();

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(fileService.upload(document, FileCategory.VEHICLE_DOCUMENT, currentUserId)).thenReturn(uploadResponse(fileId));
        when(uploadedFileRepository.findByIdAndDeletedFalse(fileId)).thenReturn(Optional.of(uploadedFile(fileId, currentUserId)));
        when(vehicleDetailsRepository.save(details)).thenThrow(new RuntimeException("db"));

        assertThatThrownBy(() -> service.attachDocument(equipmentId, document, currentUserId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db");

        verify(fileService).delete(fileId, currentUserId);
    }

    @Test
    void attachDocumentRollsBackUploadedFileIfMetadataLookupFails() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        VehicleDetails details = details(equipmentId, "01A001AA", "VIN-DOC");
        MockMultipartFile document = document();

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(fileService.upload(document, FileCategory.VEHICLE_DOCUMENT, currentUserId)).thenReturn(uploadResponse(fileId));
        when(uploadedFileRepository.findByIdAndDeletedFalse(fileId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.attachDocument(equipmentId, document, currentUserId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Uploaded file not found");

        verify(fileService).delete(fileId, currentUserId);
    }

    @Test
    void attachDocumentReplacesOldFileAfterSavingNewRelation() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID oldFileId = UUID.randomUUID();
        UUID newFileId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        VehicleDetails details = details(equipmentId, "01A001AA", "VIN-DOC");
        details.setDocumentFile(uploadedFile(oldFileId, currentUserId));
        UploadedFile newFile = uploadedFile(newFileId, currentUserId);
        MockMultipartFile document = document();

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(fileService.upload(document, FileCategory.VEHICLE_DOCUMENT, currentUserId)).thenReturn(uploadResponse(newFileId));
        when(uploadedFileRepository.findByIdAndDeletedFalse(newFileId)).thenReturn(Optional.of(newFile));
        when(vehicleDetailsRepository.save(details)).thenReturn(details);
        when(equipmentService.findById(equipmentId)).thenReturn(EquipmentDto.from(equipment));

        service.attachDocument(equipmentId, document, currentUserId);

        assertThat(details.getDocumentFile()).isEqualTo(newFile);
        verify(fileService).delete(oldFileId, currentUserId);
    }

    @Test
    void attachDocumentReplacementFailureKeepsOldDocumentAndDeletesNewFile() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID oldFileId = UUID.randomUUID();
        UUID newFileId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        VehicleDetails details = details(equipmentId, "01A001AA", "VIN-DOC");
        UploadedFile oldFile = uploadedFile(oldFileId, currentUserId);
        details.setDocumentFile(oldFile);
        MockMultipartFile document = document();

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(fileService.upload(document, FileCategory.VEHICLE_DOCUMENT, currentUserId)).thenReturn(uploadResponse(newFileId));
        when(uploadedFileRepository.findByIdAndDeletedFalse(newFileId)).thenReturn(Optional.of(uploadedFile(newFileId, currentUserId)));
        when(vehicleDetailsRepository.save(details)).thenThrow(new RuntimeException("db"));

        assertThatThrownBy(() -> service.attachDocument(equipmentId, document, currentUserId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db");

        assertThat(details.getDocumentFile()).isEqualTo(oldFile);
        verify(fileService).delete(newFileId, currentUserId);
        verify(fileService, never()).delete(oldFileId, currentUserId);
    }

    @Test
    void getDocumentReturnsMetadataWhenAttached() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        VehicleDetails details = details(equipmentId, "01A001AA", "VIN-DOC");
        details.setDocumentFile(uploadedFile(fileId, currentUserId));

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));

        VehicleDetailDto.DocumentRef result = service.getDocument(equipmentId, currentUserId);

        assertThat(result.id()).isEqualTo(fileId);
        verify(fileService).getMetadata(fileId, currentUserId);
    }

    @Test
    void getDocumentWithoutAttachedFileReturnsNotFound() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        VehicleDetails details = details(equipmentId, "01A001AA", "VIN-DOC");

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));

        assertThatThrownBy(() -> service.getDocument(equipmentId, UUID.randomUUID()))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Vehicle document not found");
    }

    @Test
    void getDocumentRejectsNonOwnerThroughFileService() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        VehicleDetails details = details(equipmentId, "01A001AA", "VIN-DOC");
        details.setDocumentFile(uploadedFile(fileId, UUID.randomUUID()));

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(fileService.getMetadata(fileId, currentUserId)).thenThrow(RestException.forbidden("File access denied"));

        assertThatThrownBy(() -> service.getDocument(equipmentId, currentUserId))
                .isInstanceOf(RestException.class)
                .hasMessage("File access denied");
    }

    @Test
    void getDocumentPresignedUrlUnauthorizedPropagatesForbidden() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        VehicleDetails details = details(equipmentId, "01A001AA", "VIN-DOC");
        details.setDocumentFile(uploadedFile(fileId, UUID.randomUUID()));

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(fileService.getPresignedUrl(fileId, currentUserId)).thenThrow(RestException.forbidden("File access denied"));

        assertThatThrownBy(() -> service.getDocumentPresignedUrl(equipmentId, currentUserId))
                .isInstanceOf(RestException.class)
                .hasMessage("File access denied");
    }

    @Test
    void deleteDocumentDetachesAndSoftDeletesFile() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        VehicleDetails details = details(equipmentId, "01A001AA", "VIN-DOC");
        details.setDocumentFile(uploadedFile(fileId, currentUserId));

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(vehicleDetailsRepository.save(details)).thenReturn(details);

        service.deleteDocument(equipmentId, currentUserId);

        assertThat(details.getDocumentFile()).isNull();
        verify(fileService).delete(fileId, currentUserId);
    }

    @Test
    void deleteDocumentWithoutAttachedFileReturnsNotFound() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        VehicleDetails details = details(equipmentId, "01A001AA", "VIN-DOC");

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));

        assertThatThrownBy(() -> service.deleteDocument(equipmentId, UUID.randomUUID()))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Vehicle document not found");
    }

    @Test
    void getDocumentPresignedUrlDelegatesAfterVehicleLookup() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        VehicleDetails details = details(equipmentId, "01A001AA", "VIN-DOC");
        details.setDocumentFile(uploadedFile(fileId, currentUserId));
        PresignedUrlResponse response = PresignedUrlResponse.builder()
                .fileId(fileId)
                .url("http://signed")
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(fileService.getPresignedUrl(fileId, currentUserId)).thenReturn(response);

        assertThat(service.getDocumentPresignedUrl(equipmentId, currentUserId)).isEqualTo(response);
    }

    private VehicleStatsProjection statsProjection(
            Long total,
            Long active,
            Long inRepair,
            Long outOfService
    ) {
        return new VehicleStatsProjection() {
            @Override
            public Long getTotal() {
                return total;
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
            public Long getOutOfService() {
                return outOfService;
            }
        };
    }



    private static VehicleRequest fullRequest(String code, String name, String inventoryNumber, String plateNumber, String vin) {
        return new VehicleRequest(
                code,
                name,
                inventoryNumber,
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                EquipmentStatus.ACTIVE,
                plateNumber,
                vin,
                "MAN",
                "TGS",
                2022,
                VehicleType.TRUCK,
                null,
                null,
                null,
                "DIESEL",
                null,
                null,
                null,
                null,
                0.0,
                0.0,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static Equipment equipment(UUID id, String code, String name, String inventoryNumber) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode(code);
        equipment.setName(name);
        equipment.setInventoryNumber(inventoryNumber);
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(UUID.randomUUID());
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.VEHICLE);
        return equipment;
    }

    private static VehicleDetails details(UUID equipmentId, String plateNumber, String vin) {
        VehicleDetails details = new VehicleDetails();
        details.setId(UUID.randomUUID());
        details.setEquipmentId(equipmentId);
        details.setPlateNumber(plateNumber);
        details.setVin(vin);
        details.setVehicleType(VehicleType.TRUCK);
        return details;
    }

    private static UploadedFile uploadedFile(UUID id, UUID uploadedBy) {
        return UploadedFile.builder()
                .id(id)
                .originalName("vehicle-passport.pdf")
                .storedName(id + ".pdf")
                .contentType("application/pdf")
                .extension("pdf")
                .size(123L)
                .uploadedBy(uploadedBy)
                .category(FileCategory.VEHICLE_DOCUMENT)
                .deleted(false)
                .objectName("vehicle-documents/2026/05/" + id + ".pdf")
                .build();
    }

    private static UploadFileResponse uploadResponse(UUID fileId) {
        return UploadFileResponse.builder()
                .id(fileId)
                .originalName("vehicle-passport.pdf")
                .storedName(fileId + ".pdf")
                .contentType("application/pdf")
                .extension("pdf")
                .size(123L)
                .category(FileCategory.VEHICLE_DOCUMENT)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static MockMultipartFile document() {
        return new MockMultipartFile("document", "vehicle-passport.pdf", "application/pdf", "%PDF-1.4\n".getBytes());
    }

    private static AuthenticatedUser authenticatedUser(UUID userId, UUID departmentId) {
        return new AuthenticatedUser(
                userId.toString(),
                "user",
                "user@example.com",
                "User",
                departmentId.toString(),
                "USER",
                List.of()
        );
    }
    private static Equipment updatedEquipment(UUID id, VehicleRequest request) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode(request.code());
        equipment.setName(request.name());
        equipment.setInventoryNumber(request.inventoryNumber());
        equipment.setEquipmentTypeId(request.equipmentTypeId());
        equipment.setDepartmentId(request.departmentId());
        equipment.setLocationId(request.locationId());
        equipment.setStatus(request.status());
        equipment.setCategory(EquipmentCategory.VEHICLE);
        return equipment;
    }
}
