package com.toir.safetypermit;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "safety_permits")
public class SafetyPermit extends BaseEntity {

    @Column(name = "work_order_id", nullable = false, unique = true)
    private UUID workOrderId;

    @Column(name = "permit_number", nullable = false, unique = true)
    private String permitNumber;

    @Column(name = "issued_by_id")
    private UUID issuedById;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SafetyPermitStatus status = SafetyPermitStatus.DRAFT;

    @Column(columnDefinition = "text")
    private String notes;

    public UUID getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(UUID workOrderId) { this.workOrderId = workOrderId; }
    public String getPermitNumber() { return permitNumber; }
    public void setPermitNumber(String permitNumber) { this.permitNumber = permitNumber; }
    public UUID getIssuedById() { return issuedById; }
    public void setIssuedById(UUID issuedById) { this.issuedById = issuedById; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }
    public Instant getValidUntil() { return validUntil; }
    public void setValidUntil(Instant validUntil) { this.validUntil = validUntil; }
    public SafetyPermitStatus getStatus() { return status; }
    public void setStatus(SafetyPermitStatus status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
