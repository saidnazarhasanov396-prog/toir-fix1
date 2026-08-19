package com.toir.entity.ai;

import com.toir.ai.repair.AiRepairAction;
import com.toir.ai.repair.AiRepairKind;
import com.toir.ai.repair.AiRepairSkipReason;
import com.toir.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_job_contexts")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AiJobContext extends BaseEntity {

    @Column(name = "job_id", nullable = false, unique = true)
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AiRepairKind kind;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @Column(name = "work_order_id")
    private UUID workOrderId;

    @Column(name = "reporter_id")
    private UUID reporterId;

    @Column(name = "media_sha256", length = 64)
    private String mediaSha256;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "repair_request_id")
    private UUID repairRequestId;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private AiRepairAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "skipped_reason", length = 64)
    private AiRepairSkipReason skippedReason;
}
