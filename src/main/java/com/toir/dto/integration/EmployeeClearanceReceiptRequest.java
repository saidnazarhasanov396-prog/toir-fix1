package com.toir.dto.integration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** A reviewed TOIR clearance result for one canonical employee and offboarding revision. */
public record EmployeeClearanceReceiptRequest(
        @NotNull UUID employeeId,
        boolean cleared,
        @PositiveOrZero long sourceRevision,
        @Size(max = 240) String evidenceReference
) {
}
