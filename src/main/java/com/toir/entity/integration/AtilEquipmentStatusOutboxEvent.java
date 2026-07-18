package com.toir.entity.integration;

import com.toir.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Durable TOIR status update for the ATIL vehicle that owns this equipment mapping. */
@Entity
@Table(name = "atil_equipment_status_outbox", indexes = {
        @Index(name = "idx_atil_equipment_status_outbox_ready", columnList = "status,next_attempt_at,created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class AtilEquipmentStatusOutboxEvent extends BaseEntity {
    @Column(name = "equipment_id", nullable = false) private UUID equipmentId;
    @Column(name = "atil_vehicle_id", nullable = false) private UUID atilVehicleId;
    @Column(name = "history_id", nullable = false, unique = true) private UUID historyId;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 180) private String idempotencyKey;
    @Column(nullable = false, columnDefinition = "text") private String payload;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false) private int attempts;
    @Column(name = "max_attempts", nullable = false) private int maxAttempts = 8;
    @Column(name = "next_attempt_at") private Instant nextAttemptAt;
    @Column(name = "sent_at") private Instant sentAt;
    @Column(name = "last_error", columnDefinition = "text") private String lastError;
}
