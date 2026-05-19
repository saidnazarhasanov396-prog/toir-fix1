package com.toir.entity.defects;

import com.toir.entity.BaseEntity;
import com.toir.enums.DefectStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "defects")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Defect extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "repair_request_id")
    private UUID repairRequestId;

    @Column(name = "category")
    private String category;

    @Column(name = "severity")
    private String severity;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "root_cause")
    private String rootCause;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DefectStatus status = DefectStatus.OPEN;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "recurrence_count", nullable = false)
    private int recurrenceCount = 0;

}
