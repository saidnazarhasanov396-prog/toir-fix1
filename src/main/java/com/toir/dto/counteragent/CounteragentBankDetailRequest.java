package com.toir.dto.counteragent;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CounteragentBankDetailRequest(
        UUID id,
        @Size(max = 255) String bankName,
        @Size(max = 255) String bankAccount,
        @Size(max = 255) String mfo,
        boolean isPrimary
) {
}
