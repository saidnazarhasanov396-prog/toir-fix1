package com.toir.dto.failurereason;

import com.toir.entity.FailureReason;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record FailureReasonDto(
        UUID id,
        String code,
        @NotBlank String name,
        String description
) {
    public static FailureReasonDto from(FailureReason f) {
        return new FailureReasonDto(f.getId(), f.getCode(), f.getName(), f.getDescription());
    }
}
