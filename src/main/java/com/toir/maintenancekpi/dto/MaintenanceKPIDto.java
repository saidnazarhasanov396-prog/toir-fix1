package com.toir.maintenancekpi.dto;

import com.toir.maintenancekpi.MaintenanceKPI;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record MaintenanceKPIDto(
        UUID id,
        @NotNull UUID departmentId,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        Integer pprPlannedCount,
        Integer pprCompletedCount,
        Double pprCompletionRate,
        Double unplannedRepairShare,
        Double averageRepairDurationHours,
        Double totalCost
) {
    public static MaintenanceKPIDto from(MaintenanceKPI k) {
        return new MaintenanceKPIDto(k.getId(), k.getDepartmentId(), k.getPeriodStart(), k.getPeriodEnd(),
                k.getPprPlannedCount(), k.getPprCompletedCount(), k.getPprCompletionRate(),
                k.getUnplannedRepairShare(), k.getAverageRepairDurationHours(), k.getTotalCost());
    }
}
