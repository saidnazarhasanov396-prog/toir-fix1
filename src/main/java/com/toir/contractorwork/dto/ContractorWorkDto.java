package com.toir.contractorwork.dto;

import com.toir.contractorwork.ContractorWork;
import com.toir.contractorwork.ContractorWorkStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record ContractorWorkDto(
        UUID id,
        @NotNull UUID contractorId,
        UUID workOrderId,
        @NotBlank String description,
        ContractorWorkStatus status,
        Instant startedAt,
        Instant completedAt,
        Double cost,
        String result,
        String acceptanceComment,
        UUID createdById,
        UUID acceptedById,
        Instant acceptedAt
) {
    public static ContractorWorkDto from(ContractorWork w) {
        return new ContractorWorkDto(w.getId(), w.getContractorId(), w.getWorkOrderId(), w.getDescription(),
                w.getStatus(), w.getStartedAt(), w.getCompletedAt(), w.getCost(), w.getResult(),
                w.getAcceptanceComment(), w.getCreatedById(), w.getAcceptedById(), w.getAcceptedAt());
    }
}
