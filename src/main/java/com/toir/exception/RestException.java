package com.toir.exception;

import com.toir.enums.ErrorType;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class RestException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final Map<String, Object> params;

    public RestException(String message, HttpStatus status) {
        this(message, status, null);
    }

    public RestException(String message, HttpStatus status, String errorCode) {
        this(message, status, errorCode, Map.of());
    }

    public RestException(String message, HttpStatus status, String errorCode, Map<String, ?> params) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.params = immutableParams(params);
    }

    public static RestException badRequest(String message) {
        return new RestException(message, HttpStatus.BAD_REQUEST);
    }

    public static RestException badRequest(String message, String errorCode) {
        return new RestException(message, HttpStatus.BAD_REQUEST, errorCode);
    }

    public static RestException unauthorized(String message) {
        return new RestException(message, HttpStatus.UNAUTHORIZED);
    }

    public static RestException forbidden(String message) {
        return new RestException(message, HttpStatus.FORBIDDEN);
    }

    public static RestException forbidden(String message, String errorCode) {
        return new RestException(message, HttpStatus.FORBIDDEN, errorCode);
    }

    public static RestException notFound(String message) {
        return new RestException(message, HttpStatus.NOT_FOUND);
    }

    public static RestException conflict(String message) {
        return new RestException(message, HttpStatus.CONFLICT);
    }

    public static RestException conflict(String message, String errorCode) {
        return new RestException(message, HttpStatus.CONFLICT, errorCode);
    }

    public static RestException restThrow(ErrorType errorType) {
        return new RestException(errorType.getMessage(), errorType.getStatus(), errorType.name());
    }

    public static RestException localized(HttpStatus status, String errorCode, Map<String, ?> params) {
        return new RestException(null, status, errorCode, params);
    }

    private static Map<String, Object> immutableParams(Map<String, ?> params) {
        if (params == null || params.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        params.forEach(copy::put);
        return Collections.unmodifiableMap(copy);
    }
}
