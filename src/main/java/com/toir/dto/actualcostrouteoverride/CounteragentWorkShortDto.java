package com.toir.dto.actualcostrouteoverride;

import java.util.UUID;

public record CounteragentWorkShortDto(
        UUID id,
        String description,
        CounteragentShortDto counteragent,
        WorkOrderShortDto workOrder
) {
}
