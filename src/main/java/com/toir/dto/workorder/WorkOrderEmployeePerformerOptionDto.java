package com.toir.dto.workorder;

import java.util.List;
import java.util.UUID;

public record WorkOrderEmployeePerformerOptionDto(
        UUID performerEmployeeId,
        String performerFullName,
        UUID performerUserId,
        UUID departmentId,
        List<BrigadeMembership> brigadeMemberships
) {
    public record BrigadeMembership(
            UUID performerBrigadeMemberId,
            UUID performerBrigadeId,
            String performerBrigadeName,
            String roleCode
    ) {
    }
}
