package com.toir.dto.actualcostrouteoverride;

public record ContractorWorkShortDto(
        java.util.UUID id,
        String description,
        ContractorShortDto contractor,
        WorkOrderShortDto workOrder
) {
}
