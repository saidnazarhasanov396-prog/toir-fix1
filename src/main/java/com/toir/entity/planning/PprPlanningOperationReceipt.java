package com.toir.entity.planning;

import com.toir.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "ppr_planning_operation_receipts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_ppr_planning_operation_receipt",
                columnNames = {"session_id", "operation_type", "idempotency_key"}))
@Getter
@Setter
public class PprPlanningOperationReceipt extends BaseEntity {

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "operation_type", nullable = false, updatable = false, length = 96)
    private String operationType;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 160)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Column(name = "response_json", nullable = false, updatable = false, columnDefinition = "text")
    private String responseJson;
}
