package com.toir.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.toir.dto.plannedshutdown.PlannedShutdownBlocker;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record PlannedShutdownBlockerResponse(
        String message,
        String path,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime timestamp,
        int code,
        String errorCode,
        Map<String, Object> params,
        Long version,
        List<PlannedShutdownBlocker> blockers
) {}
