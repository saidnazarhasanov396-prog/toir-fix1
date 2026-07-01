package com.toir.dto.counteragent;

import com.toir.entity.Counteragent;
import com.toir.enums.CounteragentStatus;

import java.util.UUID;

public record CounteragentDto(
        UUID id,
        String code,
        String name,
        String inn,
        String contactName,
        String contactPosition,
        String contactPhone,
        String contactEmail,
        String address,
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
                counteragent.getInn(),
                counteragent.getContactName(),
                counteragent.getContactPosition(),
                counteragent.getContactPhone(),
                counteragent.getContactEmail(),
                counteragent.getAddress(),
                counteragent.getDirectorName(),
                counteragent.getBankName(),
                counteragent.getBankAccount(),
                counteragent.getMfo(),
                counteragent.getStatus()
        );
    }
}
