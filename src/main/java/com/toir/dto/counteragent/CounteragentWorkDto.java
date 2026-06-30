package com.toir.dto.counteragent;

import com.toir.entity.contractors.ContractorWork;
import com.toir.enums.ContractorWorkStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CounteragentWorkDto(
        UUID id,
        @NotNull UUID counteragentId,
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
    public static CounteragentWorkDto from(ContractorWork work) {
        return new CounteragentWorkDto(
                work.getId(),
                work.getCounteragentId(),
                work.getWorkOrderId(),
                work.getDescription(),
                work.getStatus(),
                work.getStartedAt(),
                work.getCompletedAt(),
                work.getCost(),
                work.getResult(),
                work.getAcceptanceComment(),
                work.getCreatedById(),
                work.getAcceptedById(),
                work.getAcceptedAt()
        );
    }
}
