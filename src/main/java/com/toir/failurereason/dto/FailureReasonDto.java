package com.toir.failurereason.dto;

import com.toir.failurereason.FailureReason;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record FailureReasonDto(
        UUID id,
        @NotBlank String code,
        @NotBlank String name,
        String description
) {
    public static FailureReasonDto from(FailureReason f) {
        return new FailureReasonDto(f.getId(), f.getCode(), f.getName(), f.getDescription());
    }
}
