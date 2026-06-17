package com.toir.dto.approval;

import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalTargetType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ApprovalStartRequest(
        @NotNull ApprovalTargetType targetType,
        @NotNull UUID targetId,
        @NotNull ApprovalActionType actionType,
        String comment
) {
}
