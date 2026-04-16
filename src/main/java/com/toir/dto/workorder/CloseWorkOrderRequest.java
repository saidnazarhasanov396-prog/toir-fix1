package com.toir.dto.workorder;

import jakarta.validation.constraints.NotBlank;

public record CloseWorkOrderRequest(
        @NotBlank String result,
        String closureNotes
) {}
