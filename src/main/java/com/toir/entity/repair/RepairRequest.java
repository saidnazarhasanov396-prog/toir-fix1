package com.toir.entity.repair;
import com.toir.entity.BaseEntity;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestSource;

import com.toir.enums.RequestStatus;
import com.toir.enums.WarrantyHandling;
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

    @Column(name = "template_id")
    private UUID templateId;

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

    @Column(name = "clarification_reason", columnDefinition = "text")
    private String clarificationReason;

    @Column(name = "close_result", columnDefinition = "text")
    private String closeResult;

    @Column(name = "warranty_active_at_creation")
    private Boolean warrantyActiveAtCreation;

    @Enumerated(EnumType.STRING)
    @Column(name = "warranty_handling")
    private WarrantyHandling warrantyHandling;

    @Column(name = "warranty_decision_comment", columnDefinition = "text")
    private String warrantyDecisionComment;

    @Column(name = "supplier_contacted_at")
    private Instant supplierContactedAt;

    @Column(name = "supplier_response", columnDefinition = "text")
    private String supplierResponse;

    @Column(name = "emergency_reason", columnDefinition = "text")
    private String emergencyReason;

    @Column(name = "warranty_decision_at")
    private Instant warrantyDecisionAt;

    @Column(name = "warranty_decision_by_user_id")
    private UUID warrantyDecisionByUserId;
}
