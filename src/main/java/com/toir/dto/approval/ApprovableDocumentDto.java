package com.toir.dto.approval;

import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;

import java.time.Instant;
import java.util.UUID;

public record ApprovableDocumentDto(
        UUID id,
        ApprovalTargetType type,
        String code,
        String name,
        ApprovalStatus approvalStatus,
        UUID approvalId,
        Instant createdAt
) {
}
