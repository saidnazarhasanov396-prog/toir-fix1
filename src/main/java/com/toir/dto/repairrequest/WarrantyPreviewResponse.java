package com.toir.dto.repairrequest;

import java.time.LocalDate;
import java.util.UUID;

public record WarrantyPreviewResponse(
        UUID equipmentId,
        boolean currentlyActive,
        LocalDate warrantyStartDate,
        LocalDate warrantyEndDate,
        UUID warrantyCounteragentId,
        String warrantyCounteragentName,
        String warrantyCounteragentContactPerson,
        String warrantyCounteragentPhone,
        String warrantyCounteragentEmail
) {}
