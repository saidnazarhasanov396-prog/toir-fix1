package com.toir.dto.defect;

public record DefectStatsResponse(
        long totalDefects,
        long open,
        long resolved,
        long withRecurrence
) {
}
