package com.toir.dto.workorder;

import jakarta.validation.constraints.NotBlank;

public record CompleteWorkOrderRequest(
        @NotBlank String result,
        String summary
) {}
