package com.toir.entity;
import com.toir.entity.RequestSource;
import com.toir.entity.RequestStatus;

import com.toir.entity.CriticalityLevel;
import com.toir.entity.PriorityLevel;
import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repair_requests")
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

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public UUID getEquipmentId() { return equipmentId; }
    public void setEquipmentId(UUID equipmentId) { this.equipmentId = equipmentId; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public UUID getLocationId() { return locationId; }
    public void setLocationId(UUID locationId) { this.locationId = locationId; }
    public UUID getReporterId() { return reporterId; }
    public void setReporterId(UUID reporterId) { this.reporterId = reporterId; }
    public UUID getAssignedToId() { return assignedToId; }
    public void setAssignedToId(UUID assignedToId) { this.assignedToId = assignedToId; }
    public PriorityLevel getPriority() { return priority; }
    public void setPriority(PriorityLevel priority) { this.priority = priority; }
    public CriticalityLevel getCriticality() { return criticality; }
    public void setCriticality(CriticalityLevel criticality) { this.criticality = criticality; }
    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }
    public RequestSource getSource() { return source; }
    public void setSource(RequestSource source) { this.source = source; }
    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }
    public Instant getTargetCompletionAt() { return targetCompletionAt; }
    public void setTargetCompletionAt(Instant targetCompletionAt) { this.targetCompletionAt = targetCompletionAt; }
    public Instant getActualCompletionAt() { return actualCompletionAt; }
    public void setActualCompletionAt(Instant actualCompletionAt) { this.actualCompletionAt = actualCompletionAt; }
    public Instant getReactedAt() { return reactedAt; }
    public void setReactedAt(Instant reactedAt) { this.reactedAt = reactedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public String getCloseResult() { return closeResult; }
    public void setCloseResult(String closeResult) { this.closeResult = closeResult; }
}
