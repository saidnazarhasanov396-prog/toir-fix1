package com.toir.dto.rootcause;

import com.toir.entity.RootCause;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record RootCauseDto(
        UUID id,
        String code,
        @NotBlank String name,
        String description
) {
    public static RootCauseDto from(RootCause r) {
        return new RootCauseDto(r.getId(), r.getCode(), r.getName(), r.getDescription());
    }
}
