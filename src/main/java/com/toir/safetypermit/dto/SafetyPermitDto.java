package com.toir.safetypermit.dto;

import com.toir.safetypermit.SafetyPermit;
import com.toir.safetypermit.SafetyPermitStatus;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public record SafetyPermitDto(
        UUID id,
        UUID workOrderId,
        @NotBlank String permitNumber,
        UUID issuedById,
        Instant issuedAt,
        Instant validUntil,
        SafetyPermitStatus status,
        String notes
) {
    public static SafetyPermitDto from(SafetyPermit p) {
        return new SafetyPermitDto(p.getId(), p.getWorkOrderId(), p.getPermitNumber(),
                p.getIssuedById(), p.getIssuedAt(), p.getValidUntil(), p.getStatus(), p.getNotes());
    }
}
