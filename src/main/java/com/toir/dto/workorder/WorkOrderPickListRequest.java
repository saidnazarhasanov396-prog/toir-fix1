package com.toir.dto.workorder;

import java.util.UUID;

public record WorkOrderPickListRequest(
        UUID assignedToId,
        String comment
) {
}
