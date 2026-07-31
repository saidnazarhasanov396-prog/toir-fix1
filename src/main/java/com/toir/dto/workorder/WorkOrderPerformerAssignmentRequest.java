package com.toir.dto.workorder;

import java.util.UUID;

public record WorkOrderPerformerAssignmentRequest(
        @Deprecated UUID performerId,
        UUID performerEmployeeId,
        UUID performerBrigadeMemberId
) {
}
