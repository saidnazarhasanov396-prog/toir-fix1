package com.toir.dto.counteragent;

import com.toir.entity.contractors.ContractorContract;
import com.toir.enums.ContractStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CounteragentContractDto(
        UUID id,
        @NotNull UUID counteragentId,
        @NotBlank String number,
        @NotBlank String subject,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        Double amount,
        ContractStatus status
) {
    public static CounteragentContractDto from(ContractorContract contract) {
        return new CounteragentContractDto(
                contract.getId(),
                contract.getCounteragentId(),
                contract.getNumber(),
                contract.getSubject(),
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getAmount(),
                contract.getStatus()
        );
    }
}
