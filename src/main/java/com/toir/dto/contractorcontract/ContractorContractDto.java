package com.toir.dto.contractorcontract;

import com.toir.enums.ContractStatus;
import com.toir.entity.ContractorContract;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record ContractorContractDto(
        UUID id,
        @NotNull UUID contractorId,
        @NotBlank String number,
        @NotBlank String subject,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        Double amount,
        ContractStatus status
) {
    public static ContractorContractDto from(ContractorContract c) {
        return new ContractorContractDto(c.getId(), c.getContractorId(), c.getNumber(), c.getSubject(),
                c.getStartDate(), c.getEndDate(), c.getAmount(), c.getStatus());
    }
}
