package com.toir.dto.repairrequest;

import com.toir.enums.WarrantyHandling;

import java.time.Instant;

public record WarrantyStatusResponse(
        Boolean warrantyActiveAtCreation,
        boolean currentlyActive,
        WarrantyHandling warrantyHandling,
        String warrantyDecisionComment,
        Instant supplierContactedAt,
        String supplierResponse,
        String emergencyReason
) {}
