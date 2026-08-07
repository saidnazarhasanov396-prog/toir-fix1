package com.toir.service.maintenanceembedding;

public class EmbeddingServiceException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;

    public EmbeddingServiceException(String errorCode, String safeMessage, boolean retryable) {
        super(safeMessage);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
