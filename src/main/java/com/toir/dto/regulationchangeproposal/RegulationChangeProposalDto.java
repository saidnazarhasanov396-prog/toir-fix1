package com.toir.dto.regulationchangeproposal;

import com.toir.entity.maintenance.RegulationChangeProposal;
import com.toir.enums.RegulationChangeProposalStatus;

import java.time.Instant;
import java.util.UUID;

public record RegulationChangeProposalDto(
        UUID id,
        UUID regulationId,
        UUID rcmSnapshotId,
        String title,
        String description,
        Integer proposedPeriodicityValue,
        String proposedPeriodicityUnit,
        UUID proposedTemplateId,
        String changeReason,
        RegulationChangeProposalStatus status,
        UUID createdById,
        UUID reviewedById,
        Instant reviewedAt,
        String reviewComment,
        Instant createdAt,
        Instant updatedAt
) {
    public static RegulationChangeProposalDto from(RegulationChangeProposal p) {
        return new RegulationChangeProposalDto(
                p.getId(),
                p.getRegulationId(),
                p.getRcmSnapshotId(),
                p.getTitle(),
                p.getDescription(),
                p.getProposedPeriodicityValue(),
                p.getProposedPeriodicityUnit(),
                p.getProposedTemplateId(),
                p.getChangeReason(),
                p.getStatus(),
                p.getCreatedById(),
                p.getReviewedById(),
                p.getReviewedAt(),
                p.getReviewComment(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}