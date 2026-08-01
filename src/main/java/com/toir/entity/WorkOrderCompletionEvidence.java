package com.toir.entity;

import com.toir.enums.CompletionEvidenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "work_order_completion_evidence")
@Getter
@Setter
public class WorkOrderCompletionEvidence extends BaseEntity {

    @Column(name = "work_order_id", nullable = false, updatable = false)
    private UUID workOrderId;

    @Column(name = "ppr_task_id", updatable = false)
    private UUID pprTaskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, updatable = false, length = 32)
    private CompletionEvidenceType evidenceType;

    @Column(name = "file_asset_id", updatable = false)
    private UUID fileAssetId;

    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    @Column(name = "submitted_by_id", updatable = false)
    private UUID submittedById;

    @Column(columnDefinition = "text")
    private String note;
}
