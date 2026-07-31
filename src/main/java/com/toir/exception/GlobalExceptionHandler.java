package com.toir.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RestException.class)
    public ResponseEntity<?> handleRestException(RestException ex, HttpServletRequest request) {
        if (ex instanceof PlannedShutdownBlockerException blocker) {
            BackendErrorLocalizer.LocalizedError localized = BackendErrorLocalizer.localize(
                    request, ex.getStatus(), ex.getErrorCode(), ex.getParams(), ex.getMessage(), true);
            return ResponseEntity.status(ex.getStatus())
                    .header(HttpHeaders.CONTENT_LANGUAGE, BackendErrorLocalizer.resolveLocale(
                            request.getHeader(HttpHeaders.ACCEPT_LANGUAGE)).getLanguage())
                    .body(new PlannedShutdownBlockerResponse(
                    localized.message(), request.getRequestURI(), LocalDateTime.now(), ex.getStatus().value(),
                    localized.errorCode(), localized.params(),
                    blocker.getVersion(), blocker.getBlockers()));
        }
        return build(ex.getStatus(), ex.getMessage(), ex.getErrorCode(), ex.getParams(), true, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> validationErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .toList();
        List<String> invalidFields = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField())
                .distinct()
                .toList();
        String message = String.join("; ", validationErrors);
        return build(HttpStatus.BAD_REQUEST, message.isBlank() ? "Validation failed" : message,
                "VALIDATION_FAILED", Map.of("fields", invalidFields), true, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        List<String> paths = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath().toString())
                .distinct()
                .toList();
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "VALIDATION_FAILED",
                Map.of("fields", paths), true, request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ErrorResponse> handleOptimisticLock(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT,
                "Resource was modified by another request; reload and retry",
                "OPTIMISTIC_LOCK", Map.of(), false, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(
            HandlerMethodValidationException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = (ex.getMessage() == null || ex.getMessage().isBlank())
                ? "Validation failed"
                : ex.getMessage();
        return build(status, message, "VALIDATION_FAILED", Map.of(), true, request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid credentials", "INVALID_CREDENTIALS",
                Map.of(), false, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, null, "AUTHENTICATION_REQUIRED", Map.of(), false, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Access denied", "ACCESS_DENIED", Map.of(), false, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String param = ex.getName();
        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "required type";
        String value = ex.getValue() != null ? String.valueOf(ex.getValue()) : "null";
        String message = "Invalid value for parameter '" + param + "': " + value + ". Expected " + expected + ".";
        return build(HttpStatus.BAD_REQUEST, message, "INVALID_PARAMETER_VALUE",
                Map.of("parameter", param, "value", value, "expectedType", expected), true, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, buildUnreadableMessage(ex), "INVALID_REQUEST_BODY",
                Map.of(), true, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), "RESOURCE_NOT_FOUND", Map.of(), true, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase()
                : ex.getMessage();
        return build(HttpStatus.METHOD_NOT_ALLOWED, message, "METHOD_NOT_ALLOWED", Map.of(), true, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        if ("comment".equals(ex.getParameterName())
                && request.getRequestURI() != null
                && request.getRequestURI().contains("/actual-costs/")
                && request.getRequestURI().contains("/reject")) {
            return build(HttpStatus.BAD_REQUEST, "Rejection comment is required",
                    "REJECTION_COMMENT_REQUIRED", Map.of("parameter", "comment"), false, request);
        }
        String message = "Required request parameter '" + ex.getParameterName() + "' is missing";
        return build(HttpStatus.BAD_REQUEST, message, "MISSING_REQUEST_PARAMETER",
                Map.of("parameter", ex.getParameterName()), true, request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String reason = ex.getReason();
        String errorCode = reason != null && reason.matches("[A-Z][A-Z0-9_]+")
                ? reason
                : BackendErrorLocalizer.defaultCode(status);
        return build(status, reason, errorCode, Map.of(), true, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected exception while handling {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, null, "INTERNAL_SERVER_ERROR",
                Map.of(), false, request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        return build(status, message, null, Map.of(), true, request);
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            String message,
            String errorCode,
            HttpServletRequest request
    ) {
        return build(status, message, errorCode, Map.of(), true, request);
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            String message,
            String errorCode,
            Map<String, ?> params,
            boolean allowLegacyEnglish,
            HttpServletRequest request
    ) {
        BackendErrorLocalizer.LocalizedError localized = BackendErrorLocalizer.localize(
                request, status, errorCode, params, message, allowLegacyEnglish);
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_LANGUAGE, BackendErrorLocalizer.resolveLocale(
                        request.getHeader(HttpHeaders.ACCEPT_LANGUAGE)).getLanguage())
                .body(ErrorResponse.of(localized.message(), request.getRequestURI(), status.value(),
                        localized.errorCode(), localized.params()));
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
