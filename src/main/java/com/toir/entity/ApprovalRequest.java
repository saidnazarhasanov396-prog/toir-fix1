package com.toir.entity;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "approval_requests", indexes = {
        @Index(name = "idx_approval_doc", columnList = "document_type,document_id")
})
public class ApprovalRequest extends BaseEntity {

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

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

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public UUID getRequesterId() { return requesterId; }
    public void setRequesterId(UUID requesterId) { this.requesterId = requesterId; }
    public ApprovalStatus getStatus() { return status; }
    public void setStatus(ApprovalStatus status) { this.status = status; }
    public int getCurrentStep() { return currentStep; }
    public void setCurrentStep(int currentStep) { this.currentStep = currentStep; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<ApprovalStep> getSteps() { return steps; }
}
