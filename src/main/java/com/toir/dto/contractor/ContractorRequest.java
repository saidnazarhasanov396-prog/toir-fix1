package com.toir.dto.contractor;

import com.toir.enums.ContractorStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ContractorRequest(
        String code,
        @NotBlank String name,
        String taxNumber,
        String contactPerson,
        String phone,
        @Email String email,
        String specialization,
        String directorName,
        String bankName,
        String bankAccount,
        String mfo,
        ContractorStatus status
) {}
