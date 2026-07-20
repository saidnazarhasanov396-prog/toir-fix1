package com.toir.dto.operationalissue;

import com.toir.enums.OperationalIssueTargetType;
import java.util.UUID;

public record OperationalIssueTarget(
        OperationalIssueTargetType targetType,
        UUID targetId,
        String targetCode,
        OperationalIssueTargetType parentTargetType,
        UUID parentTargetId
) {
}
