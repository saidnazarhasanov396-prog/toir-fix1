package com.toir.service;

import com.toir.dto.attachment.AttachmentGroupDto;
import com.toir.dto.equipment.EquipmentDto;
import com.toir.dto.file.PresignedUrlResponse;
import com.toir.dto.file.UploadFileResponse;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueRequest;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueDto;
import com.toir.dto.equipmentmanualattribute.EquipmentManualAttributeDto;
import com.toir.dto.equipmentmanualattribute.EquipmentManualAttributeRequest;
import com.toir.dto.mxik.MxikRefDto;
import com.toir.dto.vehicle.VehicleDetailDto;
import com.toir.dto.vehicle.VehicleDocumentDto;
import com.toir.dto.vehicle.VehicleRequest;
import com.toir.dto.vehicle.VehicleStatsResponse;
import com.toir.dto.vehicle.VehicleSummaryDto;
import com.toir.entity.Mxik;
import com.toir.entity.UploadedFile;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentLocationHistory;
import com.toir.entity.equipment.VehicleDocument;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentAttributeDataType;
import com.toir.enums.AttachmentTargetType;
import com.toir.enums.EquipmentLocationType;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.FileCategory;
import com.toir.enums.MeterType;
import com.toir.enums.VehicleRegistrationPlateType;
import com.toir.enums.VehicleType;
import com.toir.exception.RestException;
import com.toir.repository.UploadedFileRepository;
import com.toir.repository.MxikRepository;
import com.toir.repository.VehicleDocumentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.repository.projection.VehicleStatsProjection;
import com.toir.repository.equipment.EquipmentLocationHistoryRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.security.SecurityScope;
import com.toir.service.equipment.EquipmentService;
import com.toir.service.equipment.EquipmentAttributeService;
import com.toir.service.equipment.EquipmentManualAttributeService;
import com.toir.service.sparepartlifecycle.VehicleMeterProjectionGuard;
import com.toir.service.attachment.AttachmentGroupService;
import com.toir.service.file_management.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

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
@MockitoSettings(strictness = Strictness.LENIENT)
class VehicleServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    EquipmentLocationHistoryRepository equipmentLocationHistoryRepository;

    @Mock
    VehicleDetailsRepository vehicleDetailsRepository;

    @Mock
    MxikRepository mxikRepository;

    @Mock
    EquipmentService equipmentService;

    @Mock
    EquipmentAttributeService equipmentAttributeService;

    @Mock
    EquipmentManualAttributeService equipmentManualAttributeService;

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

    @Mock
    VehicleDocumentRepository vehicleDocumentRepository;

    @Mock
    AttachmentGroupService attachmentGroupService;

    @Mock
    VehicleMeterProjectionGuard vehicleMeterProjectionGuard;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "auditBuilderService", auditBuilderService);
        lenient().when(equipmentRepository.maxSequenceByCodePrefix(anyString())).thenReturn(0L);
        lenient().when(equipmentRepository.existsByCodeAndIsDeletedFalse(anyString())).thenReturn(false);
        lenient().doAnswer(invocation -> {
            List<MultipartFile> files = invocation.getArgument(6);
            AuthenticatedUser user = invocation.getArgument(8);
            String originalName = files == null || files.isEmpty() ? "document.pdf" : files.getFirst().getOriginalFilename();
            UUID uploadedBy = user == null || user.id() == null ? UUID.randomUUID() : UUID.fromString(user.id());
            return attachmentGroup(
                    invocation.getArgument(3),
                    UUID.randomUUID(),
                    invocation.getArgument(0),
                    invocation.getArgument(4),
                    invocation.getArgument(5),
                    originalName,
                    uploadedBy
            );
        }).when(attachmentGroupService).createGroup(any(), any(), any(), any(), any(), any(), anyList(), any(), any());
    }

    @Test
    void createVehicleCreatesEquipmentWithVehicleCategoryAndDetails() {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        VehicleRequest request = withGlobalRequirements(new VehicleRequest(
                null,
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
        ));

        lenient().when(equipmentRepository.existsByCodeAndIsDeletedFalse("VH-001")).thenReturn(false);
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
    void createVehicleStoresMxikOnEquipment() {
        UUID mxikId = UUID.randomUUID();
        Mxik mxik = mxik(mxikId, "8703", "Vehicle");
        VehicleRequest request = withMxik(
                fullRequest("VH-MXIK-001", "Truck MXIK", "INV-VH-MXIK-001", "01A787AA", null),
                mxikId
        );

        when(mxikRepository.findByIdAndIsDeletedFalse(mxikId)).thenReturn(Optional.of(mxik));
        when(equipmentRepository.existsByInventoryNumberAndIsDeletedFalse("INV-VH-MXIK-001")).thenReturn(false);
        when(vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse("01A787AA")).thenReturn(false);
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> {
            Equipment equipment = invocation.getArgument(0);
            equipment.setId(UUID.randomUUID());
            return equipment;
        });
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID equipmentId = invocation.getArgument(0);
            Equipment equipment = equipment(equipmentId, "VH-2026-0001", "Truck MXIK", "INV-VH-MXIK-001");
            equipment.setMxikId(mxikId);
            return EquipmentDto.from(equipment, null, null, null, null, null, null, null, null, null, null,
                    false, null, null, MxikRefDto.from(mxik));
        });

        service.create(request);

        ArgumentCaptor<Equipment> captor = ArgumentCaptor.forClass(Equipment.class);
        verify(equipmentRepository).save(captor.capture());
        assertThat(captor.getValue().getMxikId()).isEqualTo(mxikId);
    }

    @Test
    void createVehicleInitializesEquipmentPlacementAndHistory() {
        UUID equipmentId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        VehicleRequest request = VehicleRequest.minimal(
                null,
                "Truck Placement",
                "INV-VH-PLACE-001",
                equipmentTypeId,
                departmentId,
                "01A177AA",
                VehicleType.TRUCK,
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2024, 1, 1)
        );

        when(equipmentRepository.existsByInventoryNumberAndIsDeletedFalse("INV-VH-PLACE-001")).thenReturn(false);
        when(vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse("01A177AA")).thenReturn(false);
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> {
            Equipment equipment = invocation.getArgument(0);
            equipment.setId(equipmentId);
            return equipment;
        });
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(equipmentId)).thenAnswer(invocation -> {
            Equipment equipment = equipment(equipmentId, "EQ-2026-0001", "Truck Placement", "INV-VH-PLACE-001");
            equipment.setDepartmentId(departmentId);
            equipment.setCurrentLocationType(EquipmentLocationType.DEPARTMENT);
            equipment.setResponsibleDepartmentId(departmentId);
            return EquipmentDto.from(equipment);
        });

        service.create(request);

        ArgumentCaptor<Equipment> equipmentCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(equipmentRepository).save(equipmentCaptor.capture());
        Equipment savedEquipment = equipmentCaptor.getValue();
        assertThat(savedEquipment.getCurrentLocationType()).isEqualTo(EquipmentLocationType.DEPARTMENT);
        assertThat(savedEquipment.getResponsibleDepartmentId()).isEqualTo(departmentId);
        assertThat(savedEquipment.getCurrentWarehouseId()).isNull();

        ArgumentCaptor<EquipmentLocationHistory> historyCaptor = ArgumentCaptor.forClass(EquipmentLocationHistory.class);
        verify(equipmentLocationHistoryRepository).save(historyCaptor.capture());
        EquipmentLocationHistory history = historyCaptor.getValue();
        assertThat(history.getEquipmentId()).isEqualTo(equipmentId);
        assertThat(history.getFromLocationType()).isNull();
        assertThat(history.getToLocationType()).isEqualTo(EquipmentLocationType.DEPARTMENT);
        assertThat(history.getToDepartmentId()).isEqualTo(departmentId);
        assertThat(history.getResponsibleDepartmentId()).isEqualTo(departmentId);
        assertThat(history.getChangedAt()).isNotNull();
    }

    @Test
    void createVehiclePersistsEquipmentUsageAndLifetimeFields() {
        VehicleRequest request = withCurrentOdometer(withEquipmentUsage(
                fullRequest("VH-USAGE-001", "Truck Usage", "INV-VH-USAGE-001", "01A155AA", null),
                MeterType.MILEAGE_KM,
                300_000.0,
                10_000.0,
                15.0,
                250.0
        ), 10_000.0);

        when(equipmentRepository.existsByInventoryNumberAndIsDeletedFalse("INV-VH-USAGE-001")).thenReturn(false);
        when(vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse("01A155AA")).thenReturn(false);
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
            Equipment equipment = equipment(id, "VH-USAGE-001", "Truck Usage", "INV-VH-USAGE-001");
            equipment.setProducedYear(request.manufactureYear());
            equipment.setAverageDailyUsage(request.averageDailyUsage());
            equipment.setLifetimeCounterType(request.lifetimeCounterType());
            equipment.setLifetimeLimitValue(request.lifetimeLimitValue());
            equipment.setLifetimeBaselineValue(request.lifetimeBaselineValue());
            equipment.setLifetimeWarningPercent(request.lifetimeWarningPercent());
            return EquipmentDto.from(equipment);
        });

        VehicleDetailDto result = service.create(request);

        ArgumentCaptor<Equipment> equipmentCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(equipmentRepository).save(equipmentCaptor.capture());
        Equipment saved = equipmentCaptor.getValue();
        assertThat(saved.getProducedYear()).isEqualTo(2022);
        assertThat(saved.getAverageDailyUsage()).isEqualTo(250.0);
        assertThat(saved.getLifetimeCounterType()).isEqualTo(MeterType.MILEAGE_KM);
        assertThat(saved.getLifetimeLimitValue()).isEqualTo(300_000.0);
        assertThat(saved.getLifetimeBaselineValue()).isEqualTo(10_000.0);
        assertThat(saved.getLifetimeWarningPercent()).isEqualTo(15.0);
        assertThat(result.equipment().averageDailyUsage()).isEqualTo(250.0);
        assertThat(result.equipment().lifetimeLimitValue()).isEqualTo(300_000.0);
    }

    @Test
    void createVehicleCalculatesDaysOfResourceRemaining() {
        VehicleRequest request = withCurrentOdometer(withEquipmentUsage(
                fullRequest("VH-DAYS-001", "Truck Days", "INV-VH-DAYS-001", "01A166AA", null),
                MeterType.MILEAGE_KM,
                45_000.0,
                0.0,
                10.0,
                300.0
        ), 40_000.0);

        when(equipmentRepository.existsByInventoryNumberAndIsDeletedFalse("INV-VH-DAYS-001")).thenReturn(false);
        when(vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse("01A166AA")).thenReturn(false);
        when(equipmentService.calculateDaysOfResourceRemaining(any(), any()))
                .thenAnswer(invocation -> {
                    Double limit = invocation.getArgument(0);
                    Double usage = invocation.getArgument(1);
                    if (limit == null || usage == null || usage <= 0) {
                        return null;
                    }
                    return (long) (limit / usage);
                });
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
            Equipment equipment = equipment(id, "VH-DAYS-001", "Truck Days", "INV-VH-DAYS-001");
            equipment.setAverageDailyUsage(request.averageDailyUsage());
            equipment.setLifetimeLimitValue(request.lifetimeLimitValue());
            equipment.setDaysOfResourceRemaining(150L);
            return EquipmentDto.from(equipment);
        });

        service.create(request);

        ArgumentCaptor<Equipment> equipmentCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(equipmentRepository).save(equipmentCaptor.capture());
        assertThat(equipmentCaptor.getValue().getDaysOfResourceRemaining()).isEqualTo(16L);
    }

    @Test
    void createVehicleWithoutAverageDailyUsageLeavesDaysOfResourceRemainingNull() {
        VehicleRequest request = withEquipmentUsage(
                fullRequest("VH-DAYS-002", "Truck Days Null", "INV-VH-DAYS-002", "01A167AA", null),
                MeterType.MILEAGE_KM,
                45_000.0,
                0.0,
                10.0,
                null
        );

        when(equipmentRepository.existsByInventoryNumberAndIsDeletedFalse("INV-VH-DAYS-002")).thenReturn(false);
        when(vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse("01A167AA")).thenReturn(false);
        when(equipmentService.calculateDaysOfResourceRemaining(any(), any()))
                .thenAnswer(invocation -> {
                    Double limit = invocation.getArgument(0);
                    Double usage = invocation.getArgument(1);
                    if (limit == null || usage == null || usage <= 0) {
                        return null;
                    }
                    return (long) (limit / usage);
                });
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
        when(equipmentService.findById(any(UUID.class))).thenAnswer(invocation ->
                EquipmentDto.from(equipment(invocation.getArgument(0), "VH-DAYS-002", "Truck Days Null", "INV-VH-DAYS-002")));

        service.create(request);

        ArgumentCaptor<Equipment> equipmentCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(equipmentRepository).save(equipmentCaptor.capture());
        assertThat(equipmentCaptor.getValue().getDaysOfResourceRemaining()).isNull();
    }

    @Test
    void updateVehicleRecalculatesDaysOfResourceRemaining() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DAYS-UPD", "Truck Update Days", "INV-VH-DAYS-UPD");
        equipment.setLifetimeLimitValue(null);
        equipment.setAverageDailyUsage(null);
        equipment.setDaysOfResourceRemaining(null);
        VehicleDetails details = details(equipmentId, "01A168AA", null);
        VehicleRequest request = withEquipmentUsage(
                withEquipmentTypeAndAttributes(
                        fullRequest("VH-DAYS-UPD", "Truck Update Days", "INV-VH-DAYS-UPD", "01A168AA", null),
                        equipment.getEquipmentTypeId(),
                        null
                ),
                MeterType.MILEAGE_KM,
                60_000.0,
                0.0,
                10.0,
                500.0
        );

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(equipmentService.calculateDaysOfResourceRemaining(any(), any()))
                .thenAnswer(invocation -> {
                    Double limit = invocation.getArgument(0);
                    Double usage = invocation.getArgument(1);
                    if (limit == null || usage == null || usage <= 0) {
                        return null;
                    }
                    return (long) (limit / usage);
                });
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(equipmentId)).thenAnswer(invocation -> {
            Equipment saved = equipment(equipmentId, "VH-DAYS-UPD", "Truck Update Days", "INV-VH-DAYS-UPD");
            saved.setLifetimeLimitValue(request.lifetimeLimitValue());
            saved.setAverageDailyUsage(request.averageDailyUsage());
            saved.setDaysOfResourceRemaining(120L);
            return EquipmentDto.from(saved);
        });

        service.update(equipmentId, request);

        ArgumentCaptor<Equipment> equipmentCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(equipmentRepository).save(equipmentCaptor.capture());
        assertThat(equipmentCaptor.getValue().getDaysOfResourceRemaining()).isEqualTo(120L);
    }

    @Test
    void updateVehicleChecksMeterOwnedProjectionBeforeApplyingDetails() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-METER-GUARD", "Meter Guard", "INV-METER-GUARD");
        VehicleDetails details = details(equipmentId, "01A169AA", null);
        details.setCurrentOdometerKm(1_000);
        details.setCurrentEngineHours(25);
        VehicleRequest request = withEquipmentTypeAndAttributes(
                fullRequest("VH-METER-GUARD", "Meter Guard", "INV-METER-GUARD", "01A169AA", null),
                equipment.getEquipmentTypeId(),
                null
        );

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        doThrow(RestException.conflict("METER_PROJECTION_READ_ONLY: use a meter reading"))
                .when(vehicleMeterProjectionGuard)
                .assertCompatibleUpdate(
                        equipmentId,
                        details,
                        request.currentOdometerKm(),
                        request.currentEngineHours()
                );

        assertThatThrownBy(() -> service.update(equipmentId, request))
                .hasMessageStartingWith("METER_PROJECTION_READ_ONLY:");
        verify(equipmentRepository, never()).save(any());
        verify(vehicleDetailsRepository, never()).save(any());
    }

    @Test
    void createVehiclePersistsAndReturnsPlateType() {
        VehicleRequest request = withPlateType(
                fullRequest("VH-PLATE-TYPE-001", "Truck Plate Type", "INV-VH-PLATE-TYPE-001", "95 123 ABC", null),
                VehicleRegistrationPlateType.LEGAL_ENTITY
        );

        lenient().when(equipmentRepository.existsByCodeAndIsDeletedFalse("VH-PLATE-TYPE-001")).thenReturn(false);
        when(equipmentRepository.existsByInventoryNumberAndIsDeletedFalse("INV-VH-PLATE-TYPE-001")).thenReturn(false);
        when(vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse("95 123 ABC")).thenReturn(false);
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
        when(equipmentService.findById(any(UUID.class))).thenAnswer(invocation ->
                EquipmentDto.from(equipment(invocation.getArgument(0), "VH-PLATE-TYPE-001", "Truck Plate Type", "INV-VH-PLATE-TYPE-001")));

        VehicleDetailDto result = service.create(request);

        ArgumentCaptor<VehicleDetails> detailsCaptor = ArgumentCaptor.forClass(VehicleDetails.class);
        verify(vehicleDetailsRepository).save(detailsCaptor.capture());
        assertThat(detailsCaptor.getValue().getPlateNumber()).isEqualTo("95 123 ABC");
        assertThat(detailsCaptor.getValue().getPlateType()).isEqualTo(VehicleRegistrationPlateType.LEGAL_ENTITY);
        assertThat(result.vehicleDetails().plateType()).isEqualTo(VehicleRegistrationPlateType.LEGAL_ENTITY);
    }

    @Test
    void createVehicleDefaultsMissingPlateTypeToUnknown() {
        VehicleRequest request = fullRequest("VH-PLATE-TYPE-002", "Truck Default Plate Type", "INV-VH-PLATE-TYPE-002", "LEGACY-123", null);

        lenient().when(equipmentRepository.existsByCodeAndIsDeletedFalse("VH-PLATE-TYPE-002")).thenReturn(false);
        when(equipmentRepository.existsByInventoryNumberAndIsDeletedFalse("INV-VH-PLATE-TYPE-002")).thenReturn(false);
        when(vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse("LEGACY-123")).thenReturn(false);
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
        when(equipmentService.findById(any(UUID.class))).thenAnswer(invocation ->
                EquipmentDto.from(equipment(invocation.getArgument(0), "VH-PLATE-TYPE-002", "Truck Default Plate Type", "INV-VH-PLATE-TYPE-002")));

        VehicleDetailDto result = service.create(request);

        ArgumentCaptor<VehicleDetails> detailsCaptor = ArgumentCaptor.forClass(VehicleDetails.class);
        verify(vehicleDetailsRepository).save(detailsCaptor.capture());
        assertThat(detailsCaptor.getValue().getPlateType()).isEqualTo(VehicleRegistrationPlateType.UNKNOWN);
        assertThat(result.vehicleDetails().plateType()).isEqualTo(VehicleRegistrationPlateType.UNKNOWN);
    }

    @Test
    void createVehicleAcceptsDynamicMetricAttribute() {
        VehicleRequest request = fullRequest("VH-ATTR-001", "Truck Attr", "INV-VH-ATTR-001", "01A101AA", null)
                .withAttributes(List.of(new EquipmentAttributeValueRequest(null, "payload_capacity", null, 12000.0, null, null, null, null)));

        lenient().when(equipmentRepository.existsByCodeAndIsDeletedFalse("VH-ATTR-001")).thenReturn(false);
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
    void vehicleDetailReturnsOfficialAttributes() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DETAIL-ATTR", "Truck Detail Attr", "INV-VH-DETAIL-ATTR");
        VehicleDetails details = details(equipmentId, "01A301AA", null);
        EquipmentAttributeValueDto attribute = attributeValue(equipmentId, "payload_capacity", 12000.0);

        when(equipmentService.findById(equipmentId)).thenReturn(EquipmentDto.from(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(vehicleDocumentRepository.findAllByEquipmentId(equipmentId)).thenReturn(List.of());
        when(equipmentAttributeService.findValues(equipmentId)).thenReturn(List.of(attribute));

        VehicleDetailDto result = service.findByEquipmentId(equipmentId);

        assertThat(result.attributes()).containsExactly(attribute);
        assertThat(result.manualAttributes()).isEmpty();
    }

    @Test
    void vehicleDetailReturnsEmptyAttributesWhenNoOfficialValues() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DETAIL-EMPTY", "Truck Detail Empty", "INV-VH-DETAIL-EMPTY");
        VehicleDetails details = details(equipmentId, "01A302AA", null);

        when(equipmentService.findById(equipmentId)).thenReturn(EquipmentDto.from(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(vehicleDocumentRepository.findAllByEquipmentId(equipmentId)).thenReturn(List.of());
        when(equipmentAttributeService.findValues(equipmentId)).thenReturn(List.of());

        VehicleDetailDto result = service.findByEquipmentId(equipmentId);

        assertThat(result.attributes()).isEmpty();
    }

    @Test
    void vehicleDetailDoesNotRequireManualAttributes() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DETAIL-NO-MANUAL", "Truck No Manual", "INV-VH-DETAIL-NO-MANUAL");
        VehicleDetails details = details(equipmentId, "01A303AA", null);

        when(equipmentService.findById(equipmentId)).thenReturn(EquipmentDto.from(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(vehicleDocumentRepository.findAllByEquipmentId(equipmentId)).thenReturn(List.of());
        when(equipmentAttributeService.findValues(equipmentId)).thenReturn(List.of());

        VehicleDetailDto result = service.findByEquipmentId(equipmentId);

        assertThat(result.attributes()).isEmpty();
        assertThat(result.manualAttributes()).isEmpty();
    }

    @Test
    void vehicleDetailReturnsManualAttributes() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DETAIL-MANUAL", "Truck Manual Detail", "INV-VH-DETAIL-MANUAL");
        VehicleDetails details = details(equipmentId, "01A307AA", null);
        EquipmentManualAttributeDto manualAttribute = manualAttribute("legacy_key", "legacy value");

        when(equipmentService.findById(equipmentId)).thenReturn(EquipmentDto.from(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(vehicleDocumentRepository.findAllByEquipmentId(equipmentId)).thenReturn(List.of());
        when(equipmentAttributeService.findValues(equipmentId)).thenReturn(List.of());
        when(equipmentManualAttributeService.list(equipmentId)).thenReturn(List.of(manualAttribute));

        VehicleDetailDto result = service.findByEquipmentId(equipmentId);

        assertThat(result.manualAttributes()).containsExactly(manualAttribute);
    }

    @Test
    void vehicleCreateWithoutManualAttributesDoesNotWriteManualAttributes() {
        VehicleRequest request = fullRequest("VH-NO-MANUAL-001", "Truck No Manual", "INV-VH-NO-MANUAL-001", "01A308AA", null);

        lenient().when(equipmentRepository.existsByCodeAndIsDeletedFalse("VH-NO-MANUAL-001")).thenReturn(false);
        when(equipmentRepository.existsByInventoryNumberAndIsDeletedFalse("INV-VH-NO-MANUAL-001")).thenReturn(false);
        when(vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse("01A308AA")).thenReturn(false);
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> {
            Equipment equipment = invocation.getArgument(0);
            equipment.setId(UUID.randomUUID());
            return equipment;
        });
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(any(UUID.class))).thenAnswer(invocation ->
                EquipmentDto.from(equipment(invocation.getArgument(0), "VH-NO-MANUAL-001", "Truck No Manual", "INV-VH-NO-MANUAL-001")));

        VehicleDetailDto result = service.create(request);

        verify(equipmentManualAttributeService, never()).replaceAll(any(), any());
        assertThat(result.manualAttributes()).isEmpty();
    }

    @Test
    void vehicleCreateStillAcceptsOfficialAttributes() {
        VehicleRequest request = fullRequest("VH-OFFICIAL-001", "Truck Official", "INV-VH-OFFICIAL-001", "01A201AA", null)
                .withAttributes(List.of(new EquipmentAttributeValueRequest(null, "payload_capacity", null, 12000.0, null, null, null, null)));

        lenient().when(equipmentRepository.existsByCodeAndIsDeletedFalse("VH-OFFICIAL-001")).thenReturn(false);
        when(equipmentRepository.existsByInventoryNumberAndIsDeletedFalse("INV-VH-OFFICIAL-001")).thenReturn(false);
        when(vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse("01A201AA")).thenReturn(false);
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> {
            Equipment equipment = invocation.getArgument(0);
            equipment.setId(UUID.randomUUID());
            return equipment;
        });
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(any(UUID.class))).thenAnswer(invocation ->
                EquipmentDto.from(equipment(invocation.getArgument(0), "VH-OFFICIAL-001", "Truck Official", "INV-VH-OFFICIAL-001")));
        when(equipmentAttributeService.findValues(any(UUID.class))).thenReturn(List.of(attributeValue(UUID.randomUUID(), "payload_capacity", 12000.0)));

        VehicleDetailDto result = service.create(request);

        verify(equipmentAttributeService).upsertValues(any(Equipment.class), eq(request.attributes()));
        verify(equipmentManualAttributeService, never()).replaceAll(any(), any());
        assertThat(result.attributes()).extracting(EquipmentAttributeValueDto::key).containsExactly("payload_capacity");
    }

    @Test
    void vehicleCreateAcceptsFullOfficialAttributes() {
        List<EquipmentAttributeValueRequest> attributes = List.of(
                new EquipmentAttributeValueRequest(null, "payload_capacity", null, 12000.0, null, null, null, null),
                new EquipmentAttributeValueRequest(null, "axle_count", null, 4.0, null, null, null, null)
        );
        VehicleRequest request = fullRequest("VH-FULL-ATTR-001", "Truck Full Attr", "INV-VH-FULL-ATTR-001", "01A207AA", null)
                .withAttributes(attributes);

        lenient().when(equipmentRepository.existsByCodeAndIsDeletedFalse("VH-FULL-ATTR-001")).thenReturn(false);
        when(equipmentRepository.existsByInventoryNumberAndIsDeletedFalse("INV-VH-FULL-ATTR-001")).thenReturn(false);
        when(vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse("01A207AA")).thenReturn(false);
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> {
            Equipment equipment = invocation.getArgument(0);
            equipment.setId(UUID.randomUUID());
            return equipment;
        });
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(any(UUID.class))).thenAnswer(invocation ->
                EquipmentDto.from(equipment(invocation.getArgument(0), "VH-FULL-ATTR-001", "Truck Full Attr", "INV-VH-FULL-ATTR-001")));
        when(equipmentAttributeService.findValues(any(UUID.class))).thenReturn(List.of(
                attributeValue(UUID.randomUUID(), "payload_capacity", 12000.0),
                attributeValue(UUID.randomUUID(), "axle_count", 4.0)
        ));

        VehicleDetailDto result = service.create(request);

        verify(equipmentAttributeService).upsertValues(any(Equipment.class), eq(attributes));
        assertThat(result.attributes()).extracting(EquipmentAttributeValueDto::key)
                .containsExactly("payload_capacity", "axle_count");
    }

    @Test
    void vehicleCreateThenDetailContainsOfficialAttributes() {
        VehicleRequest request = fullRequest("VH-DETAIL-CREATE", "Truck Detail Create", "INV-VH-DETAIL-CREATE", "01A304AA", null)
                .withAttributes(List.of(new EquipmentAttributeValueRequest(null, "payload_capacity", null, 13000.0, null, null, null, null)));

        lenient().when(equipmentRepository.existsByCodeAndIsDeletedFalse("VH-DETAIL-CREATE")).thenReturn(false);
        when(equipmentRepository.existsByInventoryNumberAndIsDeletedFalse("INV-VH-DETAIL-CREATE")).thenReturn(false);
        when(vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse("01A304AA")).thenReturn(false);
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> {
            Equipment equipment = invocation.getArgument(0);
            equipment.setId(UUID.randomUUID());
            return equipment;
        });
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(any(UUID.class))).thenAnswer(invocation ->
                EquipmentDto.from(equipment(invocation.getArgument(0), "VH-DETAIL-CREATE", "Truck Detail Create", "INV-VH-DETAIL-CREATE")));
        when(equipmentAttributeService.findValues(any(UUID.class))).thenReturn(List.of(attributeValue(UUID.randomUUID(), "payload_capacity", 13000.0)));

        VehicleDetailDto result = service.create(request);

        assertThat(result.attributes()).extracting(EquipmentAttributeValueDto::key).containsExactly("payload_capacity");
        assertThat(result.attributes().getFirst().valueNumber()).isEqualTo(13000.0);
    }

    @Test
    void vehicleCreateAcceptsManualAttributes() {
        VehicleRequest request = withManualAttributes(
                fullRequest("VH-MANUAL-001", "Truck Manual", "INV-VH-MANUAL-001", "01A202AA", null),
                List.of(new EquipmentManualAttributeRequest("legacy_key", "legacy value"))
        );
        EquipmentManualAttributeDto manualAttribute = manualAttribute("legacy_key", "legacy value");

        lenient().when(equipmentRepository.existsByCodeAndIsDeletedFalse("VH-MANUAL-001")).thenReturn(false);
        when(equipmentRepository.existsByInventoryNumberAndIsDeletedFalse("INV-VH-MANUAL-001")).thenReturn(false);
        when(vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse("01A202AA")).thenReturn(false);
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> {
            Equipment equipment = invocation.getArgument(0);
            equipment.setId(UUID.randomUUID());
            return equipment;
        });
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(any(UUID.class))).thenAnswer(invocation ->
                EquipmentDto.from(equipment(invocation.getArgument(0), "VH-MANUAL-001", "Truck Manual", "INV-VH-MANUAL-001")));
        when(equipmentManualAttributeService.list(any(UUID.class))).thenReturn(List.of(manualAttribute));

        VehicleDetailDto result = service.create(request);

        verify(equipmentManualAttributeService).replaceAll(any(UUID.class), argThat(bulk ->
                bulk.attributes().size() == 1
                        && "legacy_key".equals(bulk.attributes().getFirst().key())
                        && "legacy value".equals(bulk.attributes().getFirst().value())));
        assertThat(result.manualAttributes()).containsExactly(manualAttribute);
    }

    @Test
    void createVehicleWithMissingRequiredDynamicMetricFails() {
        VehicleRequest request = fullRequest("VH-ATTR-002", "Truck Attr Missing", "INV-VH-ATTR-002", "01A102AA", null);

        lenient().when(equipmentRepository.existsByCodeAndIsDeletedFalse("VH-ATTR-002")).thenReturn(false);
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
                null,
                "Truck 002",
                "INV-VH-002",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "01A999AA",
                VehicleType.TRUCK,
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2024, 1, 1)
        );

        lenient().when(equipmentRepository.existsByCodeAndIsDeletedFalse("VH-002")).thenReturn(false);
        when(equipmentRepository.existsByInventoryNumberAndIsDeletedFalse("INV-VH-002")).thenReturn(false);
        when(vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse("01A999AA")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Vehicle plate number already exists");
    }

    @Test
    void createVehicleAllowsSoftDeletedPlateAndVinReuseWhenActiveLookupsDoNotFindThem() {
        VehicleRequest request = fullRequest("VH-003", "Truck 003", "INV-VH-003", "01A777AA", "VIN-REUSED");

        lenient().when(equipmentRepository.existsByCodeAndIsDeletedFalse("VH-003")).thenReturn(false);
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

        lenient().when(equipmentRepository.existsByCodeAndIsDeletedFalse("VH-004")).thenReturn(false);
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
    void updateVehicleRejectsClientProvidedCode() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-010", "Truck 010", "INV-VH-010");
        VehicleRequest request = withClientCode(
                fullRequest("VH-010", "Truck 010", "INV-VH-010", "01A010AA", "VIN-010"),
                "VH-011"
        );

        assertThatThrownBy(() -> service.update(equipmentId, request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("code is generated by backend and must not be provided");
        verify(equipmentRepository, never()).findByIdAndIsDeletedFalse(equipment.getId());
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
        VehicleRequest request = withEquipmentTypeAndAttributes(
                fullRequest("VH-041", "Truck 041", "INV-VH-041", "01A041AA", "VIN-041"),
                equipment.getEquipmentTypeId(),
                null
        );
        Equipment updated = updatedEquipment(equipmentId, request);
        updated.setCode(equipment.getCode());

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(equipmentRepository.findByInventoryNumberAndIsDeletedFalse("INV-VH-041")).thenReturn(Optional.empty());
        when(vehicleDetailsRepository.findByPlateNumberAndIsDeletedFalse("01A041AA")).thenReturn(Optional.empty());
        when(vehicleDetailsRepository.findByVinAndIsDeletedFalse("VIN-041")).thenReturn(Optional.empty());
        when(equipmentRepository.save(any(Equipment.class))).thenReturn(updated);
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(equipmentId)).thenReturn(EquipmentDto.from(updated));

        VehicleDetailDto result = service.update(equipmentId, request);

        assertThat(result.equipment().code()).isEqualTo("VH-040");
        assertThat(result.equipment().inventoryNumber()).isEqualTo("INV-VH-041");
        assertThat(result.vehicleDetails().plateNumber()).isEqualTo("01A041AA");
        assertThat(result.vehicleDetails().vin()).isEqualTo("VIN-041");
    }

    @Test
    void updateVehicleNormalizesBlankVinToNull() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-050", "Truck 050", "INV-VH-050");
        VehicleDetails details = details(equipmentId, "01A050AA", "VIN-050");
        VehicleRequest request = withEquipmentTypeAndAttributes(
                fullRequest("VH-050", "Truck 050", "INV-VH-050", "01A050AA", " "),
                equipment.getEquipmentTypeId(),
                null
        );
        Equipment updated = updatedEquipment(equipmentId, request);
        updated.setCode(equipment.getCode());

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
        VehicleRequest request = withEquipmentTypeAndAttributes(
                fullRequest("VH-020", "Truck 020 Updated", "INV-VH-020", "01A020AA", "VIN-020"),
                equipment.getEquipmentTypeId(),
                null
        );
        Equipment updated = updatedEquipment(equipmentId, request);
        updated.setCode(equipment.getCode());

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
    void updateVehicleDepartmentChangeWritesLocationHistory() {
        UUID equipmentId = UUID.randomUUID();
        UUID fromDepartmentId = UUID.randomUUID();
        UUID toDepartmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-021", "Truck 021", "INV-VH-021");
        equipment.setDepartmentId(fromDepartmentId);
        equipment.setCurrentLocationType(EquipmentLocationType.DEPARTMENT);
        equipment.setResponsibleDepartmentId(fromDepartmentId);
        VehicleDetails details = details(equipmentId, "01A021AA", null);
        VehicleRequest request = VehicleRequest.minimal(
                null,
                "Truck 021 Updated",
                "INV-VH-021",
                equipment.getEquipmentTypeId(),
                toDepartmentId,
                "01A021AA",
                VehicleType.TRUCK,
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2024, 1, 1)
        );

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(equipmentId)).thenReturn(EquipmentDto.from(equipment));

        service.update(equipmentId, request);

        ArgumentCaptor<EquipmentLocationHistory> historyCaptor = ArgumentCaptor.forClass(EquipmentLocationHistory.class);
        verify(equipmentLocationHistoryRepository).save(historyCaptor.capture());
        EquipmentLocationHistory history = historyCaptor.getValue();
        assertThat(history.getEquipmentId()).isEqualTo(equipmentId);
        assertThat(history.getFromLocationType()).isEqualTo(EquipmentLocationType.DEPARTMENT);
        assertThat(history.getFromDepartmentId()).isEqualTo(fromDepartmentId);
        assertThat(history.getToLocationType()).isEqualTo(EquipmentLocationType.DEPARTMENT);
        assertThat(history.getToDepartmentId()).isEqualTo(toDepartmentId);
        assertThat(history.getResponsibleDepartmentId()).isEqualTo(toDepartmentId);
    }

    @Test
    void updateVehiclePersistsAndReturnsPlateTypeWithoutChangingPlateNumberBehavior() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-PLATE-TYPE-010", "Truck Plate Type", "INV-VH-PLATE-TYPE-010");
        VehicleDetails details = details(equipmentId, "95 K 123 BA", "VIN-PLATE-TYPE-010");
        VehicleRequest request = withPlateType(
                withEquipmentTypeAndAttributes(
                        fullRequest("VH-PLATE-TYPE-010", "Truck Plate Type Updated", "INV-VH-PLATE-TYPE-010", "95 K 123 BA", "VIN-PLATE-TYPE-010"),
                        equipment.getEquipmentTypeId(),
                        null
                ),
                VehicleRegistrationPlateType.INDIVIDUAL
        );

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(equipmentId)).thenReturn(EquipmentDto.from(equipment));

        VehicleDetailDto result = service.update(equipmentId, request);

        assertThat(details.getPlateNumber()).isEqualTo("95 K 123 BA");
        assertThat(details.getPlateType()).isEqualTo(VehicleRegistrationPlateType.INDIVIDUAL);
        assertThat(result.vehicleDetails().plateNumber()).isEqualTo("95 K 123 BA");
        assertThat(result.vehicleDetails().plateType()).isEqualTo(VehicleRegistrationPlateType.INDIVIDUAL);
        verify(vehicleDetailsRepository, never()).findByPlateNumberAndIsDeletedFalse("95 K 123 BA");
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
    void vehicleUpdateStillAcceptsOfficialAttributes() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-OFFICIAL-003", "Truck Official Update", "INV-VH-OFFICIAL-003");
        equipment.setCategory(EquipmentCategory.VEHICLE);
        VehicleDetails details = details(equipmentId, "01A203AA", null);
        VehicleRequest request = fullRequest("VH-OFFICIAL-003", "Truck Official Update", "INV-VH-OFFICIAL-003", "01A203AA", null)
                .withAttributes(List.of(new EquipmentAttributeValueRequest(null, "payload_capacity", null, 14000.0, null, null, null, null)));

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(equipmentId)).thenReturn(EquipmentDto.from(equipment));
        when(equipmentAttributeService.findValues(equipmentId)).thenReturn(List.of(attributeValue(equipmentId, "payload_capacity", 14000.0)));

        VehicleDetailDto result = service.update(equipmentId, request);

        verify(equipmentAttributeService).upsertValues(eq(equipment), eq(request.attributes()));
        verify(equipmentManualAttributeService, never()).replaceAll(any(), any());
        assertThat(result.attributes()).extracting(EquipmentAttributeValueDto::key).containsExactly("payload_capacity");
    }

    @Test
    void vehicleUpdateAcceptsFullOfficialAttributes() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-FULL-ATTR-003", "Truck Full Attr Update", "INV-VH-FULL-ATTR-003");
        equipment.setCategory(EquipmentCategory.VEHICLE);
        VehicleDetails details = details(equipmentId, "01A208AA", null);
        List<EquipmentAttributeValueRequest> attributes = List.of(
                new EquipmentAttributeValueRequest(null, "payload_capacity", null, 14000.0, null, null, null, null),
                new EquipmentAttributeValueRequest(null, "axle_count", null, 6.0, null, null, null, null)
        );
        VehicleRequest request = fullRequest("VH-FULL-ATTR-003", "Truck Full Attr Update", "INV-VH-FULL-ATTR-003", "01A208AA", null)
                .withAttributes(attributes);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(equipmentId)).thenReturn(EquipmentDto.from(equipment));
        when(equipmentAttributeService.findValues(equipmentId)).thenReturn(List.of(
                attributeValue(equipmentId, "payload_capacity", 14000.0),
                attributeValue(equipmentId, "axle_count", 6.0)
        ));

        VehicleDetailDto result = service.update(equipmentId, request);

        verify(equipmentAttributeService).upsertValues(eq(equipment), eq(attributes));
        assertThat(result.attributes()).extracting(EquipmentAttributeValueDto::key)
                .containsExactly("payload_capacity", "axle_count");
    }

    @Test
    void vehicleUpdateSameTypeWithoutAttributesKeepsExistingBehavior() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-SAME-TYPE", "Truck Same Type", "INV-VH-SAME-TYPE");
        equipment.setCategory(EquipmentCategory.VEHICLE);
        equipment.setEquipmentTypeId(typeId);
        VehicleDetails details = details(equipmentId, "01A401AA", null);
        VehicleRequest request = withEquipmentTypeAndAttributes(
                fullRequest("VH-SAME-TYPE", "Truck Same Type Updated", "INV-VH-SAME-TYPE", "01A401AA", null),
                typeId,
                null
        );

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(equipmentId)).thenReturn(EquipmentDto.from(equipment));

        service.update(equipmentId, request);

        verify(equipmentAttributeService, never()).upsertValues(any(), any());
    }

    @Test
    void vehicleTypeChangeWithoutAttributesIsRejected() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-TYPE-NO-ATTR", "Truck Type Change", "INV-VH-TYPE-NO-ATTR");
        equipment.setCategory(EquipmentCategory.VEHICLE);
        VehicleDetails details = details(equipmentId, "01A402AA", null);
        VehicleRequest request = withEquipmentTypeAndAttributes(
                fullRequest("VH-TYPE-NO-ATTR", "Truck Type Change", "INV-VH-TYPE-NO-ATTR", "01A402AA", null),
                UUID.randomUUID(),
                null
        );

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));

        assertThatThrownBy(() -> service.update(equipmentId, request))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getMessage()).contains("Attributes are required when equipment type changes."));

        verify(equipmentRepository, never()).save(any());
        verify(equipmentAttributeService, never()).upsertValues(any(), any());
    }

    @Test
    void vehicleTypeChangeWithEmptyAttributesAndRequiredNewTypeIsRejected() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-TYPE-EMPTY", "Truck Type Empty", "INV-VH-TYPE-EMPTY");
        equipment.setCategory(EquipmentCategory.VEHICLE);
        VehicleDetails details = details(equipmentId, "01A403AA", null);
        VehicleRequest request = withEquipmentTypeAndAttributes(
                fullRequest("VH-TYPE-EMPTY", "Truck Type Empty", "INV-VH-TYPE-EMPTY", "01A403AA", null),
                UUID.randomUUID(),
                List.of()
        );

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(RestException.badRequest("Missing required equipment attributes: payload_capacity (required by equipment type)"))
                .when(equipmentAttributeService).upsertValues(any(Equipment.class), eq(List.of()));

        assertThatThrownBy(() -> service.update(equipmentId, request))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getMessage()).contains("Missing required equipment attributes"));
    }

    @Test
    void vehicleTypeChangeWithRequiredNewTypeAttributesSucceeds() {
        UUID equipmentId = UUID.randomUUID();
        UUID newTypeId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-TYPE-ATTR", "Truck Type Attr", "INV-VH-TYPE-ATTR");
        equipment.setCategory(EquipmentCategory.VEHICLE);
        VehicleDetails details = details(equipmentId, "01A404AA", null);
        List<EquipmentAttributeValueRequest> attributes = List.of(
                new EquipmentAttributeValueRequest(null, "payload_capacity", null, 14000.0, null, null, null, null)
        );
        VehicleRequest request = withEquipmentTypeAndAttributes(
                fullRequest("VH-TYPE-ATTR", "Truck Type Attr", "INV-VH-TYPE-ATTR", "01A404AA", null),
                newTypeId,
                attributes
        );

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(equipmentId)).thenReturn(EquipmentDto.from(equipment));

        service.update(equipmentId, request);

        ArgumentCaptor<Equipment> equipmentCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(equipmentAttributeService).upsertValues(equipmentCaptor.capture(), eq(attributes));
        assertThat(equipmentCaptor.getValue().getEquipmentTypeId()).isEqualTo(newTypeId);
    }

    @Test
    void vehicleTypeChangeRejectsOldTypeAttributeKey() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-TYPE-OLD-KEY", "Truck Old Key", "INV-VH-TYPE-OLD-KEY");
        equipment.setCategory(EquipmentCategory.VEHICLE);
        VehicleDetails details = details(equipmentId, "01A405AA", null);
        List<EquipmentAttributeValueRequest> attributes = List.of(
                new EquipmentAttributeValueRequest(null, "old_type_key", "legacy", null, null, null, null, null)
        );
        VehicleRequest request = withEquipmentTypeAndAttributes(
                fullRequest("VH-TYPE-OLD-KEY", "Truck Old Key", "INV-VH-TYPE-OLD-KEY", "01A405AA", null),
                UUID.randomUUID(),
                attributes
        );

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(RestException.badRequest("Unknown equipment attribute key: old_type_key"))
                .when(equipmentAttributeService).upsertValues(any(Equipment.class), eq(attributes));

        assertThatThrownBy(() -> service.update(equipmentId, request))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getMessage()).contains("Unknown equipment attribute key"));
    }

    @Test
    void vehicleTypeChangeRejectsOldTypeAttributeDefinitionId() {
        UUID equipmentId = UUID.randomUUID();
        UUID oldDefinitionId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-TYPE-OLD-ID", "Truck Old Id", "INV-VH-TYPE-OLD-ID");
        equipment.setCategory(EquipmentCategory.VEHICLE);
        VehicleDetails details = details(equipmentId, "01A406AA", null);
        List<EquipmentAttributeValueRequest> attributes = List.of(
                new EquipmentAttributeValueRequest(oldDefinitionId, null, "legacy", null, null, null, null, null)
        );
        VehicleRequest request = withEquipmentTypeAndAttributes(
                fullRequest("VH-TYPE-OLD-ID", "Truck Old Id", "INV-VH-TYPE-OLD-ID", "01A406AA", null),
                UUID.randomUUID(),
                attributes
        );

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(RestException.badRequest("Attribute definition is not allowed for this equipment type: " + oldDefinitionId))
                .when(equipmentAttributeService).upsertValues(any(Equipment.class), eq(attributes));

        assertThatThrownBy(() -> service.update(equipmentId, request))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getMessage()).contains("not allowed for this equipment type"));
    }

    @Test
    void vehicleUpdateThenDetailReflectsOfficialAttributes() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DETAIL-UPDATE", "Truck Detail Update", "INV-VH-DETAIL-UPDATE");
        equipment.setCategory(EquipmentCategory.VEHICLE);
        VehicleDetails details = details(equipmentId, "01A305AA", null);
        VehicleRequest request = fullRequest("VH-DETAIL-UPDATE", "Truck Detail Update", "INV-VH-DETAIL-UPDATE", "01A305AA", null)
                .withAttributes(List.of(new EquipmentAttributeValueRequest(null, "payload_capacity", null, 15000.0, null, null, null, null)));

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(equipmentId)).thenReturn(EquipmentDto.from(equipment));
        when(equipmentAttributeService.findValues(equipmentId)).thenReturn(List.of(attributeValue(equipmentId, "payload_capacity", 15000.0)));

        VehicleDetailDto result = service.update(equipmentId, request);

        assertThat(result.attributes()).extracting(EquipmentAttributeValueDto::key).containsExactly("payload_capacity");
        assertThat(result.attributes().getFirst().valueNumber()).isEqualTo(15000.0);
    }

    @Test
    void vehicleUpdateAcceptsManualAttributes() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-MANUAL-003", "Truck Manual Update", "INV-VH-MANUAL-003");
        equipment.setCategory(EquipmentCategory.VEHICLE);
        VehicleDetails details = details(equipmentId, "01A204AA", null);
        VehicleRequest request = withManualAttributes(
                withEquipmentTypeAndAttributes(
                        fullRequest("VH-MANUAL-003", "Truck Manual Update", "INV-VH-MANUAL-003", "01A204AA", null),
                        equipment.getEquipmentTypeId(),
                        null
                ),
                List.of(new EquipmentManualAttributeRequest("legacy_key", "legacy value"))
        );
        EquipmentManualAttributeDto manualAttribute = manualAttribute("legacy_key", "legacy value");

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(equipmentId)).thenReturn(EquipmentDto.from(equipment));
        when(equipmentManualAttributeService.list(equipmentId)).thenReturn(List.of(manualAttribute));

        VehicleDetailDto result = service.update(equipmentId, request);

        verify(equipmentManualAttributeService).replaceAll(eq(equipmentId), argThat(bulk ->
                bulk.attributes().size() == 1
                        && "legacy_key".equals(bulk.attributes().getFirst().key())
                        && "legacy value".equals(bulk.attributes().getFirst().value())));
        verify(equipmentAttributeService, never()).upsertValues(any(), any());
        assertThat(result.manualAttributes()).containsExactly(manualAttribute);
    }

    @Test
    void createVehicleWithOfficialAndManualAttributesPersistsBoth() {
        VehicleRequest request = withManualAttributes(
                fullRequest("VH-MANUAL-PHASE1A", "Truck Manual Phase1A", "INV-VH-MANUAL-PHASE1A", "01A306AA", null)
                        .withAttributes(List.of(new EquipmentAttributeValueRequest(null, "payload_capacity", null, 12000.0, null, null, null, null))),
                List.of(new EquipmentManualAttributeRequest("legacy_key", "legacy value"))
        );
        EquipmentManualAttributeDto manualAttribute = manualAttribute("legacy_key", "legacy value");
        EquipmentAttributeValueDto officialAttribute = attributeValue(UUID.randomUUID(), "payload_capacity", 12000.0);

        lenient().when(equipmentRepository.existsByCodeAndIsDeletedFalse("VH-MANUAL-PHASE1A")).thenReturn(false);
        when(equipmentRepository.existsByInventoryNumberAndIsDeletedFalse("INV-VH-MANUAL-PHASE1A")).thenReturn(false);
        when(vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse("01A306AA")).thenReturn(false);
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> {
            Equipment equipment = invocation.getArgument(0);
            equipment.setId(UUID.randomUUID());
            return equipment;
        });
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentService.findById(any(UUID.class))).thenAnswer(invocation ->
                EquipmentDto.from(equipment(invocation.getArgument(0), "VH-MANUAL-PHASE1A", "Truck Manual Phase1A", "INV-VH-MANUAL-PHASE1A")));
        when(equipmentAttributeService.findValues(any(UUID.class))).thenReturn(List.of(officialAttribute));
        when(equipmentManualAttributeService.list(any(UUID.class))).thenReturn(List.of(manualAttribute));

        VehicleDetailDto result = service.create(request);

        verify(equipmentAttributeService).upsertValues(any(Equipment.class), eq(request.attributes()));
        verify(equipmentManualAttributeService).replaceAll(any(UUID.class), any());
        assertThat(result.attributes()).containsExactly(officialAttribute);
        assertThat(result.manualAttributes()).containsExactly(manualAttribute);
    }

    @Test
    void listSkipsVehicleEquipmentRowsWithoutDetails() {
        UUID completeId = UUID.randomUUID();
        UUID incompleteId = UUID.randomUUID();
        Equipment complete = equipment(completeId, "VH-030", "Truck 030", "INV-VH-030");
        complete.setProducedYear(2024);
        complete.setAverageDailyUsage(220.0);
        complete.setLifetimeCounterType(MeterType.MILEAGE_KM);
        complete.setLifetimeLimitValue(280_000.0);
        complete.setLifetimeBaselineValue(10_000.0);
        complete.setLifetimeWarningPercent(12.0);
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
                isNull(),
                eq(EquipmentCategory.VEHICLE),
                isNull(),
                eq(pageRequest)
        )).thenReturn(equipmentPage);

        when(equipmentService.enrich(equipmentPage)).thenReturn(enrichedEquipmentPage);
        when(vehicleDetailsRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(completeId, incompleteId)))
                .thenReturn(List.of(completeDetails));

        Page<VehicleSummaryDto> result = service.list(null, null, null, null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().equipmentId()).isEqualTo(completeId);
        assertThat(result.getContent().getFirst().manufactureYear()).isEqualTo(2024);
        assertThat(result.getContent().getFirst().averageDailyUsage()).isEqualTo(220.0);
        assertThat(result.getContent().getFirst().lifetimeCounterType()).isEqualTo(MeterType.MILEAGE_KM);
        assertThat(result.getContent().getFirst().lifetimeLimitValue()).isEqualTo(280_000.0);
        assertThat(result.getContent().getFirst().lifetimeBaselineValue()).isEqualTo(10_000.0);
        assertThat(result.getContent().getFirst().lifetimeWarningPercent()).isEqualTo(12.0);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void listExposesMxikFromEnrichedEquipment() {
        UUID equipmentId = UUID.randomUUID();
        UUID mxikId = UUID.randomUUID();
        Mxik mxik = mxik(mxikId, "8703", "Vehicle");
        Equipment equipment = equipment(equipmentId, "VH-MXIK-LIST", "Truck MXIK List", "INV-VH-MXIK-LIST");
        equipment.setMxikId(mxikId);
        VehicleDetails details = details(equipmentId, "01A788AA", "VIN-MXIK-LIST");
        PageRequest pageRequest = PageRequest.of(0, 20);
        Page<Equipment> equipmentPage = new PageImpl<>(List.of(equipment), pageRequest, 1);
        EquipmentDto enrichedEquipment = EquipmentDto.from(equipment, null, null, null, null, null, null, null,
                null, null, null, false, null, null, MxikRefDto.from(mxik));

        when(vehicleDetailsRepository.searchVehicleEquipment(
                isNull(),
                isNull(),
                isNull(),
                eq(EquipmentCategory.VEHICLE),
                isNull(),
                eq(pageRequest)
        )).thenReturn(equipmentPage);
        when(equipmentService.enrich(equipmentPage))
                .thenReturn(new PageImpl<>(List.of(enrichedEquipment), pageRequest, 1));
        when(vehicleDetailsRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(equipmentId)))
                .thenReturn(List.of(details));

        Page<VehicleSummaryDto> result = service.list(null, null, null, null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().mxikId()).isEqualTo(mxikId);
        assertThat(result.getContent().getFirst().mxik()).isNotNull();
        assertThat(result.getContent().getFirst().mxik().kod()).isEqualTo("8703");
    }

    @Test
    void listSortsByStatusBeforePagination() {
        UUID activeId = UUID.randomUUID();
        UUID repairId = UUID.randomUUID();
        UUID outOfServiceId = UUID.randomUUID();
        Equipment active = equipment(activeId, "VH-ACTIVE", "Active truck", "INV-VH-ACTIVE");
        active.setStatus(EquipmentStatus.ACTIVE);
        Equipment repair = equipment(repairId, "VH-REPAIR", "Repair truck", "INV-VH-REPAIR");
        repair.setStatus(EquipmentStatus.IN_REPAIR);
        Equipment outOfService = equipment(outOfServiceId, "VH-OUT", "Out truck", "INV-VH-OUT");
        outOfService.setStatus(EquipmentStatus.OUT_OF_SERVICE);
        Page<Equipment> equipmentPage = new PageImpl<>(
                List.of(outOfService, active, repair),
                Pageable.unpaged(),
                3
        );
        Page<EquipmentDto> enrichedEquipmentPage = new PageImpl<>(
                List.of(EquipmentDto.from(outOfService), EquipmentDto.from(active), EquipmentDto.from(repair)),
                Pageable.unpaged(),
                3
        );

        when(vehicleDetailsRepository.searchVehicleEquipment(
                isNull(),
                isNull(),
                isNull(),
                eq(EquipmentCategory.VEHICLE),
                isNull(),
                eq(Pageable.unpaged())
        )).thenReturn(equipmentPage);
        when(equipmentService.enrich(equipmentPage)).thenReturn(enrichedEquipmentPage);
        when(vehicleDetailsRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(outOfServiceId, activeId, repairId)))
                .thenReturn(List.of(
                        details(outOfServiceId, "01A901AA", "VIN-OUT"),
                        details(activeId, "01A902AA", "VIN-ACTIVE"),
                        details(repairId, "01A903AA", "VIN-REPAIR")
                ));

        Page<VehicleSummaryDto> result = service.list(null, null, null, null, 0, 20, "status", "asc");

        assertThat(result.getContent()).extracting(VehicleSummaryDto::status)
                .containsExactly(EquipmentStatus.ACTIVE, EquipmentStatus.IN_REPAIR, EquipmentStatus.OUT_OF_SERVICE);
    }


    @Test
    void listSortsByVehicleTypeBeforePagination() {
        UUID truckId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        Equipment truck = equipment(truckId, "VH-TRUCK", "Truck", "INV-VH-TRUCK");
        Equipment passenger = equipment(passengerId, "VH-CAR", "Passenger car", "INV-VH-CAR");
        VehicleDetails truckDetails = details(truckId, "01A921AA", "VIN-TRUCK");
        truckDetails.setVehicleType(VehicleType.TRUCK);
        VehicleDetails passengerDetails = details(passengerId, "01A922AA", "VIN-CAR");
        passengerDetails.setVehicleType(VehicleType.PASSENGER_CAR);
        Page<Equipment> equipmentPage = new PageImpl<>(
                List.of(truck, passenger),
                Pageable.unpaged(),
                2
        );
        Page<EquipmentDto> enrichedEquipmentPage = new PageImpl<>(
                List.of(EquipmentDto.from(truck), EquipmentDto.from(passenger)),
                Pageable.unpaged(),
                2
        );

        when(vehicleDetailsRepository.searchVehicleEquipment(
                isNull(),
                isNull(),
                isNull(),
                eq(EquipmentCategory.VEHICLE),
                isNull(),
                eq(Pageable.unpaged())
        )).thenReturn(equipmentPage);
        when(equipmentService.enrich(equipmentPage)).thenReturn(enrichedEquipmentPage);
        when(vehicleDetailsRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(truckId, passengerId)))
                .thenReturn(List.of(truckDetails, passengerDetails));

        Page<VehicleSummaryDto> result = service.list(null, null, null, null, 0, 20, "vehicleType", "asc");

        assertThat(result.getContent()).extracting(VehicleSummaryDto::vehicleType)
                .containsExactly(VehicleType.PASSENGER_CAR, VehicleType.TRUCK);
    }

    @Test
    void listSortsByInsuranceExpiryDateBeforePagination() {
        UUID earlyId = UUID.randomUUID();
        UUID lateId = UUID.randomUUID();
        Equipment early = equipment(earlyId, "VH-EARLY", "Early insurance", "INV-VH-EARLY");
        Equipment late = equipment(lateId, "VH-LATE", "Late insurance", "INV-VH-LATE");
        VehicleDetails earlyDetails = details(earlyId, "01A911AA", "VIN-EARLY");
        earlyDetails.setInsuranceExpiryDate(LocalDate.of(2026, 1, 15));
        VehicleDetails lateDetails = details(lateId, "01A912AA", "VIN-LATE");
        lateDetails.setInsuranceExpiryDate(LocalDate.of(2026, 12, 15));
        Page<Equipment> equipmentPage = new PageImpl<>(
                List.of(late, early),
                Pageable.unpaged(),
                2
        );
        Page<EquipmentDto> enrichedEquipmentPage = new PageImpl<>(
                List.of(EquipmentDto.from(late), EquipmentDto.from(early)),
                Pageable.unpaged(),
                2
        );

        when(vehicleDetailsRepository.searchVehicleEquipment(
                isNull(),
                isNull(),
                isNull(),
                eq(EquipmentCategory.VEHICLE),
                isNull(),
                eq(Pageable.unpaged())
        )).thenReturn(equipmentPage);
        when(equipmentService.enrich(equipmentPage)).thenReturn(enrichedEquipmentPage);
        when(vehicleDetailsRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(lateId, earlyId)))
                .thenReturn(List.of(lateDetails, earlyDetails));

        Page<VehicleSummaryDto> result = service.list(null, null, null, null, 0, 20, "insuranceExpiryDate", "asc");

        assertThat(result.getContent()).extracting(VehicleSummaryDto::equipmentId)
                .containsExactly(earlyId, lateId);
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
    void attachMultipleDocumentsInOneRequestSucceeds() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID firstFileId = UUID.randomUUID();
        UUID secondFileId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        VehicleDetails details = details(equipmentId, "01A001AA", "VIN-DOC");
        MockMultipartFile firstDocument = document("files", "passport.pdf");
        MockMultipartFile secondDocument = document("files", "insurance.pdf");
        VehicleDocument firstVehicleDocument = vehicleDocument(UUID.randomUUID(), details, uploadedFile(firstFileId, currentUserId), "PASSPORT", "Technical Passport");
        firstVehicleDocument.setDocumentNumber("PAS-2024-001");
        VehicleDocument secondVehicleDocument = vehicleDocument(UUID.randomUUID(), details, uploadedFile(secondFileId, currentUserId), "CERTIFICATE", "Insurance Document");
        secondVehicleDocument.setDocumentNumber("CERT-2024-015");

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(fileService.upload(firstDocument, FileCategory.VEHICLE_DOCUMENT, currentUserId)).thenReturn(uploadResponse(firstFileId, "passport.pdf"));
        when(fileService.upload(secondDocument, FileCategory.VEHICLE_DOCUMENT, currentUserId)).thenReturn(uploadResponse(secondFileId, "insurance.pdf"));
        when(uploadedFileRepository.findByIdAndDeletedFalse(firstFileId)).thenReturn(Optional.of(uploadedFile(firstFileId, currentUserId, "passport.pdf")));
        when(uploadedFileRepository.findByIdAndDeletedFalse(secondFileId)).thenReturn(Optional.of(uploadedFile(secondFileId, currentUserId, "insurance.pdf")));
        when(vehicleDocumentRepository.saveAllAndFlush(anyList())).thenReturn(List.of(firstVehicleDocument, secondVehicleDocument));

        List<VehicleDocumentDto> result = service.attachDocuments(
                equipmentId,
                List.of(firstDocument, secondDocument),
                List.of(" Technical Passport ", "Insurance Document"),
                List.of("PASSPORT", "CERTIFICATE"),
                List.of("PAS-2024-001", "CERT-2024-015"),
                authenticatedUser(currentUserId, equipment.getDepartmentId())
        );

        assertThat(result).hasSize(2);
        assertThat(result).extracting(VehicleDocumentDto::documentType).containsExactly("PASSPORT", "CERTIFICATE");
        assertThat(result).extracting(VehicleDocumentDto::documentNumber).containsExactly("PAS-2024-001", "CERT-2024-015");
        assertThat(result).extracting(VehicleDocumentDto::documentName).containsExactly("Technical Passport", "Insurance Document");
    }

    @Test
    void attachDocumentToMissingVehicleReturnsNotFound() {
        UUID equipmentId = UUID.randomUUID();

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.attachDocuments(
                equipmentId,
                List.of(document()),
                List.of("Technical Passport"),
                null,
                null,
                authenticatedUser(UUID.randomUUID(), UUID.randomUUID())))
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

        assertThatThrownBy(() -> service.attachDocuments(
                equipmentId,
                List.of(document()),
                List.of("Technical Passport"),
                null,
                null,
                authenticatedUser(UUID.randomUUID(), userDepartmentId)))
                .isInstanceOf(RestException.class)
                .hasMessage("Vehicle access denied");

        verify(fileService, never()).upload(any(), any(), any());
    }

    @Test
    void attachDocumentsRejectsEmptyFilesList() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));

        assertThatThrownBy(() -> service.attachDocuments(
                equipmentId,
                List.of(),
                List.of(),
                null,
                null,
                authenticatedUser(currentUserId, equipment.getDepartmentId())))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("At least one vehicle document file is required");

        verify(fileService, never()).upload(any(), any(), any());
    }

    @Test
    void attachDocumentsRejectsMissingDocumentNames() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));

        assertThatThrownBy(() -> service.attachDocuments(
                equipmentId,
                List.of(document()),
                null,
                null,
                null,
                authenticatedUser(currentUserId, equipment.getDepartmentId())))
                .isInstanceOf(RestException.class)
                .hasMessage("documentNames are required for vehicle document uploads");

        verify(fileService, never()).upload(any(), any(), any());
    }

    @Test
    void attachDocumentsRejectsBlankDocumentName() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));

        assertThatThrownBy(() -> service.attachDocuments(
                equipmentId,
                List.of(document()),
                List.of(" "),
                null,
                null,
                authenticatedUser(currentUserId, equipment.getDepartmentId())))
                .isInstanceOf(RestException.class)
                .hasMessage("documentNames[0] must not be blank");

        verify(fileService, never()).upload(any(), any(), any());
    }

    @Test
    void attachDocumentsRejectsDocumentNameCountMismatch() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));

        assertThatThrownBy(() -> service.attachDocuments(
                equipmentId,
                List.of(document("files", "passport.pdf"), document("files", "insurance.pdf")),
                List.of("Technical Passport"),
                null,
                null,
                authenticatedUser(currentUserId, equipment.getDepartmentId())))
                .isInstanceOf(RestException.class)
                .hasMessage("files and documentNames must have the same length");

        verify(fileService, never()).upload(any(), any(), any());
    }

    @Test
    void attachDocumentsRollsBackPreviouslyUploadedFilesIfLaterUploadFails() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID firstFileId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        VehicleDetails details = details(equipmentId, "01A001AA", "VIN-DOC");
        MockMultipartFile firstDocument = document("files", "passport.pdf");
        MockMultipartFile secondDocument = document("files", "insurance.pdf");

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(fileService.upload(firstDocument, FileCategory.VEHICLE_DOCUMENT, currentUserId)).thenReturn(uploadResponse(firstFileId, "passport.pdf"));
        when(uploadedFileRepository.findByIdAndDeletedFalse(firstFileId)).thenReturn(Optional.of(uploadedFile(firstFileId, currentUserId, "passport.pdf")));
        when(fileService.upload(secondDocument, FileCategory.VEHICLE_DOCUMENT, currentUserId)).thenThrow(RestException.badRequest("Invalid file"));
        when(attachmentGroupService.createGroup(eq("Insurance Document"), any(), eq("VEHICLE"), eq(equipmentId),
                any(), any(), eq(List.of(secondDocument)), any(), any()))
                .thenThrow(RestException.badRequest("Invalid file"));

        assertThatThrownBy(() -> service.attachDocuments(
                equipmentId,
                List.of(firstDocument, secondDocument),
                List.of("Technical Passport", "Insurance Document"),
                null,
                null,
                authenticatedUser(currentUserId, equipment.getDepartmentId())))
                .isInstanceOf(RestException.class)
                .hasMessage("Invalid file");

        verify(attachmentGroupService).createGroup(eq("Technical Passport"), any(), eq("VEHICLE"), eq(equipmentId),
                any(), any(), eq(List.of(firstDocument)), any(), any());
    }

    @Test
    void attachDocumentsRollsBackAllUploadedFilesIfDatabaseSaveFails() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID firstFileId = UUID.randomUUID();
        UUID secondFileId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        VehicleDetails details = details(equipmentId, "01A001AA", "VIN-DOC");
        MockMultipartFile firstDocument = document("files", "passport.pdf");
        MockMultipartFile secondDocument = document("files", "insurance.pdf");

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(fileService.upload(firstDocument, FileCategory.VEHICLE_DOCUMENT, currentUserId)).thenReturn(uploadResponse(firstFileId, "passport.pdf"));
        when(fileService.upload(secondDocument, FileCategory.VEHICLE_DOCUMENT, currentUserId)).thenReturn(uploadResponse(secondFileId, "insurance.pdf"));
        when(uploadedFileRepository.findByIdAndDeletedFalse(firstFileId)).thenReturn(Optional.of(uploadedFile(firstFileId, currentUserId, "passport.pdf")));
        when(uploadedFileRepository.findByIdAndDeletedFalse(secondFileId)).thenReturn(Optional.of(uploadedFile(secondFileId, currentUserId, "insurance.pdf")));
        when(vehicleDocumentRepository.saveAllAndFlush(anyList())).thenThrow(new RuntimeException("db"));
        when(attachmentGroupService.createGroup(any(), any(), eq("VEHICLE"), eq(equipmentId),
                any(), any(), anyList(), any(), any()))
                .thenThrow(RestException.badRequest("Could not attach vehicle documents"));

        assertThatThrownBy(() -> service.attachDocuments(
                equipmentId,
                List.of(firstDocument, secondDocument),
                List.of("Technical Passport", "Insurance Document"),
                null,
                null,
                authenticatedUser(currentUserId, equipment.getDepartmentId())))
                .isInstanceOf(RestException.class)
                .hasMessage("Could not attach vehicle documents");

        verify(attachmentGroupService).createGroup(eq("Technical Passport"), any(), eq("VEHICLE"), eq(equipmentId),
                any(), any(), eq(List.of(firstDocument)), any(), any());
    }

    @Test
    void attachDocuments_eachFileGetsItsOwnTypeAndNumber() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID fileId1 = UUID.randomUUID();
        UUID fileId2 = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        VehicleDetails details = details(equipmentId, "01A001AA", "VIN-DOC");
        MockMultipartFile file1 = document("files", "passport.pdf");
        MockMultipartFile file2 = new MockMultipartFile("files", "drawing.png", "image/png", new byte[]{1, 2, 3});
        UploadedFile uploadedFile1 = uploadedFile(fileId1, currentUserId, "passport.pdf");
        UploadedFile uploadedFile2 = uploadedFile(fileId2, currentUserId, "drawing.png");
        uploadedFile2.setContentType("image/png");
        uploadedFile2.setExtension("png");

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(fileService.upload(eq(file1), eq(FileCategory.VEHICLE_DOCUMENT), eq(currentUserId)))
                .thenReturn(uploadResponse(fileId1, "passport.pdf"));
        when(fileService.upload(eq(file2), eq(FileCategory.VEHICLE_DOCUMENT), eq(currentUserId)))
                .thenReturn(uploadResponse(fileId2, "drawing.png"));
        when(uploadedFileRepository.findByIdAndDeletedFalse(fileId1)).thenReturn(Optional.of(uploadedFile1));
        when(uploadedFileRepository.findByIdAndDeletedFalse(fileId2)).thenReturn(Optional.of(uploadedFile2));
        when(vehicleDocumentRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<VehicleDocumentDto> result = service.attachDocuments(
                equipmentId,
                List.of(file1, file2),
                List.of("Technical Passport", "Drawing"),
                List.of("PASSPORT", "DRAWING"),
                List.of("АКТ-2024-001", "DRW-2024-001"),
                authenticatedUser(currentUserId, equipment.getDepartmentId())
        );

        assertThat(result).hasSize(2);
        assertThat(result).extracting(VehicleDocumentDto::documentType).containsExactly("PASSPORT", "DRAWING");
        assertThat(result).extracting(VehicleDocumentDto::documentName).containsExactly("Technical Passport", "Drawing");
    }

    @Test
    void getDocumentsReturnsAllVehicleDocuments() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID firstFileId = UUID.randomUUID();
        UUID secondFileId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(attachmentGroupService.listGroups(eq("VEHICLE"), eq(equipmentId), any()))
                .thenReturn(List.of(
                        attachmentGroup(equipmentId, firstFileId, "Technical Passport", "TECHNICAL", null, "passport.pdf", currentUserId),
                        attachmentGroup(equipmentId, secondFileId, "Insurance Document", "TECHNICAL", null, "insurance.pdf", currentUserId)
                ));

        List<VehicleDocumentDto> result = service.getDocuments(equipmentId, authenticatedUser(currentUserId, equipment.getDepartmentId()));

        assertThat(result).extracting(VehicleDocumentDto::fileId).containsExactly(firstFileId, secondFileId);
        assertThat(result).extracting(VehicleDocumentDto::documentName).containsExactly("Technical Passport", "Insurance Document");
    }

    @Test
    void getSingleDocumentWorks() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(attachmentGroupService.getGroup(eq(documentId), any()))
                .thenReturn(attachmentGroup(documentId, equipmentId, fileId, "Technical Passport", "TECHNICAL", null, "passport.pdf", currentUserId));

        VehicleDocumentDto result = service.getDocument(equipmentId, documentId, authenticatedUser(currentUserId, equipment.getDepartmentId()));

        assertThat(result.id()).isEqualTo(documentId);
        assertThat(result.fileId()).isEqualTo(fileId);
    }

    @Test
    void getDocumentWithoutAttachedFileReturnsNotFound() {
        UUID equipmentId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(attachmentGroupService.getGroup(eq(documentId), any()))
                .thenThrow(RestException.notFound("Vehicle document not found: " + documentId));

        assertThatThrownBy(() -> service.getDocument(equipmentId, documentId, authenticatedUser(UUID.randomUUID(), equipment.getDepartmentId())))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Vehicle document not found");
    }

    @Test
    void getDocumentRejectsNonOwnerThroughFileService() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(attachmentGroupService.getGroup(eq(documentId), any()))
                .thenThrow(RestException.forbidden("File access denied"));

        assertThatThrownBy(() -> service.getDocument(equipmentId, documentId, authenticatedUser(currentUserId, equipment.getDepartmentId())))
                .isInstanceOf(RestException.class)
                .hasMessage("File access denied");
    }

    @Test
    void getDocumentPresignedUrlUnauthorizedPropagatesForbidden() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(attachmentGroupService.getGroup(eq(documentId), any()))
                .thenReturn(attachmentGroup(documentId, equipmentId, fileId, "Technical Passport", null, null, "passport.pdf", UUID.randomUUID()));
        when(attachmentGroupService.getFilePresignedUrl(eq(documentId), eq(fileId), any()))
                .thenThrow(RestException.forbidden("File access denied"));

        assertThatThrownBy(() -> service.getDocumentPresignedUrl(equipmentId, documentId, authenticatedUser(currentUserId, equipment.getDepartmentId())))
                .isInstanceOf(RestException.class)
                .hasMessage("File access denied");
    }

    @Test
    void deleteOneDocumentDoesNotDeleteOthers() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));

        service.deleteDocument(equipmentId, documentId, authenticatedUser(currentUserId, equipment.getDepartmentId()));

        verify(attachmentGroupService).deleteGroup(eq(documentId), any());
    }

    @Test
    void deleteDocumentWithoutAttachedFileReturnsNotFound() {
        UUID equipmentId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        doThrow(RestException.notFound("Vehicle document not found: " + documentId))
                .when(attachmentGroupService).deleteGroup(eq(documentId), any());

        assertThatThrownBy(() -> service.deleteDocument(equipmentId, documentId, authenticatedUser(UUID.randomUUID(), equipment.getDepartmentId())))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Vehicle document not found");
    }

    @Test
    void getDocumentPresignedUrlDelegatesAfterVehicleLookup() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        PresignedUrlResponse response = PresignedUrlResponse.builder()
                .fileId(fileId)
                .url("http://signed")
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(attachmentGroupService.getGroup(eq(documentId), any()))
                .thenReturn(attachmentGroup(documentId, equipmentId, fileId, "Technical Passport", null, null, "passport.pdf", currentUserId));
        when(attachmentGroupService.getFilePresignedUrl(eq(documentId), eq(fileId), any())).thenReturn(response);

        assertThat(service.getDocumentPresignedUrl(equipmentId, documentId, authenticatedUser(currentUserId, equipment.getDepartmentId())))
                .isEqualTo(response);
    }

    @Test
    void oldSingularAttachEndpointDelegatesToPluralLogic() {
        UUID equipmentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "VH-DOC", "Truck", "INV-DOC");
        VehicleDetails details = details(equipmentId, "01A001AA", "VIN-DOC");
        MockMultipartFile document = document();
        VehicleDocument vehicleDocument = vehicleDocument(documentId, details, uploadedFile(fileId, currentUserId), null);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(fileService.upload(document, FileCategory.VEHICLE_DOCUMENT, currentUserId)).thenReturn(uploadResponse(fileId));
        when(uploadedFileRepository.findByIdAndDeletedFalse(fileId)).thenReturn(Optional.of(uploadedFile(fileId, currentUserId)));
        when(vehicleDocumentRepository.saveAllAndFlush(anyList())).thenReturn(List.of(vehicleDocument));
        when(equipmentService.findById(equipmentId)).thenReturn(EquipmentDto.from(equipment));
        when(vehicleDocumentRepository.findAllByEquipmentId(equipmentId))
                .thenReturn(List.of(vehicleDocument));

        VehicleDetailDto result = service.attachDocument(equipmentId, document, currentUserId);

        assertThat(result.vehicleDetails().document().id()).isEqualTo(documentId);
        assertThat(result.vehicleDetails().documents()).hasSize(1);
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



    private static VehicleRequest withGlobalRequirements(VehicleRequest request) {
        return new VehicleRequest(
                request.code(), request.name(), request.inventoryNumber(), request.technicalNumber(),
                request.serialNumber(), request.equipmentTypeId(), request.departmentId(),
                request.locationId(), request.status(), request.plateNumber(), request.plateType(),
                request.vin(), request.brand(), request.model(), request.manufactureYear(),
                request.vehicleType(), request.bodyNumber(), request.chassisNumber(),
                request.engineNumber(), request.fuelType(), request.fuelTankCapacity(),
                request.carryingCapacity(), request.seatCount(), request.assignedDriverId(),
                request.assignedDriverUsageLimitMinutes(), request.currentOdometerKm(),
                request.currentEngineHours(), request.registrationCertificateNumber(),
                request.insurancePolicyNumber(), request.insuranceExpiryDate(),
                request.technicalInspectionExpiryDate(), request.gpsDeviceId(), request.attributes(),
                request.manualAttributes(), request.lifetimeCounterType(), request.lifetimeMeterId(),
                request.lifetimeLimitValue(), request.lifetimeBaselineValue(),
                request.lifetimeWarningPercent(), request.averageDailyUsage(), request.mxikId(),
                request.responsibleId() == null ? UUID.randomUUID() : request.responsibleId(),
                request.criticalityClassId() == null ? UUID.randomUUID() : request.criticalityClassId(),
                request.commissionedAt() == null ? LocalDate.of(2024, 1, 1) : request.commissionedAt()
        );
    }

    private static VehicleRequest fullRequest(
            String code,
            String name,
            String inventoryNumber,
            String plateNumber,
            String vin
    ) {
        return new VehicleRequest(
                null, name, inventoryNumber, null, null, UUID.randomUUID(), UUID.randomUUID(),
                null, EquipmentStatus.ACTIVE, plateNumber, null, vin, "MAN", "TGS", 2022,
                VehicleType.TRUCK, null, null, null, "DIESEL", null, null, null, null,
                null, 0.0, 0.0, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, UUID.randomUUID(),
                UUID.randomUUID(), LocalDate.of(2024, 1, 1)
        );
    }

    private static VehicleRequest withMxik(VehicleRequest request, UUID mxikId) {
        return new VehicleRequest(
                request.code(),
                request.name(),
                request.inventoryNumber(),
                request.technicalNumber(),
                request.serialNumber(),
                request.equipmentTypeId(),
                request.departmentId(),
                request.locationId(),
                request.status(),
                request.plateNumber(),
                request.plateType(),
                request.vin(),
                request.brand(),
                request.model(),
                request.manufactureYear(),
                request.vehicleType(),
                request.bodyNumber(),
                request.chassisNumber(),
                request.engineNumber(),
                request.fuelType(),
                request.fuelTankCapacity(),
                request.carryingCapacity(),
                request.seatCount(),
                request.assignedDriverId(),
                request.assignedDriverUsageLimitMinutes(),
                request.currentOdometerKm(),
                request.currentEngineHours(),
                request.registrationCertificateNumber(),
                request.insurancePolicyNumber(),
                request.insuranceExpiryDate(),
                request.technicalInspectionExpiryDate(),
                request.gpsDeviceId(),
                request.attributes(),
                request.manualAttributes(),
                request.lifetimeCounterType(),
                request.lifetimeMeterId(),
                request.lifetimeLimitValue(),
                request.lifetimeBaselineValue(),
                request.lifetimeWarningPercent(),
                request.averageDailyUsage(),
                mxikId,
                request.responsibleId(),
                request.criticalityClassId(),
                request.commissionedAt()
        );
    }

    private static VehicleRequest withCurrentOdometer(VehicleRequest request, Double currentOdometerKm) {
        return new VehicleRequest(
                request.code(), request.name(), request.inventoryNumber(), request.technicalNumber(),
                request.serialNumber(), request.equipmentTypeId(), request.departmentId(),
                request.locationId(), request.status(), request.plateNumber(), request.plateType(),
                request.vin(), request.brand(), request.model(), request.manufactureYear(),
                request.vehicleType(), request.bodyNumber(), request.chassisNumber(),
                request.engineNumber(), request.fuelType(), request.fuelTankCapacity(),
                request.carryingCapacity(), request.seatCount(), request.assignedDriverId(),
                request.assignedDriverUsageLimitMinutes(), currentOdometerKm,
                request.currentEngineHours(), request.registrationCertificateNumber(),
                request.insurancePolicyNumber(), request.insuranceExpiryDate(),
                request.technicalInspectionExpiryDate(), request.gpsDeviceId(), request.attributes(),
                request.manualAttributes(), request.lifetimeCounterType(), request.lifetimeMeterId(),
                request.lifetimeLimitValue(), request.lifetimeBaselineValue(),
                request.lifetimeWarningPercent(), request.averageDailyUsage(), request.mxikId(),
                request.responsibleId(), request.criticalityClassId(), request.commissionedAt()
        );
    }

    private static VehicleRequest withEquipmentUsage(
            VehicleRequest request,
            MeterType lifetimeCounterType,
            Double lifetimeLimitValue,
            Double lifetimeBaselineValue,
            Double lifetimeWarningPercent,
            Double averageDailyUsage
    ) {
        return new VehicleRequest(
                request.code(),
                request.name(),
                request.inventoryNumber(),
                request.technicalNumber(),
                request.serialNumber(),
                request.equipmentTypeId(),
                request.departmentId(),
                request.locationId(),
                request.status(),
                request.plateNumber(),
                request.plateType(),
                request.vin(),
                request.brand(),
                request.model(),
                request.manufactureYear(),
                request.vehicleType(),
                request.bodyNumber(),
                request.chassisNumber(),
                request.engineNumber(),
                request.fuelType(),
                request.fuelTankCapacity(),
                request.carryingCapacity(),
                request.seatCount(),
                request.assignedDriverId(),
                request.assignedDriverUsageLimitMinutes(),
                request.currentOdometerKm(),
                request.currentEngineHours(),
                request.registrationCertificateNumber(),
                request.insurancePolicyNumber(),
                request.insuranceExpiryDate(),
                request.technicalInspectionExpiryDate(),
                request.gpsDeviceId(),
                request.attributes(),
                request.manualAttributes(),
                lifetimeCounterType,
                null,
                lifetimeLimitValue,
                lifetimeBaselineValue,
                lifetimeWarningPercent,
                averageDailyUsage,
                request.mxikId(),
                request.responsibleId(),
                request.criticalityClassId(),
                request.commissionedAt()
        );
    }

    private static VehicleRequest withClientCode(VehicleRequest request, String code) {
        return withGlobalRequirements(new VehicleRequest(
                code,
                request.name(),
                request.inventoryNumber(),
                request.technicalNumber(),
                request.serialNumber(),
                request.equipmentTypeId(),
                request.departmentId(),
                request.locationId(),
                request.status(),
                request.plateNumber(),
                request.vin(),
                request.brand(),
                request.model(),
                request.manufactureYear(),
                request.vehicleType(),
                request.bodyNumber(),
                request.chassisNumber(),
                request.engineNumber(),
                request.fuelType(),
                request.fuelTankCapacity(),
                request.carryingCapacity(),
                request.seatCount(),
                request.assignedDriverId(),
                request.currentOdometerKm(),
                request.currentEngineHours(),
                request.registrationCertificateNumber(),
                request.insurancePolicyNumber(),
                request.insuranceExpiryDate(),
                request.technicalInspectionExpiryDate(),
                request.gpsDeviceId(),
                request.attributes(),
                request.manualAttributes()
        ));
    }

    private static VehicleRequest withManualAttributes(
            VehicleRequest request,
            List<EquipmentManualAttributeRequest> manualAttributes
    ) {
        return withGlobalRequirements(new VehicleRequest(
                request.code(),
                request.name(),
                request.inventoryNumber(),
                request.technicalNumber(),
                request.serialNumber(),
                request.equipmentTypeId(),
                request.departmentId(),
                request.locationId(),
                request.status(),
                request.plateNumber(),
                request.vin(),
                request.brand(),
                request.model(),
                request.manufactureYear(),
                request.vehicleType(),
                request.bodyNumber(),
                request.chassisNumber(),
                request.engineNumber(),
                request.fuelType(),
                request.fuelTankCapacity(),
                request.carryingCapacity(),
                request.seatCount(),
                request.assignedDriverId(),
                request.currentOdometerKm(),
                request.currentEngineHours(),
                request.registrationCertificateNumber(),
                request.insurancePolicyNumber(),
                request.insuranceExpiryDate(),
                request.technicalInspectionExpiryDate(),
                request.gpsDeviceId(),
                request.attributes(),
                manualAttributes
        ));
    }

    private static VehicleRequest withPlateType(
            VehicleRequest request,
            VehicleRegistrationPlateType plateType
    ) {
        return withGlobalRequirements(new VehicleRequest(
                request.code(),
                request.name(),
                request.inventoryNumber(),
                request.technicalNumber(),
                request.serialNumber(),
                request.equipmentTypeId(),
                request.departmentId(),
                request.locationId(),
                request.status(),
                request.plateNumber(),
                plateType,
                request.vin(),
                request.brand(),
                request.model(),
                request.manufactureYear(),
                request.vehicleType(),
                request.bodyNumber(),
                request.chassisNumber(),
                request.engineNumber(),
                request.fuelType(),
                request.fuelTankCapacity(),
                request.carryingCapacity(),
                request.seatCount(),
                request.assignedDriverId(),
                request.currentOdometerKm(),
                request.currentEngineHours(),
                request.registrationCertificateNumber(),
                request.insurancePolicyNumber(),
                request.insuranceExpiryDate(),
                request.technicalInspectionExpiryDate(),
                request.gpsDeviceId(),
                request.attributes(),
                request.manualAttributes()
        ));
    }

    private static VehicleRequest withEquipmentTypeAndAttributes(
            VehicleRequest request,
            UUID equipmentTypeId,
            List<EquipmentAttributeValueRequest> attributes
    ) {
        return withGlobalRequirements(new VehicleRequest(
                request.code(),
                request.name(),
                request.inventoryNumber(),
                request.technicalNumber(),
                request.serialNumber(),
                equipmentTypeId,
                request.departmentId(),
                request.locationId(),
                request.status(),
                request.plateNumber(),
                request.vin(),
                request.brand(),
                request.model(),
                request.manufactureYear(),
                request.vehicleType(),
                request.bodyNumber(),
                request.chassisNumber(),
                request.engineNumber(),
                request.fuelType(),
                request.fuelTankCapacity(),
                request.carryingCapacity(),
                request.seatCount(),
                request.assignedDriverId(),
                request.currentOdometerKm(),
                request.currentEngineHours(),
                request.registrationCertificateNumber(),
                request.insurancePolicyNumber(),
                request.insuranceExpiryDate(),
                request.technicalInspectionExpiryDate(),
                request.gpsDeviceId(),
                attributes,
                request.manualAttributes()
        ));
    }

    private static EquipmentAttributeValueDto attributeValue(UUID equipmentId, String key, Double value) {
        return new EquipmentAttributeValueDto(
                UUID.randomUUID(),
                equipmentId,
                UUID.randomUUID(),
                key,
                "Payload capacity",
                "Грузоподъемность",
                "Yuk ko'tarish",
                EquipmentAttributeDataType.NUMBER,
                "kg",
                false,
                null,
                List.of(),
                "vehicle_metrics",
                10,
                null,
                value,
                null,
                null,
                null,
                null
        );
    }

    private static EquipmentManualAttributeDto manualAttribute(String key, String value) {
        return new EquipmentManualAttributeDto(UUID.randomUUID(), key, value);
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

    private static Mxik mxik(UUID id, String kod, String name) {
        Mxik mxik = new Mxik();
        mxik.setId(id);
        mxik.setKod(kod);
        mxik.setName(name);
        mxik.setType("VEHICLE");
        return mxik;
    }

    private static UploadedFile uploadedFile(UUID id, UUID uploadedBy) {
        return uploadedFile(id, uploadedBy, "vehicle-passport.pdf");
    }

    private static UploadedFile uploadedFile(UUID id, UUID uploadedBy, String originalName) {
        return UploadedFile.builder()
                .id(id)
                .originalName(originalName)
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
        return uploadResponse(fileId, "vehicle-passport.pdf");
    }

    private static UploadFileResponse uploadResponse(UUID fileId, String originalName) {
        return UploadFileResponse.builder()
                .id(fileId)
                .originalName(originalName)
                .storedName(fileId + ".pdf")
                .contentType("application/pdf")
                .extension("pdf")
                .size(123L)
                .category(FileCategory.VEHICLE_DOCUMENT)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static MockMultipartFile document() {
        return document("document", "vehicle-passport.pdf");
    }

    private static MockMultipartFile document(String paramName, String originalName) {
        return new MockMultipartFile(paramName, originalName, "application/pdf", "%PDF-1.4\n".getBytes());
    }

    private static VehicleDocument vehicleDocument(UUID id, VehicleDetails details, UploadedFile file, String documentType) {
        return vehicleDocument(id, details, file, documentType, file == null ? "Vehicle document" : file.getOriginalName());
    }

    private static VehicleDocument vehicleDocument(
            UUID id,
            VehicleDetails details,
            UploadedFile file,
            String documentType,
            String documentName
    ) {
        VehicleDocument document = VehicleDocument.builder()
                .vehicleDetails(details)
                .file(file)
                .documentType(documentType)
                .documentName(documentName)
                .createdAt(LocalDateTime.now())
                .build();
        document.setId(id);
        return document;
    }

    private static AttachmentGroupDto attachmentGroup(
            UUID targetId,
            UUID fileId,
            String title,
            String documentType,
            String documentNumber,
            String originalName,
            UUID uploadedBy
    ) {
        return attachmentGroup(UUID.randomUUID(), targetId, fileId, title, documentType, documentNumber, originalName, uploadedBy);
    }

    private static AttachmentGroupDto attachmentGroup(
            UUID groupId,
            UUID targetId,
            UUID fileId,
            String title,
            String documentType,
            String documentNumber,
            String originalName,
            UUID uploadedBy
    ) {
        return new AttachmentGroupDto(
                groupId,
                title,
                null,
                AttachmentTargetType.VEHICLE,
                targetId,
                documentType,
                documentNumber,
                uploadedBy,
                LocalDateTime.now(),
                List.of(new AttachmentGroupDto.FileItem(
                        UUID.randomUUID(),
                        fileId,
                        originalName,
                        fileId + ".pdf",
                        originalName != null && originalName.endsWith(".png") ? "image/png" : "application/pdf",
                        123L,
                        0,
                        null,
                        uploadedBy,
                        LocalDateTime.now(),
                        "/download",
                        "/presigned"
                ))
        );
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
