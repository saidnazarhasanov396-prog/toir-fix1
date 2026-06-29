package com.toir.dto.procurement;

import com.toir.dto.wms.WmsDocumentGroupRequest;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProcurementReceiptRequest(
        List<@Valid ProcurementReceiptLineRequest> lines,
        LocalDate receiptDate,
        String documentNumber,
        UUID responsiblePersonId,
        String comment,
        List<@Valid WmsDocumentGroupRequest> documentGroups,
        boolean strictDocumentPolicy
) {
    public ProcurementReceiptRequest(List<@Valid ProcurementReceiptLineRequest> lines,
                                     LocalDate receiptDate,
                                     String documentNumber,
                                     UUID responsiblePersonId,
                                     String comment) {
        this(lines, receiptDate, documentNumber, responsiblePersonId, comment, List.of(), false);
    }
}
