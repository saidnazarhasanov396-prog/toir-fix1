package com.toir.dto.repairrequest;

import jakarta.validation.constraints.NotBlank;

public record CloseRequestRequest(
        @NotBlank String closeResult
) {}
