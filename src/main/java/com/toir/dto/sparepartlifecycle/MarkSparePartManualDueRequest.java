package com.toir.dto.sparepartlifecycle;

import jakarta.validation.constraints.NotBlank;

public record MarkSparePartManualDueRequest(@NotBlank String reason) { }
