package com.toir.dto.counteragent;

import com.toir.enums.CounteragentStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CounteragentRequest(
        @NotBlank @Size(max = 255) String name,
        @Pattern(regexp = "\\s*|\\s*\\d{9}\\s*", message = "INN must contain exactly 9 digits") String inn,
        @Size(max = 255) String contactName,
        @Size(max = 255) String contactPosition,
        @Size(max = 255) String contactPhone,
        @Email String contactEmail,
        String address,
        String directorName,
        String bankName,
        String bankAccount,
        String mfo,
        CounteragentStatus status
) {
}
