package com.toir.entity.maintenance;

import com.toir.entity.ActorStampedEntity;
import com.toir.enums.RegulationChangeProposalStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "regulation_change_proposals")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RegulationChangeProposal extends ActorStampedEntity {

    @Column(name = "regulation_id", nullable = false)
    private UUID regulationId;

    // RCM snapshot dan kelgan — ixtiyoriy
    @Column(name = "rcm_snapshot_id")
    private UUID rcmSnapshotId;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    // Nimani o'zgartirish taklif qilinmoqda
    @Column(name = "proposed_periodicity_value")
    private Integer proposedPeriodicityValue;

    @Column(name = "proposed_periodicity_unit")
    private String proposedPeriodicityUnit;

    @Column(name = "proposed_template_id")
    private UUID proposedTemplateId;

    @Column(name = "change_reason", columnDefinition = "text")
    private String changeReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegulationChangeProposalStatus status = RegulationChangeProposalStatus.DRAFT;

    @Column(name = "reviewed_by_id")
    private UUID reviewedById;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_comment", columnDefinition = "text")
    private String reviewComment;
}
