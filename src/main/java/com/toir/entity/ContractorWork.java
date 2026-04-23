package com.toir.entity;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contractor_works")
public class ContractorWork extends BaseEntity {

    @Column(name = "contractor_id", nullable = false)
    private UUID contractorId;

    @Column(name = "work_order_id")
    private UUID workOrderId;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractorWorkStatus status = ContractorWorkStatus.DRAFT;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    private Double cost;

    @Column(columnDefinition = "text")
    private String result;

    @Column(name = "acceptance_comment", columnDefinition = "text")
    private String acceptanceComment;

    @Column(name = "created_by_id")
    private UUID createdById;

    @Column(name = "accepted_by_id")
    private UUID acceptedById;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    public UUID getContractorId() { return contractorId; }
    public void setContractorId(UUID contractorId) { this.contractorId = contractorId; }
    public UUID getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(UUID workOrderId) { this.workOrderId = workOrderId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ContractorWorkStatus getStatus() { return status; }
    public void setStatus(ContractorWorkStatus status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Double getCost() { return cost; }
    public void setCost(Double cost) { this.cost = cost; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getAcceptanceComment() { return acceptanceComment; }
    public void setAcceptanceComment(String acceptanceComment) { this.acceptanceComment = acceptanceComment; }
    public UUID getCreatedById() { return createdById; }
    public void setCreatedById(UUID createdById) { this.createdById = createdById; }
    public UUID getAcceptedById() { return acceptedById; }
    public void setAcceptedById(UUID acceptedById) { this.acceptedById = acceptedById; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
}
