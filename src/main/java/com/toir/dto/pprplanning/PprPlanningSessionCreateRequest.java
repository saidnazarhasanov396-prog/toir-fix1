package com.toir.dto.pprplanning;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record PprPlanningSessionCreateRequest(
        @NotBlank String name,
        @Min(2000) @Max(2200) int year,
        @NotNull UUID departmentId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        String notes
) {
    @AssertTrue(message = "Planning dates must belong to the selected year")
    public boolean isAnnualBoundaryValid() {
        return startDate == null || endDate == null
                || !startDate.isAfter(endDate)
                && startDate.getYear() == year
                && endDate.getYear() == year;
    }
}
