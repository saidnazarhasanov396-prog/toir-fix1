package com.toir.dto.vehicle;

import com.toir.dto.equipment.EquipmentDto;
import com.toir.entity.VehicleDetails;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.VehicleType;

import java.time.LocalDate;
import java.util.UUID;

public record VehicleSummaryDto(
        UUID equipmentId,
        String code,
        String name,
        String inventoryNumber,
        EquipmentStatus status,
        EquipmentDto.Ref department,
        EquipmentDto.Ref location,
        String plateNumber,
        String vin,
        String brand,
        String model,
        VehicleType vehicleType,
        UUID assignedDriverId,
        double currentOdometerKm,
        double currentEngineHours,
        LocalDate insuranceExpiryDate,
        LocalDate technicalInspectionExpiryDate
) {
    public static VehicleSummaryDto from(EquipmentDto equipment, VehicleDetails details) {
        return new VehicleSummaryDto(
                equipment.id(),
                equipment.code(),
                equipment.name(),
                equipment.inventoryNumber(),
                equipment.status(),
                equipment.department(),
                equipment.location(),
                details.getPlateNumber(),
                details.getVin(),
                details.getBrand(),
                details.getModel(),
                details.getVehicleType(),
                details.getAssignedDriverId(),
                details.getCurrentOdometerKm(),
                details.getCurrentEngineHours(),
                details.getInsuranceExpiryDate(),
                details.getTechnicalInspectionExpiryDate()
        );
    }
}
