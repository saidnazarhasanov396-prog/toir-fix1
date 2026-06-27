package com.toir.dto.contractor;

import com.toir.dto.common.BankAccountDto;
import com.toir.enums.ContractorStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ContractorRequest(
        String code,
        @NotBlank String name,
        String taxNumber,
        String contactPerson,
        String phone,
        @Email String email,
        String specialization,
        String directorName,
        List<BankAccountDto> bankAccounts,
        ContractorStatus status
) {}
