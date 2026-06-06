package com.toir.dto.workorder;

import java.util.UUID;

public record WorkOrderPerformerOptionDto(
        UUID id,
        String name,
        UUID departmentId,
        String departmentName,
        String role
) {
}
