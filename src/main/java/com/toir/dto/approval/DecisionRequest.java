package com.toir.dto.approval;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DecisionRequest(
        @NotNull UUID approverId,
        String comment
) {}
