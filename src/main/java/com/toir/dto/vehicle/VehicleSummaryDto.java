package com.toir.dto.vehicle;

import com.toir.dto.equipment.EquipmentDto;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.MeterType;
import com.toir.enums.VehicleRegistrationPlateType;
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
        VehicleRegistrationPlateType plateType,
        String vin,
        String brand,
        String model,
        VehicleType vehicleType,
        UUID assignedDriverId,
        Integer assignedDriverUsageLimitMinutes,
        Integer manufactureYear,
        Double averageDailyUsage,
        MeterType lifetimeCounterType,
        Double lifetimeLimitValue,
        Double lifetimeBaselineValue,
        Double lifetimeWarningPercent,
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
                details.getPlateType(),
                details.getVin(),
                details.getBrand(),
                details.getModel(),
                details.getVehicleType(),
                details.getAssignedDriverId(),
                details.getAssignedDriverUsageLimitMinutes(),
                details.getManufactureYear() != null ? details.getManufactureYear() : equipment.producedYear(),
                equipment.averageDailyUsage(),
                equipment.lifetimeCounterType(),
                equipment.lifetimeLimitValue(),
                equipment.lifetimeBaselineValue(),
                equipment.lifetimeWarningPercent(),
                details.getCurrentOdometerKm(),
                details.getCurrentEngineHours(),
                details.getInsuranceExpiryDate(),
                details.getTechnicalInspectionExpiryDate()
        );
    }
}
