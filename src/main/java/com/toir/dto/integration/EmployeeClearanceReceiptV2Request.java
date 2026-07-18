package com.toir.dto.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** A reviewed TOIR clearance result bound to a single ERP offboarding case. */
public record EmployeeClearanceReceiptV2Request(
        @NotNull UUID employeeId,
        @NotNull UUID offboardingCaseId,
        @NotNull Boolean cleared,
        @NotNull @PositiveOrZero Long sourceRevision,
        @NotBlank @Size(max = 240) String evidenceReference
) {
}
