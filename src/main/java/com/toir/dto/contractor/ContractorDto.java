package com.toir.dto.contractor;

import com.toir.entity.contractors.Contractor;
import com.toir.enums.ContractorStatus;

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
        ContractorStatus status
) {
    public static ContractorDto from(Contractor c) {
        return new ContractorDto(c.getId(), c.getCode(), c.getName(), c.getTaxNumber(),
                c.getContactPerson(), c.getPhone(), c.getEmail(), c.getSpecialization(), c.getStatus());
    }
}
