package com.toir.exception;

import com.toir.enums.ErrorType;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class RestException extends RuntimeException {

    private final HttpStatus status;

    public RestException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public static RestException badRequest(String message) {
        return new RestException(message, HttpStatus.BAD_REQUEST);
    }

    public static RestException unauthorized(String message) {
        return new RestException(message, HttpStatus.UNAUTHORIZED);
    }

    public static RestException forbidden(String message) {
        return new RestException(message, HttpStatus.FORBIDDEN);
    }

    public static RestException notFound(String message) {
        return new RestException(message, HttpStatus.NOT_FOUND);
    }

    public static RestException conflict(String message) {
        return new RestException(message, HttpStatus.CONFLICT);
    }

    public static RestException restThrow(ErrorType errorType) {
        return new RestException(errorType.getMessage(), errorType.getStatus());
    }
}
