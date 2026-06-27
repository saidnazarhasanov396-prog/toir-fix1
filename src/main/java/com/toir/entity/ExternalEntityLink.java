package com.toir.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_entity_links")
@Getter
@Setter
public class ExternalEntityLink extends BaseEntity {

    @Column(name = "source_system", nullable = false, length = 64)
    private String sourceSystem;

    @Column(name = "source_entity_type", nullable = false, length = 64)
    private String sourceEntityType;

    @Column(name = "source_entity_id", nullable = false, length = 128)
    private String sourceEntityId;

    @Column(name = "target_system", nullable = false, length = 64)
    private String targetSystem;

    @Column(name = "target_entity_type", nullable = false, length = 64)
    private String targetEntityType;

    @Column(name = "target_entity_id")
    private UUID targetEntityId;

    @Column(name = "natural_key")
    private String naturalKey;

    @Column(name = "last_payload_hash", length = 128)
    private String lastPayloadHash;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "sync_status", length = 32)
    private String syncStatus;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;
}
