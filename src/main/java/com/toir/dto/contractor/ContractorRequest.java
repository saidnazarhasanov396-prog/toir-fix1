package com.toir.dto.contractor;

import com.toir.entity.ContractorStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ContractorRequest(
        @NotBlank String code,
        @NotBlank String name,
        String taxNumber,
        String contactPerson,
        String phone,
        @Email String email,
        String specialization,
        ContractorStatus status
) {}
