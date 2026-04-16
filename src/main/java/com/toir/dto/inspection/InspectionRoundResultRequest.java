package com.toir.dto.inspection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record InspectionRoundResultRequest(
        @NotNull UUID checkpointId,
        @NotBlank String status,
        Double measuredValue,
        String measuredUnit,
        String comment,
        List<UUID> photoFileIds
) {}
