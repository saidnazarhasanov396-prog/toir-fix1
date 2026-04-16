package com.toir.procurement;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Заявка на закупку ТМЦ/запчастей. По ТЗ §4.2.9 — формируется службой МТО
 * вручную или автоматически из low-stock, проходит согласование, после
 * одобрения переходит в заказ поставщику и затем в приход на склад.
 */
@Entity
@Table(name = "procurement_requests")
public class ProcurementRequest extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String number;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcurementRequestStatus status = ProcurementRequestStatus.DRAFT;

    /** AUTO — сгенерирована автоматически из low-stock; MANUAL — создана пользователем. */
    @Column(nullable = false)
    private String source = "MANUAL";

    @Column(name = "required_by")
    private LocalDate requiredBy;

    @Column(name = "total_estimated_cost", nullable = false)
    private double totalEstimatedCost = 0.0;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "ordered_at")
    private Instant orderedAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProcurementRequestLine> lines = new ArrayList<>();

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public UUID getWarehouseId() { return warehouseId; }
    public void setWarehouseId(UUID warehouseId) { this.warehouseId = warehouseId; }
    public UUID getRequestedBy() { return requestedBy; }
    public void setRequestedBy(UUID requestedBy) { this.requestedBy = requestedBy; }
    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID approvedBy) { this.approvedBy = approvedBy; }
    public ProcurementRequestStatus getStatus() { return status; }
    public void setStatus(ProcurementRequestStatus status) { this.status = status; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDate getRequiredBy() { return requiredBy; }
    public void setRequiredBy(LocalDate requiredBy) { this.requiredBy = requiredBy; }
    public double getTotalEstimatedCost() { return totalEstimatedCost; }
    public void setTotalEstimatedCost(double totalEstimatedCost) { this.totalEstimatedCost = totalEstimatedCost; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public Instant getOrderedAt() { return orderedAt; }
    public void setOrderedAt(Instant orderedAt) { this.orderedAt = orderedAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public List<ProcurementRequestLine> getLines() { return lines; }
    public void setLines(List<ProcurementRequestLine> lines) { this.lines = lines; }
}
