package com.toir.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorType {
    FILE_TOO_LARGE("File size exceeds the allowed limit", HttpStatus.BAD_REQUEST),
    INVALID_FILE("Invalid file", HttpStatus.BAD_REQUEST),
    FILE_EMPTY("File is empty", HttpStatus.BAD_REQUEST),
    FILE_TYPE_NOT_ALLOWED("File type is not allowed", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    FILE_NOT_FOUND("File not found", HttpStatus.NOT_FOUND),
    FILE_ACCESS_DENIED("File access denied", HttpStatus.FORBIDDEN),
    FILE_UPLOAD_FAILED("File upload failed", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_STORAGE_ACCESS_DENIED("File storage access denied or misconfigured", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_STORAGE_CONFIGURATION_FAILED("File storage configuration error", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_DELETE_FAILED("File delete failed", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_DOWNLOAD_FAILED("File download failed", HttpStatus.INTERNAL_SERVER_ERROR),
    PRESIGNED_URL_FAILED("Could not generate presigned URL", HttpStatus.INTERNAL_SERVER_ERROR),
    UPLOAD_FILES_FAILED("File upload failed", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String message;
    private final HttpStatus status;

    ErrorType(String message, HttpStatus status) {
        this.message = message;
        this.status = status;
    }
}
