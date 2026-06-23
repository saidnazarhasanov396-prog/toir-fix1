package com.toir.dto.repairrequest;

import com.toir.enums.WarrantyHandling;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record WarrantyDecisionRequest(
        @NotNull WarrantyHandling warrantyHandling,
        String warrantyDecisionComment,
        Instant supplierContactedAt,
        String supplierResponse,
        String emergencyReason
) {}
