package com.toir.entity;

import com.toir.enums.IntegrationSyncStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "integration_sync_logs", indexes = {
        @Index(name = "idx_sync_log_endpoint", columnList = "endpoint_id,started_at")
})
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

    public UUID getEndpointId() { return endpointId; }
    public void setEndpointId(UUID endpointId) { this.endpointId = endpointId; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public IntegrationSyncStatus getStatus() { return status; }
    public void setStatus(IntegrationSyncStatus status) { this.status = status; }
    public Integer getRecordsSent() { return recordsSent; }
    public void setRecordsSent(Integer recordsSent) { this.recordsSent = recordsSent; }
    public Integer getRecordsReceived() { return recordsReceived; }
    public void setRecordsReceived(Integer recordsReceived) { this.recordsReceived = recordsReceived; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
