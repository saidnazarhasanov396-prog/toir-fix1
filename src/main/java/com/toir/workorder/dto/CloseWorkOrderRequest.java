package com.toir.workorder.dto;

import jakarta.validation.constraints.NotBlank;

public record CloseWorkOrderRequest(
        @NotBlank String result,
        String closureNotes
) {}
