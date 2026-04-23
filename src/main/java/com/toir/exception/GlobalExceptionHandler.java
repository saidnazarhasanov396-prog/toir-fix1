package com.toir.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RestException.class)
    public ResponseEntity<ErrorResponse> handleRestException(RestException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message.isBlank() ? "Validation failed" : message, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid credentials", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String param = ex.getName();
        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "required type";
        String value = ex.getValue() != null ? String.valueOf(ex.getValue()) : "null";
        String message = "Invalid value for parameter '" + param + "': " + value + ". Expected " + expected + ".";
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, buildUnreadableMessage(ex), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(message, request.getRequestURI(), status.value()));
    }

    private String buildUnreadableMessage(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof InvalidFormatException ife) {
                String field = ife.getPath() != null
                        ? ife.getPath().stream().map(ref -> ref.getFieldName()).filter(n -> n != null && !n.isBlank()).findFirst().orElse(null)
                        : null;
                String value = ife.getValue() != null ? String.valueOf(ife.getValue()) : "null";
                Class<?> targetType = ife.getTargetType();
                if (targetType != null && Instant.class.isAssignableFrom(targetType)) {
                    String prefix = field != null ? ("Invalid value for field '" + field + "': ") : "Invalid timestamp value: ";
                    return prefix + value + ". Use ISO-8601, e.g. 2026-04-24T17:46:00Z or 2026-04-24T17:46:00+05:00.";
                }
                if (field != null) {
                    return "Invalid value for field '" + field + "': " + value + ".";
                }
                return "Invalid value: " + value + ".";
            }
            if (cause instanceof DateTimeParseException dtpe) {
                return "Invalid timestamp format. Use ISO-8601, e.g. 2026-04-24T17:46:00Z or 2026-04-24T17:46:00+05:00.";
            }
            cause = cause.getCause();
        }
        String msg = ex.getMessage();
        return (msg == null || msg.isBlank()) ? "Invalid JSON request body" : msg;
    }
}
