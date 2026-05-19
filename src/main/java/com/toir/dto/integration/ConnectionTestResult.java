package com.toir.dto.integration;

public record ConnectionTestResult(
        boolean reachable,
        int httpStatus,
        long responseTimeMs,
        String serverInfo,
        String error
) {}
