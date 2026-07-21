package com.toir.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

public record ErrorResponse(
        String message,
        String path,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime timestamp,
        int code,
        @JsonInclude(JsonInclude.Include.NON_NULL) String errorCode
) {
    public static ErrorResponse of(String message, String path, int code) {
        return of(message, path, code, null);
    }

    public static ErrorResponse of(String message, String path, int code, String errorCode) {
        return new ErrorResponse(message, path, LocalDateTime.now(), code, errorCode);
    }
}
