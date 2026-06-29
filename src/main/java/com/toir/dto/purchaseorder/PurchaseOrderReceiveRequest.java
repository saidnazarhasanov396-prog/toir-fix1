package com.toir.dto.purchaseorder;

import com.toir.dto.wms.WmsDocumentGroupRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderReceiveRequest(
        @NotEmpty List<@Valid PurchaseOrderReceiveLineRequest> lines,
        LocalDate receiptDate,
        String documentNumber,
        @NotNull UUID responsiblePersonId,
        List<@Valid WmsDocumentGroupRequest> documentGroups,
        boolean strictDocumentPolicy
) {
    public PurchaseOrderReceiveRequest(@NotEmpty List<@Valid PurchaseOrderReceiveLineRequest> lines,
                                       LocalDate receiptDate,
                                       String documentNumber,
                                       UUID responsiblePersonId) {
        this(lines, receiptDate, documentNumber, responsiblePersonId, List.of(), false);
    }
}
