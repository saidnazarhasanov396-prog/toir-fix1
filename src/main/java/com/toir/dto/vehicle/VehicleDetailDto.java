package com.toir.dto.vehicle;

import com.toir.dto.equipment.EquipmentDto;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.enums.VehicleType;

import java.time.LocalDate;
import java.util.UUID;

public record VehicleDetailDto(
        EquipmentDto equipment,
        Details vehicleDetails
) {
    public record Details(
            UUID id,
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
            double currentOdometerKm,
            double currentEngineHours,
            String registrationCertificateNumber,
            String insurancePolicyNumber,
            LocalDate insuranceExpiryDate,
            LocalDate technicalInspectionExpiryDate,
            String gpsDeviceId
    ) {
    }

    public static VehicleDetailDto from(EquipmentDto equipment, VehicleDetails details) {
        return new VehicleDetailDto(
                equipment,
                new Details(
                        details.getId(),
                        details.getPlateNumber(),
                        details.getVin(),
                        details.getBrand(),
                        details.getModel(),
                        details.getManufactureYear(),
                        details.getVehicleType(),
                        details.getBodyNumber(),
                        details.getChassisNumber(),
                        details.getEngineNumber(),
                        details.getFuelType(),
                        details.getFuelTankCapacity(),
                        details.getCarryingCapacity(),
                        details.getSeatCount(),
                        details.getAssignedDriverId(),
                        details.getCurrentOdometerKm(),
                        details.getCurrentEngineHours(),
                        details.getRegistrationCertificateNumber(),
                        details.getInsurancePolicyNumber(),
                        details.getInsuranceExpiryDate(),
                        details.getTechnicalInspectionExpiryDate(),
                        details.getGpsDeviceId()
                )
        );
    }
}
