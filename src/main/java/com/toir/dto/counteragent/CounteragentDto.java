package com.toir.dto.counteragent;

import com.toir.entity.Counteragent;
import com.toir.enums.CounteragentStatus;

import java.util.Comparator;
import java.util.List;
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
        List<CounteragentBankDetailDto> bankDetails,
        @Deprecated String bankName,
        @Deprecated String bankAccount,
        @Deprecated String mfo,
        CounteragentStatus status
) {
    public CounteragentDto(
            UUID id, String code, String name, String inn, String contactName, String contactPosition,
            String contactPhone, String contactEmail, String address, String directorName,
            String bankName, String bankAccount, String mfo, CounteragentStatus status
    ) {
        this(id, code, name, inn, contactName, contactPosition, contactPhone, contactEmail, address,
                directorName, List.of(), bankName, bankAccount, mfo, status);
    }

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
                counteragent.getBankDetails() == null ? List.of() : counteragent.getBankDetails().stream()
                        .sorted(Comparator.comparingInt(com.toir.entity.CounteragentBankDetail::getDisplayOrder)
                                .thenComparing(detail -> detail.getId() == null ? new UUID(0, 0) : detail.getId()))
                        .map(CounteragentBankDetailDto::from)
                        .toList(),
                counteragent.getBankName(),
                counteragent.getBankAccount(),
                counteragent.getMfo(),
                counteragent.getStatus()
        );
    }
}
