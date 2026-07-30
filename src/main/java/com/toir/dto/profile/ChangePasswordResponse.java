package com.toir.dto.profile;

public record ChangePasswordResponse(
        String message,
        boolean reauthenticationRequired
) {
}
