package com.toir.dto.integration;

import java.util.List;
import java.util.UUID;

public record RunDueSyncsResponse(
        int processed,
        List<Result> results
) {
    public record Result(UUID endpointId, String code, String status) {
    }
}
