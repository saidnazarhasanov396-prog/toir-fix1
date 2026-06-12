package com.toir.entity;

import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalTargetType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "approval_requests", indexes = {
        @Index(name = "idx_approval_doc", columnList = "document_type,document_id")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApprovalRequest extends BaseEntity {

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type")
    private ApprovalTargetType targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type")
    private ApprovalActionType actionType;

    @Column(name = "payload_json", columnDefinition = "text")
    private String payloadJson;

    @Column(name = "result_json", columnDefinition = "text")
    private String resultJson;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "current_step", nullable = false)
    private int currentStep = 0;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(columnDefinition = "text")
    private String description;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepNumber ASC")
    private List<ApprovalStep> steps = new ArrayList<>();

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
        if (this.targetType == null) {
            this.targetType = ApprovalTargetType.fromDocumentType(documentType);
        }
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
        if (this.targetId == null) {
            this.targetId = documentId;
        }
    }

    public void setTargetType(ApprovalTargetType targetType) {
        this.targetType = targetType;
        if (this.documentType == null && targetType != null) {
            this.documentType = targetType.name();
        }
    }

    public void setTargetId(UUID targetId) {
        this.targetId = targetId;
        if (this.documentId == null) {
            this.documentId = targetId;
        }
    }

    @PrePersist
    @PreUpdate
    void syncTargetAliases() {
        if (targetType == null) {
            targetType = ApprovalTargetType.fromDocumentType(documentType);
        }
        if (targetId == null) {
            targetId = documentId;
        }
        if (documentType == null && targetType != null) {
            documentType = targetType.name();
        }
        if (documentId == null) {
            documentId = targetId;
        }
    }

}
