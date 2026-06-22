package com.toir.dto.equipmentcommissioning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EquipmentCommissioningActRequest(
        @NotNull UUID equipmentId,
        @NotNull UUID sourceWarehouseId,
        UUID warehouseItemId,
        @NotNull UUID targetDepartmentId,
        UUID targetLocationId,
        @NotNull UUID responsibleEmployeeId,
        @NotBlank String actNumber,
        @NotNull LocalDate actDate,
        @NotNull LocalDate commissionedAt,
        @NotNull LocalDate operationStartDate,
        List<Signatory> committee,
        String notes
) {
    public record Signatory(UUID employeeId, String name, String role) {
    }
}
