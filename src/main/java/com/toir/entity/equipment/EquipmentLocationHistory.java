package com.toir.entity.equipment;

import com.toir.entity.BaseEntity;
import com.toir.enums.EquipmentLocationType;
import com.toir.enums.EquipmentOutsideReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "equipment_location_history")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EquipmentLocationHistory extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_location_type")
    private EquipmentLocationType fromLocationType;

    @Column(name = "from_department_id")
    private UUID fromDepartmentId;

    @Column(name = "from_warehouse_id")
    private UUID fromWarehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_outside_reason")
    private EquipmentOutsideReason fromOutsideReason;

    @Column(name = "from_outside_taken_by")
    private String fromOutsideTakenBy;

    @Column(name = "from_outside_recipient_user_id")
    private UUID fromOutsideRecipientUserId;

    @Column(name = "from_outside_started_date")
    private LocalDate fromOutsideStartedDate;

    @Column(name = "from_outside_expected_return_date")
    private LocalDate fromOutsideExpectedReturnDate;

    @Column(name = "from_outside_destination")
    private String fromOutsideDestination;

    @Column(name = "from_outside_reason_note", columnDefinition = "text")
    private String fromOutsideReasonNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_location_type", nullable = false)
    private EquipmentLocationType toLocationType;

    @Column(name = "to_department_id")
    private UUID toDepartmentId;

    @Column(name = "to_warehouse_id")
    private UUID toWarehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_outside_reason")
    private EquipmentOutsideReason toOutsideReason;

    @Column(name = "to_outside_taken_by")
    private String toOutsideTakenBy;

    @Column(name = "to_outside_recipient_user_id")
    private UUID toOutsideRecipientUserId;

    @Column(name = "to_outside_started_date")
    private LocalDate toOutsideStartedDate;

    @Column(name = "to_outside_expected_return_date")
    private LocalDate toOutsideExpectedReturnDate;

    @Column(name = "to_outside_destination")
    private String toOutsideDestination;

    @Column(name = "to_outside_reason_note", columnDefinition = "text")
    private String toOutsideReasonNote;

    @Column(name = "responsible_department_id")
    private UUID responsibleDepartmentId;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "note", columnDefinition = "text")
    private String note;
}
