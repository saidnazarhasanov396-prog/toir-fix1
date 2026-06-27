package com.toir.dto.integration.atil;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record AtilVehicleUpsertRequest(
        @NotNull UUID vehicleId,
        UUID equipmentId,
        @NotBlank String plateNumber,
        String vin,
        String garageNumber,
        String refId,
        String inventoryNumber,
        String brand,
        String model,
        Integer manufactureYear,
        String vehicleType,
        String vehicleCategory,
        @NotNull UUID equipmentTypeId,
        @NotNull UUID departmentId,
        UUID locationId,
        UUID assignedDriverId,
        String gpsDeviceId,
        Double currentOdometerKm,
        Double currentEngineHours,
        LocalDate commissionedAt,
        String status
) {}
