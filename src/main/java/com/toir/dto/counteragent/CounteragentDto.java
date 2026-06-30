package com.toir.dto.counteragent;

import com.toir.entity.Counteragent;
import com.toir.enums.CounteragentStatus;

import java.util.UUID;

public record CounteragentDto(
        UUID id,
        String code,
        String name,
        String taxNumber,
        String baseInn,
        String contactPerson,
        String phone,
        String email,
        String address,
        String specialization,
        String directorName,
        String bankName,
        String bankAccount,
        String mfo,
        CounteragentStatus status
) {
    public static CounteragentDto from(Counteragent counteragent) {
        return new CounteragentDto(
                counteragent.getId(),
                counteragent.getCode(),
                counteragent.getName(),
                counteragent.getTaxNumber(),
                counteragent.getBaseInn(),
                counteragent.getContactPerson(),
                counteragent.getPhone(),
                counteragent.getEmail(),
                counteragent.getAddress(),
                counteragent.getSpecialization(),
                counteragent.getDirectorName(),
                counteragent.getBankName(),
                counteragent.getBankAccount(),
                counteragent.getMfo(),
                counteragent.getStatus()
        );
    }
}
