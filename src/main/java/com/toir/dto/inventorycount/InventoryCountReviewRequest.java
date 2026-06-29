package com.toir.dto.inventorycount;

import com.toir.dto.wms.WmsDocumentGroupRequest;

import java.util.List;

public record InventoryCountReviewRequest(
        List<WmsDocumentGroupRequest> documentGroups,
        boolean strictDocumentPolicy,
        String comment
) {
}
