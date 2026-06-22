package com.toir.entity.equipment;

import com.toir.entity.BaseEntity;
import com.toir.enums.EquipmentCommissioningStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "equipment_commissioning_acts")
@Getter
@Setter
public class EquipmentCommissioningAct extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "source_warehouse_id", nullable = false)
    private UUID sourceWarehouseId;

    @Column(name = "warehouse_item_id", nullable = false)
    private UUID warehouseItemId;

    @Column(name = "target_department_id", nullable = false)
    private UUID targetDepartmentId;

    @Column(name = "target_location_id")
    private UUID targetLocationId;

    @Column(name = "responsible_employee_id", nullable = false)
    private UUID responsibleEmployeeId;

    @Column(name = "act_number", nullable = false, unique = true)
    private String actNumber;

    @Column(name = "act_date", nullable = false)
    private LocalDate actDate;

    @Column(name = "commissioned_at", nullable = false)
    private LocalDate commissionedAt;

    @Column(name = "operation_start_date", nullable = false)
    private LocalDate operationStartDate;

    @Column(name = "committee_json", columnDefinition = "text")
    private String committeeJson;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EquipmentCommissioningStatus status = EquipmentCommissioningStatus.DRAFT;

    @Column(name = "approval_request_id")
    private UUID approvalRequestId;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "warehouse_movement_id")
    private UUID warehouseMovementId;
}
