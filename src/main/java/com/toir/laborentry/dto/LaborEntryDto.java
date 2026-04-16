package com.toir.laborentry.dto;

import com.toir.laborentry.LaborEntry;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.util.UUID;

public record LaborEntryDto(
        UUID id,
        UUID workOrderId,
        UUID userId,
        String contractorName,
        @NotNull LocalDate workDate,
        @PositiveOrZero double hours,
        Double rate,
        String description
) {
    public static LaborEntryDto from(LaborEntry l) {
        return new LaborEntryDto(l.getId(), l.getWorkOrderId(), l.getUserId(), l.getContractorName(),
                l.getWorkDate(), l.getHours(), l.getRate(), l.getDescription());
    }
}
