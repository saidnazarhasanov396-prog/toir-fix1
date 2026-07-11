package com.toir.entity.plannedshutdown;

import com.toir.entity.BaseEntity;
import com.toir.enums.PlannedShutdownItemStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "planned_shutdown_isolation_points")
@Getter @Setter
public class PlannedShutdownIsolationPoint extends BaseEntity {
    @Column(name = "planned_shutdown_id", nullable = false) private UUID plannedShutdownId;
    @Column(name = "equipment_id", nullable = false) private UUID equipmentId;
    @Column(name = "location_id") private UUID locationId;
    @Column(name = "isolation_method", nullable = false) private String isolationMethod;
    @Column(name = "lock_tag_identifier", nullable = false) private String lockTagIdentifier;
    @Column(name = "responsible_employee_id", nullable = false) private UUID responsibleEmployeeId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PlannedShutdownItemStatus status = PlannedShutdownItemStatus.PENDING;
    @Column(name = "permit_id") private UUID permitId;
    @Column(name = "applied_by_id") private UUID appliedById;
    @Column(name = "applied_at") private Instant appliedAt;
    @Column(name = "verified_by_id") private UUID verifiedById;
    @Column(name = "verified_at") private Instant verifiedAt;
    @Column(name = "released_by_id") private UUID releasedById;
    @Column(name = "released_at") private Instant releasedAt;
    @Column(name = "order_number", nullable = false) private Integer orderNumber = 0;
}
