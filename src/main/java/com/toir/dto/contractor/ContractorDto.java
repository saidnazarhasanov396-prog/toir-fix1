package com.toir.dto.contractor;

import com.toir.dto.common.BankAccountDto;
import com.toir.entity.contractors.Contractor;
import com.toir.enums.ContractorStatus;
import com.toir.util.PartyLegalDetailsUtils;
import java.util.List;
import java.util.UUID;

public record ContractorDto(
        UUID id,
        String code,
        String name,
        String taxNumber,
        String contactPerson,
        String phone,
        String email,
        String specialization,
        String directorName,
        List<BankAccountDto> bankAccounts,
        ContractorStatus status,
        Summary summary
) {
    public ContractorDto {
        summary = summary == null ? Summary.empty() : summary;
        bankAccounts = bankAccounts == null ? List.of() : List.copyOf(bankAccounts);
    }

    public static ContractorDto from(Contractor c) {
        return from(c, Summary.empty());
    }

    public static ContractorDto from(Contractor c, Summary summary) {
        return new ContractorDto(
                c.getId(),
                c.getCode(),
                c.getName(),
                c.getTaxNumber(),
                c.getContactPerson(),
                c.getPhone(),
                c.getEmail(),
                c.getSpecialization(),
                c.getDirectorName(),
                PartyLegalDetailsUtils.toBankAccountDtos(c.getBankAccounts()),
                c.getStatus(),
                summary
        );
    }

    public record Summary(
            long activeContracts,
            long activeWorkOrders,
            long pendingActualCostReview,
            long acceptedAwaitingReflection
    ) {
        public static Summary empty() {
            return new Summary(0, 0, 0, 0);
        }
    }
}
