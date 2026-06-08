package com.toir.dto.repairacceptance;

import com.toir.entity.maintenance.RepairAcceptance;
import com.toir.enums.RepairAcceptanceQualityGrade;
import com.toir.enums.RepairAcceptanceStage;
import com.toir.enums.RepairAcceptanceStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RepairAcceptanceDto(
        UUID id,
        UUID workOrderId,
        RepairAcceptanceStage stage,
        RepairAcceptanceStatus status,
        UUID acceptedById,
        UUID handedOverById,
        Instant acceptanceStartedAt,
        Instant acceptedAt,
        boolean runInRequired,
        Integer runInShiftsRequired,
        Instant runInStartedAt,
        Instant runInCompletedAt,
        RepairAcceptanceQualityGrade qualityGrade,
        String performanceBefore,
        String performanceAfter,
        String qualityBefore,
        String qualityAfter,
        String remarks,
        List<RepairAcceptanceDefectDto> defects,
        Instant updatedAt
) {
    public static RepairAcceptanceDto from(RepairAcceptance acceptance) {
        List<RepairAcceptanceDefectDto> defectDtos = acceptance.getDefects() == null
                ? List.of()
                : acceptance.getDefects().stream()
                .filter(defect -> !defect.isDeleted())
                .map(RepairAcceptanceDefectDto::from)
                .toList();
        return from(acceptance, defectDtos);
    }

    public static RepairAcceptanceDto from(RepairAcceptance acceptance, List<RepairAcceptanceDefectDto> defects) {
        return new RepairAcceptanceDto(
                acceptance.getId(),
                acceptance.getWorkOrderId(),
                acceptance.getStage(),
                acceptance.getStatus(),
                acceptance.getAcceptedById(),
                acceptance.getHandedOverById(),
                acceptance.getAcceptanceStartedAt(),
                acceptance.getAcceptedAt(),
                acceptance.isRunInRequired(),
                acceptance.getRunInShiftsRequired(),
                acceptance.getRunInStartedAt(),
                acceptance.getRunInCompletedAt(),
                acceptance.getQualityGrade(),
                acceptance.getPerformanceBefore(),
                acceptance.getPerformanceAfter(),
                acceptance.getQualityBefore(),
                acceptance.getQualityAfter(),
                acceptance.getRemarks(),
                defects == null ? List.of() : List.copyOf(defects),
                acceptance.getUpdatedAt()
        );
    }
}
