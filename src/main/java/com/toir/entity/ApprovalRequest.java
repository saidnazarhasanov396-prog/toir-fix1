package com.toir.entity;

import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalFlowType;
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
        @Index(name = "idx_approval_doc", columnList = "document_type,document_id"),
        @Index(name = "idx_approval_target", columnList = "target_type,target_id")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApprovalRequest extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_type", nullable = false)
    private ApprovalFlowType flowType = ApprovalFlowType.SEQUENTIAL;

    @Column(name = "approval_round", nullable = false)
    private int approvalRound = 1;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "template_version")
    private Long templateVersion;

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

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Column(name = "executed", nullable = false)
    private boolean executed;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "execution_id")
    private UUID executionId;

    @Column(name = "last_returned_at")
    private Instant lastReturnedAt;

    @Column(name = "last_returned_by")
    private UUID lastReturnedBy;

    @Column(name = "last_return_comment", columnDefinition = "text")
    private String lastReturnComment;

    @Column(columnDefinition = "text")
    private String description;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepNumber ASC")
    private List<ApprovalStep> steps = new ArrayList<>();

    public void setDocumentType(String documentType) {
        this.targetType = ApprovalTargetType.fromDocumentType(documentType);
        this.documentType = this.targetType == null ? documentType : this.targetType.name();
    }

    public void setDocumentId(UUID documentId) {
        this.targetId = documentId;
        this.documentId = documentId;
    }

    public void setTargetType(ApprovalTargetType targetType) {
        this.targetType = targetType;
        if (targetType != null) {
            this.documentType = targetType.name();
        }
    }

    public void setTargetId(UUID targetId) {
        this.targetId = targetId;
        if (targetId != null) {
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
        if (targetType != null) {
            documentType = targetType.name();
        }
        if (documentId == null) {
            documentId = targetId;
        }
        if (targetId != null) {
            documentId = targetId;
        }
    }

}
