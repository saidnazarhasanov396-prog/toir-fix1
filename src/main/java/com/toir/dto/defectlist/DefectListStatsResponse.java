package com.toir.dto.defectlist;

public record DefectListStatsResponse(
        long totalDefectLists,
        long draft,
        long approved,
        long closed
) {}
