package com.toir.entity.repair;
import com.toir.entity.BaseEntity;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestSource;

import com.toir.enums.RequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repair_requests")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RepairRequest extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String number;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "assigned_to_id")
    private UUID assignedToId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PriorityLevel priority = PriorityLevel.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CriticalityLevel criticality = CriticalityLevel.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status = RequestStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestSource source = RequestSource.MANUAL;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt = Instant.now();

    @Column(name = "target_completion_at")
    private Instant targetCompletionAt;

    @Column(name = "actual_completion_at")
    private Instant actualCompletionAt;

    @Column(name = "reacted_at")
    private Instant reactedAt;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "close_result", columnDefinition = "text")
    private String closeResult;
}
