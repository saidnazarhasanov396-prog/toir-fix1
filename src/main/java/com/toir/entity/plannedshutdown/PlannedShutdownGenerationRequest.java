package com.toir.entity.plannedshutdown;

import com.toir.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "planned_shutdown_generation_requests")
@Getter
@Setter
public class PlannedShutdownGenerationRequest extends BaseEntity {
    @Column(name = "planned_shutdown_id", nullable = false)
    private UUID plannedShutdownId;
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;
    @Column(name = "window_version", nullable = false)
    private Long windowVersion;
    @Column(name = "ordered_work_order_ids", nullable = false, columnDefinition = "text")
    private String orderedWorkOrderIds;
}
