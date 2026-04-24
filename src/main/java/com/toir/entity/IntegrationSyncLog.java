package com.toir.entity;

import com.toir.enums.IntegrationSyncStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "integration_sync_logs", indexes = {
        @Index(name = "idx_sync_log_endpoint", columnList = "endpoint_id,started_at")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IntegrationSyncLog extends BaseEntity {

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "direction", nullable = false)
    private String direction;

    @Column(name = "module", nullable = false)
    private String module;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IntegrationSyncStatus status;

    @Column(name = "records_sent")
    private Integer recordsSent;

    @Column(name = "records_received")
    private Integer recordsReceived;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;
}
