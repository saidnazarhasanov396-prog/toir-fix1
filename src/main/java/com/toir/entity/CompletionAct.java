package com.toir.entity;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "completion_acts")
public class CompletionAct extends BaseEntity {

    @Column(name = "work_order_id", nullable = false, unique = true)
    private UUID workOrderId;

    @Column(name = "act_number", nullable = false, unique = true)
    private String actNumber;

    @Column(name = "signed_by_id")
    private UUID signedById;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(columnDefinition = "text")
    private String summary;

    public UUID getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(UUID workOrderId) { this.workOrderId = workOrderId; }
    public String getActNumber() { return actNumber; }
    public void setActNumber(String actNumber) { this.actNumber = actNumber; }
    public UUID getSignedById() { return signedById; }
    public void setSignedById(UUID signedById) { this.signedById = signedById; }
    public Instant getSignedAt() { return signedAt; }
    public void setSignedAt(Instant signedAt) { this.signedAt = signedAt; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
