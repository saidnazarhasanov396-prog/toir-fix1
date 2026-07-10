package com.toir.entity.sparepartlifecycle;

import com.toir.entity.BaseEntity;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleCommandStatus;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleCommandType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "spare_part_lifecycle_commands")
@Getter
@Setter
public class SparePartLifecycleCommand extends BaseEntity {

    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false, updatable = false)
    private SparePartLifecycleCommandType commandType;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SparePartLifecycleCommandStatus status = SparePartLifecycleCommandStatus.IN_PROGRESS;

    @Column(name = "result_installation_id")
    private UUID resultInstallationId;

    @Column(name = "result_removed_installation_id")
    private UUID resultRemovedInstallationId;

    @Column(name = "work_order_id", updatable = false)
    private UUID workOrderId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "completed_at")
    private Instant completedAt;
}
