package com.toir.dto.repairrequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RepairRequestMeterReadingBatchRequest(
        @NotEmpty @Valid List<RepairRequestMeterReadingRequest> readings
) {
    public RepairRequestMeterReadingBatchRequest {
        readings = readings == null ? null : List.copyOf(readings);
    }
}
