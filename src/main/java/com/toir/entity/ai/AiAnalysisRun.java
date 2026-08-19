package com.toir.entity.ai;

import com.toir.ai.repair.AiRepairAction;
import com.toir.ai.repair.AiRepairKind;
import com.toir.ai.repair.AiRepairSkipReason;
import com.toir.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "ai_analysis_runs")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AiAnalysisRun extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AiRepairKind kind;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @Column(name = "work_order_id")
    private UUID workOrderId;

    @Column(name = "reporter_id")
    private UUID reporterId;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "media_sha256", length = 64)
    private String mediaSha256;

    @Column(name = "problem_key")
    private String problemKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiRepairAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "skipped_reason", length = 64)
    private AiRepairSkipReason skippedReason;

    @Column(name = "repair_request_id")
    private UUID repairRequestId;

    @Column(columnDefinition = "text")
    private String payload;
}
