package com.toir.repairrequest.dto;

import jakarta.validation.constraints.NotBlank;

public record CloseRequestRequest(
        @NotBlank String closeResult
) {}
