package com.toir.entity.sparepartlifecycle;

import com.toir.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "spare_part_due_event_work_orders", uniqueConstraints = {
        @UniqueConstraint(name = "uq_sp_due_wo_idempotency", columnNames = {"due_event_id", "idempotency_key"})
})
@Getter
@Setter
public class SparePartDueEventWorkOrderLink extends BaseEntity {

    @Column(name = "due_event_id", nullable = false, updatable = false)
    private UUID dueEventId;

    @Column(name = "work_order_id", nullable = false, updatable = false)
    private UUID workOrderId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 200)
    private String idempotencyKey;

    @Column(name = "link_status", nullable = false, length = 32)
    private String linkStatus;

    @Column(name = "linked_by", nullable = false, updatable = false)
    private UUID linkedBy;
}
