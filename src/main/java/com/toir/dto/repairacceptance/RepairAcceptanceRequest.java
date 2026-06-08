package com.toir.dto.repairacceptance;

import com.toir.enums.RepairAcceptanceQualityGrade;
import com.toir.enums.RepairAcceptanceStage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record RepairAcceptanceRequest(
        @NotNull RepairAcceptanceStage stage,
        UUID acceptedById,
        UUID handedOverById,
        Boolean runInRequired,
        Integer runInShiftsRequired,
        RepairAcceptanceQualityGrade qualityGrade,
        String performanceBefore,
        String performanceAfter,
        String qualityBefore,
        String qualityAfter,
        String remarks,
        @Valid List<RepairAcceptanceDefectRequest> defects
) {
}
