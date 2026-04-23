package com.toir.dto.serviceclass;

import com.toir.entity.ServiceClass;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record ServiceClassDto(
        UUID id,
        @NotBlank String code,
        @NotBlank String name,
        String description
) {
    public static ServiceClassDto from(ServiceClass s) {
        return new ServiceClassDto(s.getId(), s.getCode(), s.getName(), s.getDescription());
    }
}
