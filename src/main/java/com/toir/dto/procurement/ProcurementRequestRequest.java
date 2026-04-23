package com.toir.dto.procurement;
import com.toir.dto.procurement.ProcurementLineRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProcurementRequestRequest(
        @NotBlank String title,
        String description,
        UUID departmentId,
        UUID warehouseId,
        LocalDate requiredBy,
        @Valid List<ProcurementLineRequest> lines
) {}
