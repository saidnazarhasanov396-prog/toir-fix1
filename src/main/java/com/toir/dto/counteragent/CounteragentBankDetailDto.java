package com.toir.dto.counteragent;

import com.toir.entity.CounteragentBankDetail;

import java.util.UUID;

public record CounteragentBankDetailDto(
        UUID id,
        String bankName,
        String bankAccount,
        String mfo,
        boolean isPrimary,
        int displayOrder
) {
    public static CounteragentBankDetailDto from(CounteragentBankDetail detail) {
        return new CounteragentBankDetailDto(
                detail.getId(),
                detail.getBankName(),
                detail.getBankAccount(),
                detail.getMfo(),
                detail.isPrimary(),
                detail.getDisplayOrder()
        );
    }
}
