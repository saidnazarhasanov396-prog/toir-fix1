package com.toir.dto.budget;

import com.toir.enums.BudgetStatus;
import com.toir.entity.projects.MaintenanceBudget;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MaintenanceBudgetDto(
        UUID id,
        @NotNull @Min(2000) Integer year,
        Integer month,
        UUID departmentId,
        String departmentName,
        BudgetStatus status,
        double totalPlanned,
        double totalActual,
        List<BudgetLineDto> lines
) {
    public static MaintenanceBudgetDto from(MaintenanceBudget b) {
        return from(b, null, Map.of());
    }

    public static MaintenanceBudgetDto from(MaintenanceBudget b, String departmentName) {
        return from(b, departmentName, Map.of());
    }

    public static MaintenanceBudgetDto from(
            MaintenanceBudget b,
            String departmentName,
            Map<UUID, String> costCategoryNames
    ) {
        Map<UUID, String> safeNames = costCategoryNames != null ? costCategoryNames : Map.of();
        return new MaintenanceBudgetDto(
                b.getId(),
                b.getYear(),
                b.getMonth(),
                b.getDepartmentId(),
                departmentName,
                b.getStatus(),
                b.getTotalPlanned(),
                b.getTotalActual(),
                b.getLines().stream()
                        .map(line -> BudgetLineDto.from(line, safeNames.get(line.getCostCategoryId())))
                        .toList()
        );
    }
}
