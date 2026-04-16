package com.toir.approval.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DecisionRequest(
        @NotNull UUID approverId,
        String comment
) {}
