package com.toir.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WorkOrderDraftTextRequest(
        @NotNull
        @JsonProperty("equipment_id")
        UUID equipmentId,
        @NotBlank
        String text
) {
}
