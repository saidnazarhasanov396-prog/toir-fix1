package com.toir.ai.repair;

public record AiRepairDefect(
        String type,
        String title,
        String description,
        String category,
        String severity,
        String failureReason,
        String rootCause
) {
}
