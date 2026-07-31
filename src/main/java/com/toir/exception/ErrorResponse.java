package com.toir.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse(
        String message,
        String path,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime timestamp,
        int code,
        String errorCode,
        Map<String, Object> params
) {
    public static ErrorResponse of(String message, String path, int code) {
        return of(message, path, code, null, Map.of());
    }

    public static ErrorResponse of(String message, String path, int code, String errorCode) {
        return of(message, path, code, errorCode, Map.of());
    }

    public static ErrorResponse of(
            String message,
            String path,
            int code,
            String errorCode,
            Map<String, Object> params
    ) {
        HttpStatus status = HttpStatus.resolve(code);
        String stableCode = errorCode == null || errorCode.isBlank()
                ? BackendErrorLocalizer.defaultCode(status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status)
                : errorCode;
        return new ErrorResponse(message, path, LocalDateTime.now(), code, stableCode,
                params == null ? Map.of() : params);
    }
}
