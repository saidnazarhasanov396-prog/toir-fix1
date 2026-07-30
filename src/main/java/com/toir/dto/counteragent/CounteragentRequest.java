package com.toir.dto.counteragent;

import com.toir.enums.CounteragentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CounteragentRequest(
        @NotBlank @Size(max = 255) String name,
        @Pattern(regexp = "\\s*|\\s*\\d{9}\\s*", message = "INN must contain exactly 9 digits") String inn,
        @Size(max = 255) String contactName,
        @Size(max = 255) String contactPosition,
        @Size(max = 255) String contactPhone,
        @Email String contactEmail,
        String address,
        String directorName,
        @Valid @Size(max = 10) List<CounteragentBankDetailRequest> bankDetails,
        @Deprecated String bankName,
        @Deprecated String bankAccount,
        @Deprecated String mfo,
        CounteragentStatus status
) {
    public CounteragentRequest(
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
        this(name, inn, contactName, contactPosition, contactPhone, contactEmail, address, directorName,
                null, bankName, bankAccount, mfo, status);
    }
}
