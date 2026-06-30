package com.toir.dto.counteragent;

import com.toir.enums.CounteragentStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CounteragentRequest(
        String code,
        @NotBlank String name,
        String taxNumber,
        String baseInn,
        String contactPerson,
        String phone,
        @Email String email,
        String address,
        String specialization,
        String directorName,
        String bankName,
        String bankAccount,
        String mfo,
        CounteragentStatus status
) {
}
