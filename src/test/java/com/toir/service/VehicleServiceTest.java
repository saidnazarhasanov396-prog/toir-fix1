package com.toir.service;

import com.toir.dto.equipment.EquipmentDto;
import com.toir.dto.vehicle.VehicleDetailDto;
import com.toir.dto.vehicle.VehicleRequest;
import com.toir.dto.vehicle.VehicleSummaryDto;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.VehicleType;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.service.equipment.EquipmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    VehicleDetailsRepository vehicleDetailsRepository;

    @Mock
    EquipmentService equipmentService;

    @InjectMocks
    VehicleService service;

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

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(equipmentRepository.findByCodeAndIsDeletedFalse("VH-041")).thenReturn(Optional.empty());
        when(equipmentRepository.findByInventoryNumberAndIsDeletedFalse("INV-VH-041")).thenReturn(Optional.empty());
        when(vehicleDetailsRepository.findByPlateNumberAndIsDeletedFalse("01A041AA")).thenReturn(Optional.empty());
        when(vehicleDetailsRepository.findByVinAndIsDeletedFalse("VIN-041")).thenReturn(Optional.empty());
        when(equipmentService.findById(equipmentId)).thenAnswer(invocation -> EquipmentDto.from(equipment));

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

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(equipmentService.findById(equipmentId)).thenAnswer(invocation -> EquipmentDto.from(equipment));

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

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(equipmentService.findById(equipmentId)).thenAnswer(invocation -> EquipmentDto.from(equipment));

        VehicleDetailDto result = service.update(equipmentId, request);

        assertThat(result.equipment().name()).isEqualTo("Truck 020 Updated");
        assertThat(result.vehicleDetails().plateNumber()).isEqualTo("01A020AA");
        verify(equipmentRepository, never()).findByCodeAndIsDeletedFalse("VH-020");
        verify(equipmentRepository, never()).findByInventoryNumberAndIsDeletedFalse("INV-VH-020");
        verify(vehicleDetailsRepository, never()).findByPlateNumberAndIsDeletedFalse("01A020AA");
        verify(vehicleDetailsRepository, never()).findByVinAndIsDeletedFalse("VIN-020");
    }

    @Test
    void listSkipsVehicleEquipmentRowsWithoutDetails() {
        UUID completeId = UUID.randomUUID();
        UUID incompleteId = UUID.randomUUID();
        Equipment complete = equipment(completeId, "VH-030", "Truck 030", "INV-VH-030");
        Equipment incomplete = equipment(incompleteId, "VH-031", "Truck 031", "INV-VH-031");
        VehicleDetails completeDetails = details(completeId, "01A030AA", "VIN-030");
        PageRequest pageRequest = PageRequest.of(0, 20);

        when(equipmentService.search(null, null, null, EquipmentCategory.VEHICLE, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(EquipmentDto.from(complete), EquipmentDto.from(incomplete)), pageRequest, 2));
        when(vehicleDetailsRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(completeId, incompleteId)))
                .thenReturn(List.of(completeDetails));

        Page<VehicleSummaryDto> result = service.list(null, null, null, 1, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).equipmentId()).isEqualTo(completeId);
        assertThat(result.getTotalElements()).isEqualTo(2);
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
}
