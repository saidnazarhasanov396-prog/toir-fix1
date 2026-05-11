package com.toir.dto.actualcostrouteoverride;

public record ContractorWorkShortDto(
        String description,
        ContractorShortDto contractor,
        WorkOrderShortDto workOrder
) {
}
