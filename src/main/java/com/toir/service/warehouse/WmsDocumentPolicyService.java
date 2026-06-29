package com.toir.service.warehouse;

import com.toir.dto.wms.WmsDocumentGroupRequest;
import com.toir.enums.DocumentType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WmsDocumentOperationType;
import com.toir.exception.RestException;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WmsDocumentPolicyService {

    public void validateReceiptDocuments(WmsDocumentOperationType operation,
                                         List<WmsDocumentGroupRequest> groups,
                                         boolean hasDiscrepancy,
                                         boolean hasWarrantyLine,
                                         boolean strict) {
        Set<DocumentType> types = documentTypes(groups);
        if (!strict) {
            return;
        }
        require(types.contains(DocumentType.INVOICE) || types.contains(DocumentType.DELIVERY_NOTE),
                "Receipt requires INVOICE or DELIVERY_NOTE document");
        require(!hasDiscrepancy || types.contains(DocumentType.DISCREPANCY_ACT),
                "Discrepancy receipt requires DISCREPANCY_ACT document");
        require(!hasWarrantyLine || types.contains(DocumentType.WARRANTY),
                "Warranty receipt requires WARRANTY document");
    }

    public void validateIssueDocuments(UUID workOrderId,
                                       String documentNumber,
                                       UUID issuedById,
                                       UUID takenById,
                                       List<WmsDocumentGroupRequest> groups,
                                       boolean strict) {
        documentTypes(groups);
        if (!strict) {
            return;
        }
        require(!isBlank(documentNumber), "Work order issue requires document number");
        require(issuedById != null, "Work order issue requires issued by");
        require(takenById != null, "Work order issue requires taken by");
    }

    public void validateReturnDocuments(String reason,
                                        WarehouseStockStatus targetStatus,
                                        List<WmsDocumentGroupRequest> groups,
                                        boolean strict) {
        Set<DocumentType> types = documentTypes(groups);
        if (!strict) {
            return;
        }
        require(!isBlank(reason), "Material return requires reason");
        require(targetStatus != null, "Material return requires target stock status");
        if (EnumSet.of(
                WarehouseStockStatus.DAMAGED,
                WarehouseStockStatus.QUARANTINE,
                WarehouseStockStatus.WRITEOFF_PENDING
        ).contains(targetStatus)) {
            require(types.contains(DocumentType.PHOTO), "Damaged or quarantine return requires PHOTO document");
        }
    }

    public void validateInventoryCountDocuments(boolean hasVariance,
                                                List<WmsDocumentGroupRequest> groups,
                                                boolean strict) {
        Set<DocumentType> types = documentTypes(groups);
        if (!strict) {
            return;
        }
        require(!hasVariance || types.contains(DocumentType.VARIANCE_ACT),
                "Inventory count posting with variance requires VARIANCE_ACT document");
    }

    public void validateWriteoffDocuments(UUID approvalRequestId,
                                          List<WmsDocumentGroupRequest> groups,
                                          boolean strict) {
        Set<DocumentType> types = documentTypes(groups);
        if (!strict) {
            return;
        }
        require(approvalRequestId != null, "Warehouse writeoff requires approval id");
        require(types.contains(DocumentType.WRITEOFF_ACT), "Warehouse writeoff requires WRITEOFF_ACT document");
    }

    private Set<DocumentType> documentTypes(List<WmsDocumentGroupRequest> groups) {
        if (groups == null || groups.isEmpty()) {
            return Set.of();
        }
        return groups.stream()
                .map(this::documentType)
                .collect(Collectors.toUnmodifiableSet());
    }

    private DocumentType documentType(WmsDocumentGroupRequest group) {
        if (group == null || isBlank(group.documentType())) {
            throw RestException.badRequest("documentType is required");
        }
        String normalized = group.normalizedType();
        try {
            return DocumentType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw RestException.badRequest("Unsupported document type: " + normalized);
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw RestException.badRequest(message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
