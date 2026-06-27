package com.toir.dto.mxik;

import jakarta.validation.constraints.NotBlank;

public record MxikRequest(
        @NotBlank String name,
        @NotBlank String kod,
        @NotBlank String type,
        String groupName,
        String positionName
) {
}
