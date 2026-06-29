package com.toir.dto.workorder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record WorkOrderWmsReservationRequest(
        @NotEmpty List<@Valid WorkOrderWmsReservationLineRequest> lines,
        UUID reservedById,
        String documentNumber
) {
}
