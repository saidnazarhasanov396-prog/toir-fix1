package com.toir.exception;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record ErrorResponse(
        String message,
        String path,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime timestamp,
        int code
) {
    public static ErrorResponse of(String message, String path, int code) {
        return new ErrorResponse(message, path, LocalDateTime.now(), code);
    }
}
