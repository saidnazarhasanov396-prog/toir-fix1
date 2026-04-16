package com.toir.completionact.dto;

import com.toir.completionact.CompletionAct;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public record CompletionActDto(
        UUID id,
        UUID workOrderId,
        @NotBlank String actNumber,
        UUID signedById,
        Instant signedAt,
        String summary
) {
    public static CompletionActDto from(CompletionAct a) {
        return new CompletionActDto(a.getId(), a.getWorkOrderId(), a.getActNumber(),
                a.getSignedById(), a.getSignedAt(), a.getSummary());
    }
}
