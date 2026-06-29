package com.toir.dto.workorder;

import com.toir.dto.wms.WmsDocumentGroupRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record WorkOrderPickConfirmRequest(
        @NotEmpty List<@Valid WorkOrderPickConfirmLineRequest> lines,
        String documentNumber,
        UUID issuedById,
        UUID takenById,
        List<@Valid WmsDocumentGroupRequest> documentGroups,
        boolean strictDocumentPolicy
) {
}
