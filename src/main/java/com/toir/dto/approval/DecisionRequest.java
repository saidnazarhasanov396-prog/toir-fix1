package com.toir.dto.approval;

import java.util.UUID;

public record DecisionRequest(
        UUID approverId,
        String comment
) {}
