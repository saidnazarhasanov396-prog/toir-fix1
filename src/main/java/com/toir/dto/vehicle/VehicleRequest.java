package com.toir.dto.vehicle;

import com.toir.dto.equipmentattribute.EquipmentAttributeValueRequest;
import com.toir.dto.equipmentmanualattribute.EquipmentManualAttributeRequest;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record VehicleRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String inventoryNumber,
        String technicalNumber,
        String serialNumber,
        @NotNull UUID equipmentTypeId,
        @NotNull UUID departmentId,
        UUID locationId,
        EquipmentStatus status,
        @NotBlank String plateNumber,
        String vin,
        String brand,
        String model,
        Integer manufactureYear,
        @NotNull VehicleType vehicleType,
        String bodyNumber,
        String chassisNumber,
        String engineNumber,
        String fuelType,
        Double fuelTankCapacity,
        Double carryingCapacity,
        Integer seatCount,
        UUID assignedDriverId,
        Double currentOdometerKm,
        Double currentEngineHours,
        String registrationCertificateNumber,
        String insurancePolicyNumber,
        LocalDate insuranceExpiryDate,
        LocalDate technicalInspectionExpiryDate,
        String gpsDeviceId,
        List<EquipmentAttributeValueRequest> attributes,
        List<EquipmentManualAttributeRequest> manualAttributes
) {
    public VehicleRequest(
            String code,
            String name,
            String inventoryNumber,
            String technicalNumber,
            String serialNumber,
            UUID equipmentTypeId,
            UUID departmentId,
            UUID locationId,
            EquipmentStatus status,
            String plateNumber,
            String vin,
            String brand,
            String model,
            Integer manufactureYear,
            VehicleType vehicleType,
            String bodyNumber,
            String chassisNumber,
            String engineNumber,
            String fuelType,
            Double fuelTankCapacity,
            Double carryingCapacity,
            Integer seatCount,
            UUID assignedDriverId,
            Double currentOdometerKm,
            Double currentEngineHours,
            String registrationCertificateNumber,
            String insurancePolicyNumber,
            LocalDate insuranceExpiryDate,
            LocalDate technicalInspectionExpiryDate,
            String gpsDeviceId
    ) {
        this(code, name, inventoryNumber, technicalNumber, serialNumber, equipmentTypeId, departmentId,
                locationId, status, plateNumber, vin, brand, model, manufactureYear, vehicleType,
                bodyNumber, chassisNumber, engineNumber, fuelType, fuelTankCapacity, carryingCapacity,
                seatCount, assignedDriverId, currentOdometerKm, currentEngineHours,
                registrationCertificateNumber, insurancePolicyNumber, insuranceExpiryDate,
                technicalInspectionExpiryDate, gpsDeviceId, null, null);
    }

    public static VehicleRequest minimal(String code, String name, String inventoryNumber,
                                         UUID equipmentTypeId, UUID departmentId,
                                         String plateNumber, VehicleType vehicleType) {
        return new VehicleRequest(
                code, name, inventoryNumber, null, null, equipmentTypeId, departmentId,
                null, EquipmentStatus.ACTIVE, plateNumber, null, null, null, null,
                vehicleType, null, null, null, null, null, null, null, null,
                0.0, 0.0, null, null, null, null, null
        );
    }

    public VehicleRequest withAttributes(List<EquipmentAttributeValueRequest> attributes) {
        return new VehicleRequest(
                code,
                name,
                inventoryNumber,
                technicalNumber,
                serialNumber,
                equipmentTypeId,
                departmentId,
                locationId,
                status,
                plateNumber,
                vin,
                brand,
                model,
                manufactureYear,
                vehicleType,
                bodyNumber,
                chassisNumber,
                engineNumber,
                fuelType,
                fuelTankCapacity,
                carryingCapacity,
                seatCount,
                assignedDriverId,
                currentOdometerKm,
                currentEngineHours,
                registrationCertificateNumber,
                insurancePolicyNumber,
                insuranceExpiryDate,
                technicalInspectionExpiryDate,
                gpsDeviceId,
                attributes,
                manualAttributes
        );
    }
}
