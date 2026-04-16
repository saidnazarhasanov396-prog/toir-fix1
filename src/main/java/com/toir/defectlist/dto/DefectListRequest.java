package com.toir.defectlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DefectListRequest(
        @NotBlank String code,
        @NotBlank String title,
        @NotNull UUID equipmentId,
        UUID repairRequestId,
        UUID workOrderId,
        @NotNull UUID createdById,
        String notes
) {}
