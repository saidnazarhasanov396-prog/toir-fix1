package com.toir.dto.repairrequest;

import com.toir.enums.WarrantyHandling;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WarrantyStatusResponse(
        Boolean warrantyActiveAtCreation,
        boolean currentlyActive,
        WarrantyHandling warrantyHandling,
        String warrantyDecisionComment,
        Instant supplierContactedAt,
        String supplierResponse,
        String emergencyReason,
        UUID warrantySupplierId,
        String warrantySupplierName,
        String warrantySupplierContactPerson,
        String warrantySupplierPhone,
        String warrantySupplierEmail,
        LocalDate warrantyStartDateAtCreation,
        LocalDate warrantyEndDateAtCreation
) {}
