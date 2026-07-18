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

/** Transactional delivery record for a TOIR equipment-status event addressed to ERP. */
@Entity
@Table(name = "erp_equipment_status_outbox", indexes = {
        @Index(name = "idx_erp_equipment_status_outbox_ready", columnList = "status,next_attempt_at,created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class ErpEquipmentStatusOutboxEvent extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "history_id", nullable = false, unique = true)
    private UUID historyId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 180)
    private String idempotencyKey;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 8;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;
}
